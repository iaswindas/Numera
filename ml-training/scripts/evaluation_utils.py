"""Model evaluation utilities for reporting accuracy, confusion matrices, etc.

Used by Colab notebooks to evaluate zone classifier and semantic matcher
performance on held-out test sets.
"""

import json
import logging
from pathlib import Path
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)


def compute_classification_metrics(
    true_labels: list[str],
    predicted_labels: list[str],
    label_names: Optional[list[str]] = None,
) -> dict:
    """Compute precision, recall, F1, and accuracy for classification.

    Args:
        true_labels: Ground truth labels.
        predicted_labels: Model predictions.
        label_names: Optional list of all possible labels.

    Returns:
        Dict with overall accuracy, per-class metrics, and confusion matrix.
    """
    from sklearn.metrics import (
        accuracy_score,
        classification_report,
        confusion_matrix,
    )

    accuracy = accuracy_score(true_labels, predicted_labels)
    report = classification_report(
        true_labels, predicted_labels, output_dict=True,
        labels=label_names, zero_division=0,
    )
    cm = confusion_matrix(
        true_labels, predicted_labels, labels=label_names
    ).tolist()

    return {
        "accuracy": accuracy,
        "classification_report": report,
        "confusion_matrix": cm,
        "label_names": label_names or sorted(set(true_labels + predicted_labels)),
    }


def compute_matching_metrics(
    true_item_ids: list[str],
    predicted_items: list[list[dict]],  # top-k predictions per row
    at_k: list[int] = [1, 3, 5],
) -> dict:
    """Compute Recall@K and MRR for semantic matching.

    Args:
        true_item_ids: Ground truth target line item IDs.
        predicted_items: List of top-k prediction dicts per source row,
                         each with 'target_line_item_id' and 'confidence'.
        at_k: K values to compute Recall@K for.

    Returns:
        Dict with recall@k, MRR, and average confidence stats.
    """
    results = {f"recall@{k}": 0.0 for k in at_k}
    mrr_sum = 0.0
    confidences = {"high": 0, "medium": 0, "low": 0, "unmapped": 0}
    total = len(true_item_ids)

    if total == 0:
        return results

    for i, true_id in enumerate(true_item_ids):
        preds = predicted_items[i] if i < len(predicted_items) else []
        pred_ids = [p.get("target_line_item_id", "") for p in preds]

        # Recall@K
        for k in at_k:
            if true_id in pred_ids[:k]:
                results[f"recall@{k}"] += 1

        # MRR
        if true_id in pred_ids:
            rank = pred_ids.index(true_id) + 1
            mrr_sum += 1.0 / rank

        # Confidence distribution
        if preds:
            top_conf = preds[0].get("confidence", 0)
            if top_conf >= 0.85:
                confidences["high"] += 1
            elif top_conf >= 0.65:
                confidences["medium"] += 1
            else:
                confidences["low"] += 1
        else:
            confidences["unmapped"] += 1

    for k in at_k:
        results[f"recall@{k}"] /= total

    results["mrr"] = mrr_sum / total
    results["confidence_distribution"] = confidences
    results["total_samples"] = total

    return results


def save_evaluation_report(
    metrics: dict,
    output_path: str,
    model_name: str = "",
    notes: str = "",
):
    """Save evaluation metrics to a JSON report."""
    report = {
        "model_name": model_name,
        "notes": notes,
        "metrics": metrics,
    }
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(report, f, indent=2)
    logger.info("Evaluation report saved to %s", output_path)
