"""NG-MILP GNN Pruner Training — Binary Cross-Entropy on Edge Labels.

Trains the _EdgeScorer network used by the GNN pruner to rank candidate
sum-addend edges before the exact MILP optimization step.

Steps:
  1. Load financial table data with known arithmetic relationships
  2. Build candidate graphs with labeled edges (correct addend = 1, not = 0)
  3. Train edge scorer with weighted binary cross-entropy
  4. Export weights to ml-service/models/ng_milp_gnn_weights.pt

Usage:
    python 12_train_ng_milp_gnn.py [--config ../configs/ng_milp_gnn_config.yaml]
"""

from __future__ import annotations

import argparse
import logging
import math
import sys
from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path

import numpy as np
import yaml

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("ng_milp_gnn_training")

try:
    import torch
    from torch import nn
    from torch.optim import AdamW
    from torch.optim.lr_scheduler import CosineAnnealingLR
except ImportError:
    logger.error("PyTorch is required. Install with: pip install torch")
    sys.exit(1)


# ── Configuration ──────────────────────────────────────────────────────────


@dataclass
class GNNTrainingConfig:
    # Model
    feature_dim: int = 7
    hidden_dims: list[int] = field(default_factory=lambda: [32, 16])
    dropout: float = 0.2

    # Training
    learning_rate: float = 1e-3
    weight_decay: float = 1e-4
    batch_size: int = 64
    num_epochs: int = 40
    pos_weight: float = 3.0
    early_stopping_patience: int = 5
    warmup_epochs: int = 2
    gradient_clip_norm: float = 1.0
    label_smoothing: float = 0.05

    # Data
    sample_documents_dir: str = "data/sample_documents"
    train_split: float = 0.8
    val_split: float = 0.1
    test_split: float = 0.1
    tolerance: float = 0.005
    max_candidates_per_cell: int = 15
    candidate_row_window: int = 16
    max_addends: int = 6

    # Output
    weights_path: str = "ml-service/models/ng_milp_gnn_weights.pt"
    checkpoint_dir: str = "ml-training/checkpoints/ng_milp_gnn"
    log_dir: str = "ml-training/logs/ng_milp_gnn"

    # MLflow
    mlflow_experiment: str = "ng-milp-gnn-pruner"
    mlflow_model_name: str = "ng-milp-gnn-pruner"


def load_config(path: str | None) -> GNNTrainingConfig:
    cfg = GNNTrainingConfig()
    if path and Path(path).exists():
        with open(path) as fh:
            raw = yaml.safe_load(fh) or {}
        for section in ("model", "training", "data", "output"):
            for key, val in raw.get(section, {}).items():
                if hasattr(cfg, key):
                    setattr(cfg, key, val)
        mlf = raw.get("mlflow", {})
        if "experiment" in mlf:
            cfg.mlflow_experiment = mlf["experiment"]
        if "model_name" in mlf:
            cfg.mlflow_model_name = mlf["model_name"]
        logger.info("Loaded config from %s", path)
    return cfg


# ── Edge Scorer (mirrors ml-service/app/ml/ng_milp/gnn_pruner.py) ─────


class EdgeScorer(nn.Module):
    """Lightweight MLP that scores candidate edges."""

    def __init__(self, feature_dim: int, hidden_dims: list[int] | None = None, dropout: float = 0.2):
        super().__init__()
        hidden_dims = hidden_dims or [32, 16]
        layers: list[nn.Module] = []
        in_dim = feature_dim
        for h_dim in hidden_dims:
            layers.extend([nn.Linear(in_dim, h_dim), nn.ReLU(), nn.Dropout(dropout)])
            in_dim = h_dim
        layers.extend([nn.Linear(in_dim, 1), nn.Sigmoid()])
        self.network = nn.Sequential(*layers)

    def forward(self, features: torch.Tensor) -> torch.Tensor:
        return self.network(features).squeeze(-1)


# ── Data structures ────────────────────────────────────────────────────


@dataclass
class CellValue:
    row: int
    col: int
    value: float
    label: str
    indent_level: int = 0
    is_bold: bool = False
    is_total: bool = False
    page: int = 0


@dataclass
class LabeledEdge:
    sum_index: int
    addend_index: int
    features: np.ndarray  # 7-element feature vector
    is_correct: int  # 1 = true addend, 0 = not


# ── Synthetic data generation ──────────────────────────────────────────


def _generate_synthetic_table(
    rng: np.random.Generator, num_rows: int,
) -> tuple[list[CellValue], list[tuple[int, list[int]]]]:
    """Generate a synthetic financial table with known sum relationships.

    Returns cells and ground-truth relationships as (sum_idx, [addend_idx, ...]).
    """
    labels = [
        "Revenue", "Cost of Sales", "Gross Profit", "Operating Expenses",
        "Admin", "Marketing", "R&D", "Operating Income",
        "Interest Expense", "Tax Expense", "Net Income", "Depreciation",
        "Capital Expenditure", "Working Capital", "Free Cash Flow", "EBITDA",
    ]
    cells: list[CellValue] = []
    relationships: list[tuple[int, list[int]]] = []

    for r in range(num_rows):
        value = round(float(rng.uniform(100, 10000)), 2)
        cells.append(CellValue(
            row=r, col=1, value=value,
            label=labels[r % len(labels)],
            indent_level=rng.integers(0, 3),
            is_bold=rng.random() > 0.7,
            is_total=False,
        ))

    # Insert sum relationships (every 3-5 rows, mark one as a total)
    i = 0
    while i < num_rows:
        group_size = min(rng.integers(2, 5), num_rows - i - 1)
        if group_size < 2:
            i += 1
            continue
        addend_indices = list(range(i, i + group_size))
        sum_idx = i + group_size
        if sum_idx >= num_rows:
            break
        # Set the sum cell to be the actual sum
        total = sum(cells[j].value for j in addend_indices)
        cells[sum_idx] = CellValue(
            row=sum_idx, col=1, value=round(total, 2),
            label=cells[sum_idx].label, indent_level=0,
            is_bold=True, is_total=True,
        )
        relationships.append((sum_idx, addend_indices))
        i = sum_idx + 1

    return cells, relationships


def _edge_features(cells: list[CellValue], sum_idx: int, addend_idx: int) -> np.ndarray:
    """Compute the 7-dimensional feature vector for an edge."""
    s = cells[sum_idx]
    a = cells[addend_idx]
    spatial_dist = abs(s.row - a.row)
    hierarchy_gap = s.indent_level - a.indent_level
    value_ratio = abs(a.value) / max(abs(s.value), 1e-8)
    return np.array([
        abs(s.value),
        abs(a.value),
        float(spatial_dist),
        float(hierarchy_gap),
        value_ratio,
        1.0 if s.is_total else 0.0,
        1.0 if a.indent_level > s.indent_level else 0.0,
    ], dtype=np.float32)


def generate_labeled_edges(
    cfg: GNNTrainingConfig, num_tables: int = 200,
) -> list[LabeledEdge]:
    """Generate labeled edge samples from synthetic tables."""
    rng = np.random.default_rng(42)
    all_edges: list[LabeledEdge] = []

    for _ in range(num_tables):
        num_rows = rng.integers(10, 30)
        cells, relationships = _generate_synthetic_table(rng, num_rows)

        # Build ground-truth set
        gt_edges: set[tuple[int, int]] = set()
        for sum_idx, addend_indices in relationships:
            for a_idx in addend_indices:
                gt_edges.add((sum_idx, a_idx))

        # Generate candidate edges for each sum cell
        sum_indices = {r[0] for r in relationships}
        for sum_idx in sum_indices:
            # Candidates within row window
            for a_idx in range(max(0, sum_idx - cfg.candidate_row_window), sum_idx):
                if a_idx == sum_idx:
                    continue
                features = _edge_features(cells, sum_idx, a_idx)
                is_correct = 1 if (sum_idx, a_idx) in gt_edges else 0
                all_edges.append(LabeledEdge(
                    sum_index=sum_idx, addend_index=a_idx,
                    features=features, is_correct=is_correct,
                ))

    pos = sum(1 for e in all_edges if e.is_correct)
    neg = len(all_edges) - pos
    logger.info("Generated %d labeled edges (pos=%d, neg=%d, ratio=%.2f)",
                len(all_edges), pos, neg, pos / max(neg, 1))
    return all_edges


def load_data(cfg: GNNTrainingConfig) -> list[LabeledEdge]:
    """Load real data from sample_documents or generate synthetic."""
    sample_dir = Path(cfg.sample_documents_dir)
    if sample_dir.exists():
        import json
        edges: list[LabeledEdge] = []
        for json_path in sorted(sample_dir.glob("**/*.json")):
            try:
                with open(json_path) as fh:
                    doc = json.load(fh)
                cells_raw = doc.get("cells", [])
                rels_raw = doc.get("relationships", [])
                if not cells_raw or not rels_raw:
                    continue
                cells = [
                    CellValue(
                        row=c.get("row", 0), col=c.get("col", 0),
                        value=float(c.get("value", 0.0)),
                        label=c.get("label", ""),
                        indent_level=c.get("indent_level", 0),
                        is_bold=c.get("is_bold", False),
                        is_total=c.get("is_total", False),
                    )
                    for c in cells_raw
                ]
                gt: set[tuple[int, int]] = set()
                for rel in rels_raw:
                    s = rel.get("sum_index")
                    for a in rel.get("addend_indices", []):
                        gt.add((s, a))
                for sum_idx in {s for s, _ in gt}:
                    for a_idx in range(max(0, sum_idx - cfg.candidate_row_window), sum_idx):
                        features = _edge_features(cells, sum_idx, a_idx)
                        edges.append(LabeledEdge(
                            sum_index=sum_idx, addend_index=a_idx,
                            features=features,
                            is_correct=1 if (sum_idx, a_idx) in gt else 0,
                        ))
            except Exception:
                continue
        if edges:
            logger.info("Loaded %d real edges from disk", len(edges))
            return edges

    logger.info("No arithmetic-labeled data found — generating synthetic training data")
    return generate_labeled_edges(cfg)


# ── Training loop ──────────────────────────────────────────────────────


def split_data(
    edges: list[LabeledEdge], cfg: GNNTrainingConfig,
) -> tuple[list[LabeledEdge], list[LabeledEdge], list[LabeledEdge]]:
    rng = np.random.default_rng(123)
    indices = rng.permutation(len(edges))
    n_train = int(len(edges) * cfg.train_split)
    n_val = int(len(edges) * cfg.val_split)
    return (
        [edges[i] for i in indices[:n_train]],
        [edges[i] for i in indices[n_train:n_train + n_val]],
        [edges[i] for i in indices[n_train + n_val:]],
    )


def edges_to_tensors(
    edges: list[LabeledEdge],
) -> tuple[torch.Tensor, torch.Tensor]:
    features = np.array([e.features for e in edges], dtype=np.float32)
    labels = np.array([e.is_correct for e in edges], dtype=np.float32)
    return torch.from_numpy(features), torch.from_numpy(labels)


def train_epoch(
    model: EdgeScorer,
    features: torch.Tensor,
    labels: torch.Tensor,
    criterion: nn.Module,
    optimizer: torch.optim.Optimizer,
    cfg: GNNTrainingConfig,
) -> float:
    model.train()
    total_loss = 0.0
    n_batches = 0
    indices = torch.randperm(features.size(0))

    for i in range(0, features.size(0), cfg.batch_size):
        batch_idx = indices[i : i + cfg.batch_size]
        x = features[batch_idx]
        y = labels[batch_idx]

        # Label smoothing
        if cfg.label_smoothing > 0:
            y = y * (1 - cfg.label_smoothing) + 0.5 * cfg.label_smoothing

        pred = model(x)
        loss = criterion(pred, y)

        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), cfg.gradient_clip_norm)
        optimizer.step()

        total_loss += loss.item()
        n_batches += 1

    return total_loss / max(n_batches, 1)


@torch.no_grad()
def evaluate(
    model: EdgeScorer,
    features: torch.Tensor,
    labels: torch.Tensor,
    criterion: nn.Module,
    cfg: GNNTrainingConfig,
) -> dict[str, float]:
    model.eval()
    pred = model(features)
    loss = criterion(pred, labels).item()

    predicted = (pred > 0.5).float()
    accuracy = (predicted == labels).float().mean().item()

    tp = ((predicted == 1) & (labels == 1)).sum().item()
    fp = ((predicted == 1) & (labels == 0)).sum().item()
    fn = ((predicted == 0) & (labels == 1)).sum().item()
    precision = tp / max(tp + fp, 1)
    recall = tp / max(tp + fn, 1)
    f1 = 2 * precision * recall / max(precision + recall, 1e-8)

    return {
        "loss": loss,
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "f1": f1,
    }


def train(cfg: GNNTrainingConfig) -> None:
    """Full training pipeline."""
    logger.info("=== NG-MILP GNN Pruner Training ===")

    Path(cfg.checkpoint_dir).mkdir(parents=True, exist_ok=True)
    Path(cfg.weights_path).parent.mkdir(parents=True, exist_ok=True)

    # Load & split data
    all_edges = load_data(cfg)
    train_edges, val_edges, test_edges = split_data(all_edges, cfg)
    logger.info("Split: train=%d, val=%d, test=%d", len(train_edges), len(val_edges), len(test_edges))

    train_x, train_y = edges_to_tensors(train_edges)
    val_x, val_y = edges_to_tensors(val_edges)
    test_x, test_y = edges_to_tensors(test_edges)

    # Model
    model = EdgeScorer(
        feature_dim=cfg.feature_dim,
        hidden_dims=cfg.hidden_dims,
        dropout=cfg.dropout,
    )
    logger.info("Model parameters: %d", sum(p.numel() for p in model.parameters()))

    # Loss with class weight
    pos_weight = torch.tensor([cfg.pos_weight])
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)

    # We need raw logits for BCEWithLogitsLoss, so remove the final Sigmoid
    # and add it back only during inference
    model.network[-1] = nn.Identity()

    optimizer = AdamW(model.parameters(), lr=cfg.learning_rate, weight_decay=cfg.weight_decay)
    scheduler = CosineAnnealingLR(optimizer, T_max=cfg.num_epochs - cfg.warmup_epochs)

    # MLflow
    mlflow_run = None
    try:
        import mlflow
        mlflow.set_experiment(cfg.mlflow_experiment)
        mlflow_run = mlflow.start_run()
        mlflow.log_params({
            "feature_dim": cfg.feature_dim, "hidden_dims": str(cfg.hidden_dims),
            "lr": cfg.learning_rate, "epochs": cfg.num_epochs,
            "pos_weight": cfg.pos_weight, "train_size": len(train_edges),
        })
    except Exception:
        logger.info("MLflow not available — training without experiment tracking")

    best_val_f1 = 0.0
    patience_counter = 0

    for epoch in range(1, cfg.num_epochs + 1):
        train_loss = train_epoch(model, train_x, train_y, criterion, optimizer, cfg)

        # For evaluation, apply sigmoid to model outputs
        val_metrics = evaluate(model, val_x, val_y, criterion, cfg)

        if epoch > cfg.warmup_epochs:
            scheduler.step()

        logger.info(
            "Epoch %3d/%d — train_loss=%.4f  val_loss=%.4f  val_f1=%.4f  val_prec=%.4f  val_rec=%.4f",
            epoch, cfg.num_epochs, train_loss,
            val_metrics["loss"], val_metrics["f1"],
            val_metrics["precision"], val_metrics["recall"],
        )

        if mlflow_run:
            import mlflow
            mlflow.log_metrics({
                "train_loss": train_loss,
                "val_loss": val_metrics["loss"],
                "val_f1": val_metrics["f1"],
                "val_precision": val_metrics["precision"],
                "val_recall": val_metrics["recall"],
            }, step=epoch)

        if val_metrics["f1"] > best_val_f1:
            best_val_f1 = val_metrics["f1"]
            patience_counter = 0
            # Save with Sigmoid for inference
            export_model = EdgeScorer(cfg.feature_dim, cfg.hidden_dims, cfg.dropout)
            export_model.load_state_dict(model.state_dict(), strict=False)
            # Copy weights into a model with Sigmoid
            state = model.state_dict()
            torch.save(state, cfg.weights_path)
            logger.info("  → Saved best model (val_f1=%.4f)", best_val_f1)
        else:
            patience_counter += 1
            if patience_counter >= cfg.early_stopping_patience:
                logger.info("Early stopping at epoch %d", epoch)
                break

    # Test evaluation
    if test_x.size(0) > 0:
        model.load_state_dict(torch.load(cfg.weights_path, weights_only=True))
        test_metrics = evaluate(model, test_x, test_y, criterion, cfg)
        logger.info(
            "Test — loss=%.4f  f1=%.4f  prec=%.4f  rec=%.4f  acc=%.4f",
            test_metrics["loss"], test_metrics["f1"],
            test_metrics["precision"], test_metrics["recall"],
            test_metrics["accuracy"],
        )
        if mlflow_run:
            import mlflow
            mlflow.log_metrics({
                "test_loss": test_metrics["loss"],
                "test_f1": test_metrics["f1"],
                "test_precision": test_metrics["precision"],
                "test_recall": test_metrics["recall"],
            })

    if mlflow_run:
        import mlflow
        mlflow.end_run()

    logger.info("Training complete. Weights saved to %s", cfg.weights_path)


def main() -> None:
    parser = argparse.ArgumentParser(description="Train NG-MILP GNN edge pruner")
    parser.add_argument(
        "--config", default="../configs/ng_milp_gnn_config.yaml",
        help="Path to training config YAML",
    )
    args = parser.parse_args()
    cfg = load_config(args.config)
    train(cfg)


if __name__ == "__main__":
    main()
