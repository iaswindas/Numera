"""Sentence-BERT semantic matching engine with A/B testing support.

Supports Production + Staging model versions for A/B testing, with
tenant-aware model resolution for client-specific models.
"""

import hashlib
import json
import logging
import random
import re

import numpy as np

try:
    from sklearn.metrics.pairwise import cosine_similarity
except Exception:  # pragma: no cover - optional dependency in lightweight envs
    def cosine_similarity(a: np.ndarray, b: np.ndarray) -> np.ndarray:  # type: ignore[no-redef]
        a_arr = np.asarray(a, dtype=np.float32)
        b_arr = np.asarray(b, dtype=np.float32)

        a_norm = np.linalg.norm(a_arr, axis=1, keepdims=True)
        b_norm = np.linalg.norm(b_arr, axis=1, keepdims=True)
        a_norm[a_norm == 0] = 1.0
        b_norm[b_norm == 0] = 1.0

        a_unit = a_arr / a_norm
        b_unit = b_arr / b_norm
        return np.matmul(a_unit, b_unit.T)

from app.api.models import (
    ConfidenceLevel,
    MappedRow,
    SourceRow,
    SuggestedMapping,
    TargetLineItem,
)
from app.services.model_manager import ModelManager

logger = logging.getLogger("ml-service.ml.semantic_matcher")


class SemanticMatcher:
    """Sentence-BERT based line-item semantic matching with A/B testing."""

    def __init__(self, model_manager: ModelManager, settings=None):
        self.is_loaded = False
        self.model = None
        self._staging_model = None
        self._staging_loaded = False
        self._target_cache: dict[str, tuple[np.ndarray, list[int]]] = {}
        self._taxonomy: dict[str, list[str]] = {}

        self._high_threshold = 0.85
        self._medium_threshold = 0.65
        self._ab_ratio = 0.0
        self._ab_enabled = False

        if settings:
            self._high_threshold = settings.mapping_high_confidence
            self._medium_threshold = settings.mapping_medium_confidence
            self._ab_ratio = settings.ab_test_staging_ratio
            self._ab_enabled = settings.ab_test_enabled

        # --- Load Production model ---
        self._load_production(model_manager, settings)

        # --- Load Staging model (for A/B testing) ---
        if self._ab_enabled:
            self._load_staging(model_manager, settings)

    def _load_production(self, model_manager: ModelManager, settings):
        """Load the Production model from MLflow or HuggingFace fallback."""
        try:
            model_path = model_manager.load_model("sbert-ifrs-matcher", stage="Production")
            from sentence_transformers import SentenceTransformer
            self.model = SentenceTransformer(str(model_path))
            self.is_loaded = True
            logger.info("SBERT Production loaded from MLflow")
        except Exception as exc:
            logger.warning("MLflow SBERT Production load failed: %s", exc)
            if settings and settings.sbert_hf_fallback:
                try:
                    from sentence_transformers import SentenceTransformer
                    self.model = SentenceTransformer(settings.sbert_hf_fallback)
                    self.is_loaded = True
                    logger.info("SBERT loaded from HuggingFace fallback")
                except Exception as hf_exc:
                    logger.warning("HuggingFace SBERT fallback failed: %s", hf_exc)

    def _load_staging(self, model_manager: ModelManager, settings):
        """Load the Staging model for A/B testing."""
        try:
            staging_path = model_manager.load_staging_model("sbert-ifrs-matcher")
            if staging_path:
                from sentence_transformers import SentenceTransformer
                self._staging_model = SentenceTransformer(str(staging_path))
                self._staging_loaded = True
                logger.info("SBERT Staging model loaded for A/B testing (ratio=%.0f%%)", self._ab_ratio * 100)
        except Exception as exc:
            logger.info("No Staging model available for A/B testing: %s", exc)

    @property
    def staging_loaded(self) -> bool:
        return self._staging_loaded

    def load_taxonomy(self, taxonomy_path: str):
        """Load IFRS synonym dictionary."""
        try:
            with open(taxonomy_path) as f:
                self._taxonomy = json.load(f)
            logger.info("Loaded taxonomy: %d terms", len(self._taxonomy))
        except Exception:
            logger.exception("Failed to load taxonomy from %s", taxonomy_path)
            self._taxonomy = {}

    def match(
        self,
        source_rows: list[SourceRow],
        target_items: list[TargetLineItem],
        top_k: int = 3,
        force_model: str | None = None,
    ) -> tuple[list[MappedRow], str]:
        """Match source rows to target items.

        Returns:
            (mappings, model_version) tuple. model_version is "production" or "staging".
        """
        if not self.is_loaded or not source_rows or not target_items:
            results = self._fallback_match(source_rows, target_items, top_k)
            return results, "fallback"

        # --- A/B test routing ---
        use_staging = False
        if force_model == "staging" and self._staging_loaded:
            use_staging = True
        elif force_model != "production" and self._ab_enabled and self._staging_loaded:
            use_staging = random.random() < self._ab_ratio

        active_model = self._staging_model if use_staging else self.model
        model_version = "staging" if use_staging else "production"

        if use_staging:
            logger.info("A/B test: using STAGING model")

        # --- Encode + match ---
        results = self._match_with_model(active_model, source_rows, target_items, top_k, model_version)
        return results, model_version

    def _match_with_model(
        self, model, source_rows, target_items, top_k, model_version
    ) -> list[MappedRow]:
        """Core matching logic for any model instance."""
        source_texts = [self._clean_text(r.text) for r in source_rows]
        source_embs = model.encode(
            source_texts, normalize_embeddings=True, batch_size=32, show_progress_bar=False
        )

        target_embs, index_map = self._precompute_target_embeddings(model, target_items)
        sim_matrix = cosine_similarity(source_embs, target_embs)

        n_items = len(target_items)
        collapsed = np.zeros((len(source_rows), n_items))
        for emb_idx, item_idx in enumerate(index_map):
            collapsed[:, item_idx] = np.maximum(
                collapsed[:, item_idx], sim_matrix[:, emb_idx]
            )

        # Zone-aware penalty
        for i, src in enumerate(source_rows):
            for j, tgt in enumerate(target_items):
                if src.zone_type != tgt.zone_type:
                    collapsed[i][j] *= 0.3

        results: list[MappedRow] = []
        for i, src in enumerate(source_rows):
            top_indices = np.argsort(collapsed[i])[-top_k:][::-1]
            suggestions: list[SuggestedMapping] = []
            for idx in top_indices:
                score = float(collapsed[i][idx])
                if score < 0.3:
                    continue
                suggestions.append(
                    SuggestedMapping(
                        target_line_item_id=target_items[idx].line_item_id,
                        target_label=target_items[idx].label,
                        confidence=round(score, 4),
                        confidence_level=self._classify_confidence(score),
                        expression=self._extract_value(src),
                        adjustments={},
                    )
                )
            results.append(
                MappedRow(
                    source_row_id=src.row_id,
                    source_text=src.text,
                    source_value=src.value,
                    source_page=src.page,
                    source_coordinates=src.coordinates,
                    suggested_mappings=suggestions,
                    model_version=model_version,
                )
            )
        return results

    def _precompute_target_embeddings(self, model, items):
        """Encode target labels + synonyms with caching."""
        cache_key = hashlib.md5("|".join(i.label for i in items).encode()).hexdigest()
        if cache_key in self._target_cache:
            return self._target_cache[cache_key]

        texts = []
        index_map = []
        for i, item in enumerate(items):
            texts.append(item.label)
            index_map.append(i)
            for syn in self._taxonomy.get(item.label, []):
                texts.append(syn)
                index_map.append(i)

        embeddings = model.encode(
            texts, normalize_embeddings=True, batch_size=64, show_progress_bar=False
        )
        self._target_cache[cache_key] = (embeddings, index_map)
        return self._target_cache[cache_key]

    def _fallback_match(self, source_rows, target_items, top_k):
        """Basic substring match fallback."""
        results = []
        for src in source_rows:
            suggestions = []
            src_lower = src.text.lower().strip()
            for tgt in target_items:
                tgt_lower = tgt.label.lower().strip()
                if src_lower in tgt_lower or tgt_lower in src_lower:
                    suggestions.append(
                        SuggestedMapping(
                            target_line_item_id=tgt.line_item_id,
                            target_label=tgt.label,
                            confidence=0.6,
                            confidence_level=ConfidenceLevel.LOW,
                            expression=self._extract_value(src),
                            adjustments={},
                        )
                    )
            results.append(
                MappedRow(
                    source_row_id=src.row_id, source_text=src.text,
                    source_value=src.value, source_page=src.page,
                    source_coordinates=src.coordinates,
                    suggested_mappings=suggestions[:top_k],
                    model_version="fallback",
                )
            )
        return results

    @staticmethod
    def _clean_text(text: str) -> str:
        text = re.sub(r"\(?[Nn]ote\s*\d+\)?", "", text)
        text = re.sub(r"\d+[.,]\d+", "", text)
        return text.strip().lower()

    def _classify_confidence(self, score: float) -> ConfidenceLevel:
        if score >= self._high_threshold:
            return ConfidenceLevel.HIGH
        elif score >= self._medium_threshold:
            return ConfidenceLevel.MEDIUM
        return ConfidenceLevel.LOW

    @staticmethod
    def _extract_value(src: SourceRow) -> str:
        return str(src.value) if src.value else "0"
