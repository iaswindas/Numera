"""LayoutLM zone classifier with A/B testing and keyword heuristic fallback."""

import logging
import random

try:
    import torch
except Exception:  # pragma: no cover - optional dependency in lightweight envs
    torch = None

try:
    from transformers import AutoModelForSequenceClassification, AutoTokenizer
except Exception:  # pragma: no cover - optional dependency in lightweight envs
    AutoModelForSequenceClassification = None
    AutoTokenizer = None

from app.api.models import ZoneType
from app.services.model_manager import ModelManager

logger = logging.getLogger("ml-service.ml.zone_classifier")

# ---------------------------------------------------------------------------
# Keyword Heuristic
# ---------------------------------------------------------------------------

ZONE_KEYWORDS: dict[ZoneType, dict[str, list[str]]] = {
    ZoneType.BALANCE_SHEET: {
        "strong": [
            "total assets", "total liabilities", "total equity",
            "shareholders' equity", "shareholders equity", "net assets",
            "statement of financial position",
        ],
        "moderate": [
            "current assets", "non-current assets", "non current assets",
            "goodwill", "trade receivables", "inventories",
            "property plant", "right-of-use", "lease liabilities",
            "intangible assets", "deferred tax",
        ],
    },
    ZoneType.INCOME_STATEMENT: {
        "strong": [
            "revenue", "net income", "profit for the year",
            "profit for the period", "operating profit",
            "earnings per share", "gross profit",
            "statement of profit or loss",
            "statement of comprehensive income",
        ],
        "moderate": [
            "cost of sales", "cost of revenue", "distribution costs",
            "administrative expenses", "finance costs", "finance income",
            "other operating income", "income tax expense",
        ],
    },
    ZoneType.CASH_FLOW: {
        "strong": [
            "cash from operations", "cash from investing",
            "cash from financing", "net increase in cash",
            "net decrease in cash", "cash and cash equivalents at end",
            "cash and cash equivalents at beginning",
            "statement of cash flows",
        ],
        "moderate": [
            "depreciation and amortization", "depreciation and amortisation",
            "working capital changes", "dividends paid",
            "proceeds from borrowings", "repayment of borrowings",
            "purchase of property",
        ],
    },
    ZoneType.NOTES_FIXED_ASSETS: {
        "strong": [
            "cost at beginning", "accumulated depreciation",
            "net book value", "additions during the year",
        ],
        "moderate": [
            "property plant and equipment", "tangible assets",
            "disposals", "write-offs", "carrying amount",
        ],
    },
    ZoneType.NOTES_RECEIVABLES: {
        "strong": [
            "trade receivables aging", "aging of receivables",
            "expected credit loss",
        ],
        "moderate": [
            "trade receivables", "allowance for doubtful",
            "impairment of receivables",
        ],
    },
    ZoneType.NOTES_DEBT: {
        "strong": [
            "maturity profile", "borrowings maturity",
            "long-term debt schedule",
        ],
        "moderate": [
            "long-term borrowings", "bonds payable",
            "interest rate", "credit facility",
        ],
    },
}


def classify_by_keywords(table_text: str) -> tuple[ZoneType, float]:
    """Classify a table by keyword matching."""
    table_text_lower = table_text.lower()
    scores: dict[ZoneType, float] = {}

    for zone_type, keywords in ZONE_KEYWORDS.items():
        strong = sum(1 for kw in keywords["strong"] if kw in table_text_lower)
        moderate = sum(1 for kw in keywords["moderate"] if kw in table_text_lower)
        scores[zone_type] = strong * 2.0 + moderate * 1.0

    if not scores or max(scores.values(), default=0) == 0:
        return ZoneType.OTHER, 0.3

    best = max(scores, key=scores.get)  # type: ignore[arg-type]
    confidence = min(0.95, 0.5 + (scores[best] / 10))
    return best, confidence


# ---------------------------------------------------------------------------
# LayoutLM ML Classifier with A/B Testing
# ---------------------------------------------------------------------------

class LayoutLMZoneClassifier:
    """LayoutLM-based zone classifier with A/B testing support."""

    def __init__(self, model_manager: ModelManager, settings=None):
        self.is_loaded = False
        self.tokenizer = None
        self.model = None

        self._staging_tokenizer = None
        self._staging_model = None
        self._staging_loaded = False
        self._ab_ratio = 0.0
        self._ab_enabled = False

        if settings:
            self._ab_ratio = settings.ab_test_staging_ratio
            self._ab_enabled = settings.ab_test_enabled

        # --- Production model ---
        self._load_model(model_manager, settings, stage="Production")

        # --- Staging model (A/B testing) ---
        if self._ab_enabled:
            self._load_staging(model_manager, settings)

    def _load_model(self, model_manager, settings, stage="Production"):
        """Load a LayoutLM model from MLflow or HuggingFace."""
        if torch is None or AutoTokenizer is None or AutoModelForSequenceClassification is None:
            logger.info("Torch/Transformers unavailable; falling back to keyword heuristic classifier")
            return

        try:
            model_path = model_manager.load_model("layoutlm-zone-classifier", stage=stage)
            self.tokenizer = AutoTokenizer.from_pretrained(str(model_path))
            self.model = AutoModelForSequenceClassification.from_pretrained(
                str(model_path), num_labels=len(ZoneType)
            )
            self.model.eval()
            self.is_loaded = True
            logger.info("LayoutLM %s loaded from MLflow", stage)
        except Exception as exc:
            logger.warning("MLflow LayoutLM %s load failed: %s", stage, exc)
            if settings and settings.layoutlm_hf_fallback and stage == "Production":
                try:
                    hf_id = settings.layoutlm_hf_fallback
                    self.tokenizer = AutoTokenizer.from_pretrained(hf_id)
                    self.model = AutoModelForSequenceClassification.from_pretrained(
                        hf_id, num_labels=len(ZoneType), ignore_mismatched_sizes=True,
                    )
                    self.model.eval()
                    self.is_loaded = True
                    logger.info("LayoutLM loaded from HuggingFace (unfinetuned)")
                except Exception as hf_exc:
                    logger.warning("HuggingFace fallback failed: %s", hf_exc)

    def _load_staging(self, model_manager, settings):
        """Load Staging model for A/B testing."""
        if torch is None or AutoTokenizer is None or AutoModelForSequenceClassification is None:
            return

        try:
            staging_path = model_manager.load_staging_model("layoutlm-zone-classifier")
            if staging_path:
                self._staging_tokenizer = AutoTokenizer.from_pretrained(str(staging_path))
                self._staging_model = AutoModelForSequenceClassification.from_pretrained(
                    str(staging_path), num_labels=len(ZoneType)
                )
                self._staging_model.eval()
                self._staging_loaded = True
                logger.info("LayoutLM Staging loaded for A/B testing")
        except Exception as exc:
            logger.info("No Staging LayoutLM: %s", exc)

    @property
    def staging_loaded(self) -> bool:
        return self._staging_loaded

    def classify(
        self, text: str, bboxes: list[list[int]], force_model: str | None = None
    ) -> tuple[ZoneType, float, str]:
        """Classify table text using LayoutLM.

        Returns:
            (zone_type, confidence, model_version) tuple.
        """
        if not self.is_loaded:
            zt, conf = classify_by_keywords(text)
            return zt, conf, "heuristic"

        # --- A/B test routing ---
        use_staging = False
        if force_model == "staging" and self._staging_loaded:
            use_staging = True
        elif force_model != "production" and self._ab_enabled and self._staging_loaded:
            use_staging = random.random() < self._ab_ratio

        if use_staging:
            tokenizer = self._staging_tokenizer
            model = self._staging_model
            version = "staging"
        else:
            tokenizer = self.tokenizer
            model = self.model
            version = "production"

        return self._infer(text, bboxes, tokenizer, model, version)

    def _infer(self, text, bboxes, tokenizer, model, version) -> tuple[ZoneType, float, str]:
        """Run inference with a specific model."""
        try:
            if hasattr(tokenizer, "encode_plus") and "boxes" in (
                tokenizer.model_input_names or []
            ):
                encoding = tokenizer(
                    text, boxes=bboxes, truncation=True,
                    max_length=512, return_tensors="pt",
                )
            else:
                encoding = tokenizer(
                    text, truncation=True,
                    max_length=512, return_tensors="pt",
                )

            with torch.no_grad():
                outputs = model(**encoding)
                probs = torch.softmax(outputs.logits, dim=-1)
                predicted = torch.argmax(probs, dim=-1).item()
                confidence = probs[0][predicted].item()

            zone_types = list(ZoneType)
            if predicted < len(zone_types):
                return zone_types[predicted], float(confidence), version
            return ZoneType.OTHER, 0.3, version

        except Exception:
            logger.exception("LayoutLM inference failed")
            zt, conf = classify_by_keywords(text)
            return zt, conf, "heuristic_fallback"
