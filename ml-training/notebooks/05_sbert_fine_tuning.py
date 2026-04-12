"""SBERT Fine-Tuning for IFRS Terms — Enhanced with Taxonomy Data.

Fine-tunes a sentence-transformer model to produce embeddings that
place IFRS-standard terms close to their common synonyms in vector space.

Training data is generated from the IFRS taxonomy (data/ifrs_taxonomy.json),
where each canonical term maps to a list of synonyms. The model learns
via MultipleNegativesRankingLoss (contrastive) on (anchor, positive) pairs.

Usage:
    python 05_sbert_fine_tuning.py [--config ../configs/training_config.yaml]
"""

from __future__ import annotations

import argparse
import itertools
import json
import logging
import random
import sys
from dataclasses import dataclass
from pathlib import Path

import yaml

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("sbert_ifrs_finetuning")

try:
    import torch
    from sentence_transformers import InputExample, SentenceTransformer, losses
    from sentence_transformers.evaluation import EmbeddingSimilarityEvaluator
    from torch.utils.data import DataLoader
except ImportError:
    logger.error(
        "Required packages missing. Install with:\n"
        "  pip install torch sentence-transformers"
    )
    sys.exit(1)


# ── Configuration ──────────────────────────────────────────────────────────


@dataclass
class SBERTConfig:
    model_name: str = "sentence-transformers/all-MiniLM-L6-v2"
    learning_rate: float = 2e-5
    batch_size: int = 32
    num_epochs: int = 5
    warmup_ratio: float = 0.1
    evaluation_steps: int = 200
    target_accuracy: float = 0.85

    taxonomy_path: str = "data/ifrs_taxonomy.json"
    augment_pairs: bool = True
    augment_templates: int = 5  # Number of template variations per synonym

    output_dir: str = "ml-training/models/sbert-ifrs"
    checkpoint_dir: str = "ml-training/checkpoints/sbert-ifrs"

    mlflow_experiment: str = "sbert-ifrs"
    mlflow_model_name: str = "sbert-ifrs-matcher"

    train_split: float = 0.85
    seed: int = 42


def load_config(path: str | None) -> SBERTConfig:
    cfg = SBERTConfig()
    if path and Path(path).exists():
        with open(path) as fh:
            raw = yaml.safe_load(fh) or {}
        sbert = raw.get("sbert", {})
        for key, val in sbert.items():
            if hasattr(cfg, key):
                setattr(cfg, key, val)
        logger.info("Loaded config from %s", path)
    return cfg


# ── IFRS Taxonomy Data ─────────────────────────────────────────────────


def load_taxonomy(taxonomy_path: str) -> dict[str, list[str]]:
    """Load IFRS taxonomy mapping canonical terms → synonyms."""
    path = Path(taxonomy_path)
    if not path.exists():
        logger.warning("Taxonomy file not found: %s", path)
        return {}
    with open(path) as fh:
        data = json.load(fh)
    logger.info("Loaded taxonomy with %d canonical terms", len(data))
    return data


# ── Training pair generation ───────────────────────────────────────────


_CONTEXT_TEMPLATES = [
    "{term}",
    "Total {term}",
    "{term} for the period",
    "{term} (audited)",
    "Net {term}",
    "{term} - as reported",
    "Consolidated {term}",
    "{term} attributable to shareholders",
    "Change in {term}",
    "Increase/(decrease) in {term}",
]


def generate_training_pairs(
    taxonomy: dict[str, list[str]], cfg: SBERTConfig,
) -> tuple[list[InputExample], list[InputExample]]:
    """Generate (anchor, positive) pairs from the taxonomy.

    Returns (train_examples, eval_examples).
    """
    rng = random.Random(cfg.seed)
    all_pairs: list[InputExample] = []

    for canonical, synonyms in taxonomy.items():
        # Each canonical ↔ synonym pair
        for synonym in synonyms:
            all_pairs.append(InputExample(texts=[canonical, synonym], label=1.0))
            all_pairs.append(InputExample(texts=[synonym, canonical], label=1.0))

        # Synonym ↔ synonym pairs (transitive similarity)
        for syn_a, syn_b in itertools.combinations(synonyms, 2):
            all_pairs.append(InputExample(texts=[syn_a, syn_b], label=0.9))

        # Augmented variants with context templates
        if cfg.augment_pairs:
            templates = rng.sample(
                _CONTEXT_TEMPLATES,
                min(cfg.augment_templates, len(_CONTEXT_TEMPLATES)),
            )
            for template in templates:
                augmented = template.format(term=canonical)
                for synonym in synonyms:
                    all_pairs.append(InputExample(texts=[augmented, synonym], label=0.85))
                    aug_syn = template.format(term=synonym)
                    all_pairs.append(InputExample(texts=[augmented, aug_syn], label=0.95))

    # Negative pairs: different categories
    category_keys = list(taxonomy.keys())
    for _ in range(len(all_pairs) // 3):
        k1, k2 = rng.sample(category_keys, 2)
        term1 = rng.choice([k1] + taxonomy[k1])
        term2 = rng.choice([k2] + taxonomy[k2])
        all_pairs.append(InputExample(texts=[term1, term2], label=0.1))

    rng.shuffle(all_pairs)
    split_idx = int(len(all_pairs) * cfg.train_split)
    train_examples = all_pairs[:split_idx]
    eval_examples = all_pairs[split_idx:]

    logger.info("Generated %d training pairs, %d evaluation pairs",
                len(train_examples), len(eval_examples))
    return train_examples, eval_examples


# ── Evaluation ─────────────────────────────────────────────────────────


def build_evaluator(
    eval_examples: list[InputExample],
) -> EmbeddingSimilarityEvaluator:
    """Build an evaluator from the eval examples."""
    sentences1 = [e.texts[0] for e in eval_examples]
    sentences2 = [e.texts[1] for e in eval_examples]
    scores = [e.label for e in eval_examples]
    return EmbeddingSimilarityEvaluator(
        sentences1, sentences2, scores, name="ifrs-eval",
    )


# ── Training ───────────────────────────────────────────────────────────


def train(cfg: SBERTConfig) -> None:
    """Fine-tune the SBERT model on IFRS taxonomy pairs."""
    logger.info("=== SBERT IFRS Fine-Tuning ===")

    Path(cfg.output_dir).mkdir(parents=True, exist_ok=True)
    Path(cfg.checkpoint_dir).mkdir(parents=True, exist_ok=True)

    # Load taxonomy
    taxonomy = load_taxonomy(cfg.taxonomy_path)
    if not taxonomy:
        logger.error("Empty taxonomy — cannot train without data")
        return

    # Load base model
    logger.info("Loading base model: %s", cfg.model_name)
    model = SentenceTransformer(cfg.model_name)

    # Generate training data
    train_examples, eval_examples = generate_training_pairs(taxonomy, cfg)
    if not train_examples:
        logger.error("No training pairs generated")
        return

    train_dataloader = DataLoader(
        train_examples, shuffle=True, batch_size=cfg.batch_size,
    )
    evaluator = build_evaluator(eval_examples)

    # Loss — MultipleNegativesRankingLoss works well for synonym matching
    train_loss = losses.CosineSimilarityLoss(model)

    warmup_steps = int(
        len(train_dataloader) * cfg.num_epochs * cfg.warmup_ratio
    )

    # MLflow
    try:
        import mlflow
        mlflow.set_experiment(cfg.mlflow_experiment)
        mlflow.start_run()
        mlflow.log_params({
            "model_name": cfg.model_name,
            "lr": cfg.learning_rate,
            "batch_size": cfg.batch_size,
            "epochs": cfg.num_epochs,
            "train_pairs": len(train_examples),
            "eval_pairs": len(eval_examples),
            "taxonomy_terms": len(taxonomy),
        })
    except Exception:
        logger.info("MLflow not available — training without experiment tracking")

    logger.info(
        "Training: %d pairs, %d epochs, batch_size=%d, warmup=%d steps",
        len(train_examples), cfg.num_epochs, cfg.batch_size, warmup_steps,
    )

    model.fit(
        train_objectives=[(train_dataloader, train_loss)],
        evaluator=evaluator,
        epochs=cfg.num_epochs,
        evaluation_steps=cfg.evaluation_steps,
        warmup_steps=warmup_steps,
        output_path=cfg.output_dir,
        optimizer_params={"lr": cfg.learning_rate},
        checkpoint_path=cfg.checkpoint_dir,
        checkpoint_save_steps=cfg.evaluation_steps,
        show_progress_bar=True,
    )

    # Final evaluation
    final_score = evaluator(model, output_path=cfg.output_dir)
    logger.info("Final evaluation score: %.4f (target: %.4f)", final_score, cfg.target_accuracy)

    try:
        import mlflow
        mlflow.log_metric("final_eval_score", final_score)
        mlflow.end_run()
    except Exception:
        pass

    logger.info("Model saved to %s", cfg.output_dir)


def main() -> None:
    parser = argparse.ArgumentParser(description="Fine-tune SBERT for IFRS term matching")
    parser.add_argument(
        "--config", default="../configs/training_config.yaml",
        help="Path to training config YAML",
    )
    args = parser.parse_args()
    cfg = load_config(args.config)
    train(cfg)


if __name__ == "__main__":
    main()
