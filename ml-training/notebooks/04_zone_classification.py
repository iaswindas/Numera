"""LayoutLM Zone Classification Training — Financial Document Zone Classification.

Fine-tunes a LayoutLM model to classify financial document table regions
into zone types: Balance Sheet, Income Statement, Cash Flow, Notes, etc.

The model learns from both text content and spatial layout (bounding boxes)
to distinguish financial statement zones.

Usage:
    python 04_zone_classification.py [--config ../configs/training_config.yaml]
"""

from __future__ import annotations

import argparse
import json
import logging
import random
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import yaml

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("layoutlm_zone_training")

try:
    import torch
    from torch import nn
    from torch.optim import AdamW
    from torch.utils.data import DataLoader, Dataset
except ImportError:
    logger.error("PyTorch is required. Install with: pip install torch")
    sys.exit(1)

try:
    from transformers import (
        LayoutLMForSequenceClassification,
        LayoutLMTokenizer,
        get_linear_schedule_with_warmup,
    )
except ImportError:
    logger.error(
        "Transformers is required. Install with:\n"
        "  pip install transformers"
    )
    sys.exit(1)


# ── Configuration ──────────────────────────────────────────────────────────


ZONE_LABELS = [
    "BALANCE_SHEET",
    "INCOME_STATEMENT",
    "CASH_FLOW",
    "NOTES_FIXED_ASSETS",
    "NOTES_RECEIVABLES",
    "NOTES_DEBT",
    "NOTES_OTHER",
    "OTHER",
]

ZONE_LABEL2ID = {label: idx for idx, label in enumerate(ZONE_LABELS)}
ZONE_ID2LABEL = {idx: label for idx, label in enumerate(ZONE_LABELS)}


@dataclass
class ZoneClassConfig:
    model_name: str = "microsoft/layoutlm-base-uncased"
    num_labels: int = 8
    max_length: int = 512
    learning_rate: float = 2e-5
    batch_size: int = 8
    num_epochs: int = 10
    warmup_steps: int = 100
    weight_decay: float = 0.01
    early_stopping_patience: int = 3
    target_accuracy: float = 0.92
    gradient_clip_norm: float = 1.0

    sample_documents_dir: str = "data/sample_documents"
    train_split: float = 0.8
    val_split: float = 0.1
    seed: int = 42

    output_dir: str = "ml-training/models/layoutlm-zone"
    checkpoint_dir: str = "ml-training/checkpoints/layoutlm-zone"

    mlflow_experiment: str = "zone-classifier"
    mlflow_model_name: str = "layoutlm-zone-classifier"


def load_config(path: str | None) -> ZoneClassConfig:
    cfg = ZoneClassConfig()
    if path and Path(path).exists():
        with open(path) as fh:
            raw = yaml.safe_load(fh) or {}
        zc = raw.get("zone_classifier", {})
        for key, val in zc.items():
            if hasattr(cfg, key):
                setattr(cfg, key, val)
        logger.info("Loaded config from %s", path)
    return cfg


# ── Dataset ────────────────────────────────────────────────────────────


@dataclass
class ZoneSample:
    """A single zone classification training sample."""
    text: str
    bboxes: list[list[int]]  # Each word's [x0, y0, x1, y1] (0-1000 scale)
    label: str
    words: list[str] = field(default_factory=list)


# Keyword patterns per zone type for synthetic data generation
_ZONE_KEYWORDS: dict[str, list[str]] = {
    "BALANCE_SHEET": [
        "Total Assets", "Total Liabilities", "Shareholders Equity",
        "Current Assets", "Non-Current Assets", "Current Liabilities",
        "Cash and Cash Equivalents", "Trade Receivables", "Inventories",
        "Property Plant and Equipment", "Goodwill", "Trade Payables",
        "Share Capital", "Retained Earnings",
    ],
    "INCOME_STATEMENT": [
        "Revenue", "Cost of Sales", "Gross Profit", "Operating Profit",
        "Profit Before Tax", "Income Tax Expense", "Profit for the Year",
        "Administrative Expenses", "Distribution Costs", "Finance Costs",
        "Earnings Per Share", "Depreciation",
    ],
    "CASH_FLOW": [
        "Cash from Operations", "Cash from Investing", "Cash from Financing",
        "Dividends Paid", "Capital Expenditure", "Net Cash Increase",
        "Cash at Beginning", "Cash at End", "Working Capital Changes",
    ],
    "NOTES_FIXED_ASSETS": [
        "Property Plant Equipment", "Land and Buildings", "Machinery",
        "Accumulated Depreciation", "Net Book Value", "Additions",
        "Disposals", "Revaluation",
    ],
    "NOTES_RECEIVABLES": [
        "Trade Receivables", "Aging Analysis", "Impairment", "Provisions",
        "Expected Credit Losses", "Past Due", "Neither Past Due",
    ],
    "NOTES_DEBT": [
        "Borrowings", "Long-term Debt", "Interest Rate", "Maturity",
        "Secured Loans", "Unsecured Loans", "Covenants", "Repayment Schedule",
    ],
    "NOTES_OTHER": [
        "Accounting Policies", "Related Party", "Contingent Liabilities",
        "Subsequent Events", "Risk Management", "Fair Value",
    ],
    "OTHER": [
        "Auditor Report", "Directors Report", "Corporate Governance",
        "Table of Contents", "Index", "Glossary",
    ],
}


def _generate_synthetic_sample(
    rng: random.Random, zone: str, keywords: list[str],
) -> ZoneSample:
    """Generate a synthetic zone classification sample."""
    num_items = rng.randint(5, 15)
    selected = [rng.choice(keywords) for _ in range(num_items)]
    # Add some numeric values
    words: list[str] = []
    bboxes: list[list[int]] = []
    y = 50
    for item in selected:
        item_words = item.split()
        x = 50
        for w in item_words:
            words.append(w)
            x1 = min(x + len(w) * 20, 950)
            bboxes.append([x, y, x1, y + 30])
            x = x1 + 10
        # Add a numeric value
        val = str(rng.randint(100, 99999))
        words.append(val)
        bboxes.append([700, y, 900, y + 30])
        y += 40
        if y > 950:
            y = 50

    text = " ".join(words)
    return ZoneSample(text=text, bboxes=bboxes, label=zone, words=words)


def generate_synthetic_data(cfg: ZoneClassConfig, num_per_zone: int = 100) -> list[ZoneSample]:
    """Generate synthetic zone classification samples."""
    rng = random.Random(cfg.seed)
    samples: list[ZoneSample] = []
    for zone, keywords in _ZONE_KEYWORDS.items():
        for _ in range(num_per_zone):
            samples.append(_generate_synthetic_sample(rng, zone, keywords))
    rng.shuffle(samples)
    logger.info("Generated %d synthetic zone samples", len(samples))
    return samples


def load_data(cfg: ZoneClassConfig) -> list[ZoneSample]:
    """Load zone classification data from disk or generate synthetic."""
    sample_dir = Path(cfg.sample_documents_dir)
    samples: list[ZoneSample] = []
    if sample_dir.exists():
        for json_path in sorted(sample_dir.glob("**/*.json")):
            try:
                with open(json_path) as fh:
                    doc = json.load(fh)
                for zone_data in doc.get("zones", []):
                    label = zone_data.get("zone_type", "OTHER")
                    if label not in ZONE_LABEL2ID:
                        label = "OTHER"
                    words = zone_data.get("words", [])
                    bboxes = zone_data.get("bboxes", [])
                    text = zone_data.get("text", " ".join(words))
                    if words and bboxes and len(words) == len(bboxes):
                        samples.append(ZoneSample(
                            text=text, bboxes=bboxes, label=label, words=words,
                        ))
            except Exception:
                continue
    if samples:
        logger.info("Loaded %d real zone samples from disk", len(samples))
        return samples

    logger.info("No zone-labeled data found — generating synthetic training data")
    return generate_synthetic_data(cfg)


class ZoneDataset(Dataset):
    """Dataset for LayoutLM zone classification."""

    def __init__(
        self, samples: list[ZoneSample], tokenizer: LayoutLMTokenizer,
        max_length: int = 512,
    ):
        self.samples = samples
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> dict[str, torch.Tensor]:
        sample = self.samples[idx]
        words = sample.words or sample.text.split()
        bboxes = sample.bboxes

        # Ensure bboxes match words
        while len(bboxes) < len(words):
            bboxes.append([0, 0, 0, 0])
        bboxes = bboxes[: len(words)]

        encoding = self.tokenizer(
            words,
            is_split_into_words=True,
            padding="max_length",
            truncation=True,
            max_length=self.max_length,
            return_tensors="pt",
        )

        # Align bboxes to tokenized output
        word_ids = encoding.word_ids(batch_index=0)
        aligned_bboxes: list[list[int]] = []
        for word_id in word_ids:
            if word_id is not None and word_id < len(bboxes):
                aligned_bboxes.append(bboxes[word_id])
            else:
                aligned_bboxes.append([0, 0, 0, 0])

        label_id = ZONE_LABEL2ID.get(sample.label, ZONE_LABEL2ID["OTHER"])

        return {
            "input_ids": encoding["input_ids"].squeeze(0),
            "attention_mask": encoding["attention_mask"].squeeze(0),
            "bbox": torch.tensor(aligned_bboxes, dtype=torch.long),
            "labels": torch.tensor(label_id, dtype=torch.long),
        }


# ── Training ───────────────────────────────────────────────────────────


def split_data(
    samples: list[ZoneSample], cfg: ZoneClassConfig,
) -> tuple[list[ZoneSample], list[ZoneSample], list[ZoneSample]]:
    rng = random.Random(cfg.seed)
    indices = list(range(len(samples)))
    rng.shuffle(indices)
    n_train = int(len(samples) * cfg.train_split)
    n_val = int(len(samples) * cfg.val_split)
    return (
        [samples[i] for i in indices[:n_train]],
        [samples[i] for i in indices[n_train:n_train + n_val]],
        [samples[i] for i in indices[n_train + n_val:]],
    )


@torch.no_grad()
def evaluate(
    model: LayoutLMForSequenceClassification,
    dataloader: DataLoader,
    device: torch.device,
) -> dict[str, float]:
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0
    n_batches = 0

    for batch in dataloader:
        batch = {k: v.to(device) for k, v in batch.items()}
        outputs = model(**batch)
        total_loss += outputs.loss.item()
        preds = outputs.logits.argmax(dim=-1)
        correct += (preds == batch["labels"]).sum().item()
        total += batch["labels"].size(0)
        n_batches += 1

    return {
        "loss": total_loss / max(n_batches, 1),
        "accuracy": correct / max(total, 1),
    }


def train(cfg: ZoneClassConfig) -> None:
    """Fine-tune LayoutLM for zone classification."""
    logger.info("=== LayoutLM Zone Classification Training ===")

    Path(cfg.output_dir).mkdir(parents=True, exist_ok=True)
    Path(cfg.checkpoint_dir).mkdir(parents=True, exist_ok=True)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    logger.info("Device: %s", device)

    # Load data
    all_samples = load_data(cfg)
    train_samples, val_samples, test_samples = split_data(all_samples, cfg)
    logger.info("Split: train=%d, val=%d, test=%d",
                len(train_samples), len(val_samples), len(test_samples))

    # Tokenizer & model
    logger.info("Loading model: %s", cfg.model_name)
    tokenizer = LayoutLMTokenizer.from_pretrained(cfg.model_name)
    model = LayoutLMForSequenceClassification.from_pretrained(
        cfg.model_name,
        num_labels=cfg.num_labels,
        id2label=ZONE_ID2LABEL,
        label2id=ZONE_LABEL2ID,
    )
    model.to(device)

    # Datasets
    train_dataset = ZoneDataset(train_samples, tokenizer, cfg.max_length)
    val_dataset = ZoneDataset(val_samples, tokenizer, cfg.max_length)

    train_loader = DataLoader(train_dataset, batch_size=cfg.batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=cfg.batch_size)

    # Optimizer & scheduler
    no_decay = {"bias", "LayerNorm.weight"}
    optimizer_groups = [
        {
            "params": [p for n, p in model.named_parameters() if not any(nd in n for nd in no_decay)],
            "weight_decay": cfg.weight_decay,
        },
        {
            "params": [p for n, p in model.named_parameters() if any(nd in n for nd in no_decay)],
            "weight_decay": 0.0,
        },
    ]
    optimizer = AdamW(optimizer_groups, lr=cfg.learning_rate)
    total_steps = len(train_loader) * cfg.num_epochs
    scheduler = get_linear_schedule_with_warmup(
        optimizer, num_warmup_steps=cfg.warmup_steps, num_training_steps=total_steps,
    )

    # MLflow
    try:
        import mlflow
        mlflow.set_experiment(cfg.mlflow_experiment)
        mlflow.start_run()
        mlflow.log_params({
            "model_name": cfg.model_name, "lr": cfg.learning_rate,
            "epochs": cfg.num_epochs, "batch_size": cfg.batch_size,
            "train_size": len(train_samples), "num_labels": cfg.num_labels,
        })
    except Exception:
        logger.info("MLflow not available — training without experiment tracking")

    best_val_acc = 0.0
    patience_counter = 0

    for epoch in range(1, cfg.num_epochs + 1):
        model.train()
        epoch_loss = 0.0
        n_batches = 0

        for batch in train_loader:
            batch = {k: v.to(device) for k, v in batch.items()}
            outputs = model(**batch)
            loss = outputs.loss
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), cfg.gradient_clip_norm)
            optimizer.step()
            scheduler.step()
            optimizer.zero_grad()
            epoch_loss += loss.item()
            n_batches += 1

        avg_train_loss = epoch_loss / max(n_batches, 1)
        val_metrics = evaluate(model, val_loader, device)

        logger.info(
            "Epoch %2d/%d — train_loss=%.4f  val_loss=%.4f  val_acc=%.4f",
            epoch, cfg.num_epochs, avg_train_loss,
            val_metrics["loss"], val_metrics["accuracy"],
        )

        try:
            import mlflow
            mlflow.log_metrics({
                "train_loss": avg_train_loss,
                "val_loss": val_metrics["loss"],
                "val_accuracy": val_metrics["accuracy"],
            }, step=epoch)
        except Exception:
            pass

        if val_metrics["accuracy"] > best_val_acc:
            best_val_acc = val_metrics["accuracy"]
            patience_counter = 0
            model.save_pretrained(cfg.output_dir)
            tokenizer.save_pretrained(cfg.output_dir)
            logger.info("  → Saved best model (val_acc=%.4f)", best_val_acc)
        else:
            patience_counter += 1
            if patience_counter >= cfg.early_stopping_patience:
                logger.info("Early stopping at epoch %d", epoch)
                break

    # Test evaluation
    if test_samples:
        test_dataset = ZoneDataset(test_samples, tokenizer, cfg.max_length)
        test_loader = DataLoader(test_dataset, batch_size=cfg.batch_size)
        model = LayoutLMForSequenceClassification.from_pretrained(cfg.output_dir)
        model.to(device)
        test_metrics = evaluate(model, test_loader, device)
        logger.info("Test — loss=%.4f  accuracy=%.4f",
                     test_metrics["loss"], test_metrics["accuracy"])
        try:
            import mlflow
            mlflow.log_metrics({
                "test_loss": test_metrics["loss"],
                "test_accuracy": test_metrics["accuracy"],
            })
        except Exception:
            pass

    try:
        import mlflow
        mlflow.end_run()
    except Exception:
        pass

    logger.info("Training complete. Model saved to %s", cfg.output_dir)


def main() -> None:
    parser = argparse.ArgumentParser(description="Train LayoutLM zone classifier")
    parser.add_argument(
        "--config", default="../configs/training_config.yaml",
        help="Path to training config YAML",
    )
    args = parser.parse_args()
    cfg = load_config(args.config)
    train(cfg)


if __name__ == "__main__":
    main()
