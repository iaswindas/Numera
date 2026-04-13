"""STGH GCN Training Pipeline — Contrastive Learning on Document Fingerprint Embeddings.

Trains the DocumentGCN model used by the STGH fingerprinter to produce
structure-aware embeddings of financial document pages.

Steps:
  1. Load documents, build spatial graphs from OCR bounding boxes
  2. Generate positive/negative pairs (same/different documents)
  3. Train with InfoNCE contrastive loss
  4. Export weights to ml-service/models/stgh_gcn_weights.pt

Usage:
    python 11_train_stgh_gcn.py [--config ../configs/stgh_training_config.yaml]
"""

from __future__ import annotations

import argparse
import hashlib
import logging
import math
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import yaml

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("stgh_gcn_training")

try:
    import torch
    from torch import nn
    from torch.nn import functional as F
    from torch.optim import AdamW
    from torch.optim.lr_scheduler import CosineAnnealingLR
except ImportError:
    logger.error("PyTorch is required. Install with: pip install torch")
    sys.exit(1)


# ── Configuration ──────────────────────────────────────────────────────────


@dataclass
class STGHTrainingConfig:
    """Parsed training configuration."""

    # Model
    input_dim: int = 135
    gcn_hidden: int = 128
    gcn_output: int = 256
    semantic_dim: int = 128
    k_neighbors: int = 6

    # Training
    learning_rate: float = 1e-3
    weight_decay: float = 1e-4
    batch_size: int = 32
    num_epochs: int = 50
    margin: float = 1.0
    temperature: float = 0.07
    loss: str = "infonce"
    pairs_per_document: int = 5
    negative_ratio: int = 3
    early_stopping_patience: int = 7
    warmup_epochs: int = 3
    gradient_clip_norm: float = 1.0

    # Data
    sample_documents_dir: str = "data/sample_documents"
    train_split: float = 0.8
    val_split: float = 0.1
    test_split: float = 0.1
    min_nodes_per_page: int = 3
    max_pages_per_document: int = 20
    augment_noise_std: float = 0.01

    # Output
    weights_path: str = "ml-service/models/stgh_gcn_weights.pt"
    checkpoint_dir: str = "ml-training/checkpoints/stgh_gcn"
    log_dir: str = "ml-training/logs/stgh_gcn"

    # MLflow
    mlflow_experiment: str = "stgh-gcn-contrastive"
    mlflow_model_name: str = "stgh-gcn"


def load_config(path: str | None) -> STGHTrainingConfig:
    """Load config from YAML, falling back to defaults."""
    cfg = STGHTrainingConfig()
    if path and Path(path).exists():
        with open(path) as fh:
            raw = yaml.safe_load(fh) or {}
        model = raw.get("model", {})
        train = raw.get("training", {})
        data = raw.get("data", {})
        output = raw.get("output", {})
        mlf = raw.get("mlflow", {})
        for key, val in {**model, **train, **data, **output}.items():
            if hasattr(cfg, key):
                setattr(cfg, key, val)
        if "experiment" in mlf:
            cfg.mlflow_experiment = mlf["experiment"]
        if "model_name" in mlf:
            cfg.mlflow_model_name = mlf["model_name"]
        logger.info("Loaded config from %s", path)
    return cfg


# ── Graph data structures ──────────────────────────────────────────────────


@dataclass
class OCRNode:
    node_id: str
    text: str
    bbox: tuple[float, float, float, float]
    page_idx: int
    row_index: int = 0
    col_index: int = 0
    is_header: bool = False
    cell_type: str = "TEXT"


@dataclass
class SpatialGraph:
    nodes: list[OCRNode]
    adjacency: np.ndarray
    features: np.ndarray


@dataclass
class DocumentSample:
    """A single document containing one or more page graphs."""

    doc_id: str
    pages: list[SpatialGraph] = field(default_factory=list)


# ── GCN Architecture (mirrors ocr-service/app/ml/stgh/gcn.py) ─────────


class GraphConvolution(nn.Module):
    def __init__(self, in_features: int, out_features: int):
        super().__init__()
        self.weight = nn.Parameter(torch.empty(in_features, out_features))
        self.bias = nn.Parameter(torch.empty(out_features))
        nn.init.xavier_uniform_(self.weight)
        nn.init.zeros_(self.bias)

    def forward(self, x: torch.Tensor, adj: torch.Tensor) -> torch.Tensor:
        support = torch.mm(x, self.weight)
        output = torch.mm(adj, support)
        return output + self.bias


class DocumentGCN(nn.Module):
    """Three-layer graph encoder for document structure."""

    def __init__(self, input_dim: int, hidden_dim: int = 128, output_dim: int = 256):
        super().__init__()
        self.gc1 = GraphConvolution(input_dim, hidden_dim)
        self.bn1 = nn.BatchNorm1d(hidden_dim)
        self.gc2 = GraphConvolution(hidden_dim, 64)
        self.bn2 = nn.BatchNorm1d(64)
        self.gc3 = GraphConvolution(64, 32)
        self.readout = nn.Sequential(
            nn.Linear(32, output_dim),
            nn.ReLU(),
            nn.Linear(output_dim, output_dim),
        )

    def forward(self, features: torch.Tensor, adjacency: torch.Tensor) -> torch.Tensor:
        if features.shape[0] == 0:
            return torch.zeros(self.readout[-1].out_features, dtype=torch.float32)
        adjacency = self._normalize(adjacency)
        x = F.relu(self.bn1(self.gc1(features, adjacency)))
        x = F.relu(self.bn2(self.gc2(x, adjacency)))
        x = F.relu(self.gc3(x, adjacency))
        pooled = x.mean(dim=0)
        return F.normalize(self.readout(pooled), p=2, dim=0)

    @staticmethod
    def _normalize(adj: torch.Tensor) -> torch.Tensor:
        identity = torch.eye(adj.size(0), dtype=adj.dtype, device=adj.device)
        adj_hat = adj + identity
        deg = adj_hat.sum(dim=1)
        deg = torch.where(deg == 0, torch.ones_like(deg), deg)
        inv_sqrt = torch.pow(deg, -0.5)
        diag = torch.diag(inv_sqrt)
        return diag @ adj_hat @ diag


# ── Graph building utilities ───────────────────────────────────────────


def hash_text(text: str, dim: int) -> np.ndarray:
    """Deterministic hash-based text embedding (matches STGHFingerprinter)."""
    vector = np.zeros(dim, dtype=np.float32)
    tokens = re.findall(r"[a-z_]+", text.lower())
    if not tokens:
        tokens = [text.lower()]
    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        idx = int.from_bytes(digest[:4], "big") % dim
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[idx] += sign
    norm = np.linalg.norm(vector)
    if norm > 0:
        vector /= norm
    return vector


def build_spatial_graph(
    nodes: list[OCRNode], semantic_dim: int, k_neighbors: int,
) -> SpatialGraph:
    """Build a spatial graph from OCR nodes with features and adjacency."""
    if not nodes:
        return SpatialGraph(
            nodes=[], adjacency=np.zeros((0, 0), dtype=np.float32),
            features=np.zeros((0, semantic_dim + 7), dtype=np.float32),
        )

    # Semantic features
    semantic = np.array([hash_text(n.text, semantic_dim) for n in nodes], dtype=np.float32)

    # Spatial features: center_x, center_y, width, height
    spatial = np.array([
        (n.bbox[0] + n.bbox[2] / 2, n.bbox[1] + n.bbox[3] / 2, n.bbox[2], n.bbox[3])
        for n in nodes
    ], dtype=np.float32)

    # Type features: is_header, is_numeric, is_label
    types = np.array([
        (1.0 if n.is_header else 0.0,
         1.0 if n.cell_type == "NUMERIC" else 0.0,
         1.0 if n.cell_type in ("TEXT", "MIXED") else 0.0)
        for n in nodes
    ], dtype=np.float32)

    features = np.concatenate([semantic, spatial, types], axis=1)

    # Adjacency via k-NN + structural edges
    count = len(nodes)
    adj = np.zeros((count, count), dtype=np.float32)
    centers = spatial[:, :2]
    for i in range(count):
        dists = np.linalg.norm(centers - centers[i], axis=1)
        dists[i] = np.inf
        nearest = np.argsort(dists)[:k_neighbors]
        for j in nearest:
            adj[i, j] = adj[j, i] = 1.0
        # Structural: same row or column
        for j in range(count):
            if i != j and (nodes[i].row_index == nodes[j].row_index or
                           nodes[i].col_index == nodes[j].col_index):
                adj[i, j] = adj[j, i] = 1.0

    return SpatialGraph(nodes=nodes, adjacency=adj, features=features)


# ── Synthetic data generation ──────────────────────────────────────────


def _generate_synthetic_table(
    rng: np.random.Generator, page_idx: int, rows: int, cols: int,
) -> list[OCRNode]:
    """Generate synthetic OCR nodes mimicking a financial table."""
    labels = [
        "Revenue", "Cost of Sales", "Gross Profit", "Operating Expenses",
        "EBIT", "Interest Expense", "Tax Expense", "Net Income",
        "Total Assets", "Total Liabilities", "Equity", "Cash",
        "Receivables", "Inventory", "Payables", "Depreciation",
    ]
    nodes: list[OCRNode] = []
    for r in range(rows):
        for c in range(cols):
            x = c / max(cols, 1)
            y = r / max(rows, 1)
            w = 1.0 / max(cols, 1)
            h = 1.0 / max(rows, 1)
            is_header = r == 0
            if c == 0:
                text = labels[r % len(labels)] if not is_header else "Item"
                cell_type = "TEXT"
            else:
                text = f"{rng.integers(100, 99999)}"
                cell_type = "NUMERIC"
            nodes.append(OCRNode(
                node_id=f"p{page_idx}_r{r}_c{c}",
                text=text, bbox=(x, y, w, h),
                page_idx=page_idx, row_index=r, col_index=c,
                is_header=is_header, cell_type=cell_type,
            ))
    return nodes


def generate_synthetic_documents(
    num_docs: int, cfg: STGHTrainingConfig,
) -> list[DocumentSample]:
    """Generate synthetic document samples for training."""
    rng = np.random.default_rng(42)
    documents: list[DocumentSample] = []

    for doc_idx in range(num_docs):
        n_pages = rng.integers(1, min(4, cfg.max_pages_per_document) + 1)
        pages: list[SpatialGraph] = []
        for page_idx in range(n_pages):
            rows = rng.integers(5, 16)
            cols = rng.integers(3, 7)
            nodes = _generate_synthetic_table(rng, page_idx, rows, cols)
            graph = build_spatial_graph(nodes, cfg.semantic_dim, cfg.k_neighbors)
            if graph.features.shape[0] >= cfg.min_nodes_per_page:
                pages.append(graph)
        if pages:
            documents.append(DocumentSample(doc_id=f"doc_{doc_idx:04d}", pages=pages))

    logger.info("Generated %d synthetic documents with %d total pages",
                len(documents), sum(len(d.pages) for d in documents))
    return documents


def load_documents(cfg: STGHTrainingConfig) -> list[DocumentSample]:
    """Load documents from sample_documents_dir or generate synthetic ones."""
    sample_dir = Path(cfg.sample_documents_dir)
    if sample_dir.exists() and any(sample_dir.iterdir()):
        logger.info("Loading documents from %s", sample_dir)
        # Walk JSON document descriptors if available
        documents: list[DocumentSample] = []
        import json
        for json_path in sorted(sample_dir.glob("**/*.json")):
            try:
                with open(json_path) as fh:
                    doc_data = json.load(fh)
                pages_raw = doc_data.get("pages", [])
                pages: list[SpatialGraph] = []
                for page_data in pages_raw:
                    nodes: list[OCRNode] = []
                    for cell in page_data.get("cells", page_data.get("nodes", [])):
                        bbox = cell.get("bbox", [0, 0, 0.1, 0.1])
                        if len(bbox) == 4:
                            bbox_tuple = (float(bbox[0]), float(bbox[1]),
                                          float(bbox[2]), float(bbox[3]))
                        else:
                            bbox_tuple = (0.0, 0.0, 0.1, 0.1)
                        nodes.append(OCRNode(
                            node_id=cell.get("id", ""),
                            text=cell.get("text", ""),
                            bbox=bbox_tuple,
                            page_idx=page_data.get("page", 0),
                            row_index=cell.get("row", 0),
                            col_index=cell.get("col", 0),
                            is_header=cell.get("is_header", False),
                            cell_type=cell.get("cell_type", "TEXT"),
                        ))
                    graph = build_spatial_graph(nodes, cfg.semantic_dim, cfg.k_neighbors)
                    if graph.features.shape[0] >= cfg.min_nodes_per_page:
                        pages.append(graph)
                if pages:
                    documents.append(DocumentSample(
                        doc_id=json_path.stem, pages=pages,
                    ))
            except Exception as exc:
                logger.warning("Skipping %s: %s", json_path.name, exc)
        if documents:
            logger.info("Loaded %d documents from disk", len(documents))
            return documents

    logger.info("No sample documents found — generating synthetic training data")
    return generate_synthetic_documents(200, cfg)


# ── Pair generation ────────────────────────────────────────────────────


def generate_pairs(
    documents: list[DocumentSample], cfg: STGHTrainingConfig,
) -> tuple[list[tuple[SpatialGraph, SpatialGraph, int]], ...]:
    """Generate positive (1) and negative (0) graph pairs.

    Returns (train_pairs, val_pairs, test_pairs).
    """
    rng = np.random.default_rng(123)

    # Group pages by document
    doc_pages: dict[str, list[SpatialGraph]] = {}
    for doc in documents:
        doc_pages[doc.doc_id] = doc.pages

    doc_ids = list(doc_pages.keys())
    rng.shuffle(doc_ids)

    n_train = int(len(doc_ids) * cfg.train_split)
    n_val = int(len(doc_ids) * cfg.val_split)
    splits = {
        "train": doc_ids[:n_train],
        "val": doc_ids[n_train:n_train + n_val],
        "test": doc_ids[n_train + n_val:],
    }

    result: dict[str, list[tuple[SpatialGraph, SpatialGraph, int]]] = {}
    for split_name, ids in splits.items():
        pairs: list[tuple[SpatialGraph, SpatialGraph, int]] = []
        for doc_id in ids:
            pages = doc_pages[doc_id]
            # Positive pairs: pages from the same document
            for _ in range(min(cfg.pairs_per_document, len(pages))):
                if len(pages) >= 2:
                    i, j = rng.choice(len(pages), size=2, replace=False)
                    pairs.append((pages[i], pages[j], 1))
                else:
                    # Self-pair with augmentation
                    pairs.append((pages[0], pages[0], 1))

            # Negative pairs: pages from different documents
            other_ids = [d for d in ids if d != doc_id]
            for _ in range(cfg.pairs_per_document * cfg.negative_ratio):
                if other_ids:
                    other_id = rng.choice(other_ids)
                    other_pages = doc_pages[other_id]
                    p_self = pages[rng.integers(len(pages))]
                    p_other = other_pages[rng.integers(len(other_pages))]
                    pairs.append((p_self, p_other, 0))

        rng.shuffle(pairs)
        result[split_name] = pairs

    logger.info(
        "Generated pairs — train: %d, val: %d, test: %d",
        len(result["train"]), len(result["val"]), len(result["test"]),
    )
    return result["train"], result["val"], result["test"]


# ── Loss functions ─────────────────────────────────────────────────────


class ContrastiveLoss(nn.Module):
    """Standard contrastive loss with margin."""

    def __init__(self, margin: float = 1.0):
        super().__init__()
        self.margin = margin

    def forward(
        self, emb1: torch.Tensor, emb2: torch.Tensor, label: torch.Tensor,
    ) -> torch.Tensor:
        dist = F.pairwise_distance(emb1, emb2)
        pos_loss = label * dist.pow(2)
        neg_loss = (1 - label) * F.relu(self.margin - dist).pow(2)
        return (pos_loss + neg_loss).mean()


class InfoNCELoss(nn.Module):
    """InfoNCE contrastive loss."""

    def __init__(self, temperature: float = 0.07):
        super().__init__()
        self.temperature = temperature

    def forward(
        self, emb1: torch.Tensor, emb2: torch.Tensor, label: torch.Tensor,
    ) -> torch.Tensor:
        # Cosine similarity matrix
        sim = F.cosine_similarity(emb1, emb2) / self.temperature
        # For positive pairs, maximize similarity; for negative, minimize
        pos_mask = label.bool()
        if pos_mask.sum() == 0:
            return torch.tensor(0.0, requires_grad=True)
        # Simple pairwise InfoNCE: treat each positive as its own class
        logits = sim
        targets = label.float()
        return F.binary_cross_entropy_with_logits(logits, targets)


# ── Training loop ──────────────────────────────────────────────────────


def encode_graph(model: DocumentGCN, graph: SpatialGraph) -> torch.Tensor:
    """Encode a single graph through the GCN, returning the embedding."""
    features = torch.as_tensor(graph.features, dtype=torch.float32)
    adj = torch.as_tensor(graph.adjacency, dtype=torch.float32)
    return model(features, adj)


def augment_graph(graph: SpatialGraph, noise_std: float, rng: np.random.Generator) -> SpatialGraph:
    """Apply slight noise to features for augmentation."""
    noise = rng.normal(0.0, noise_std, size=graph.features.shape).astype(np.float32)
    return SpatialGraph(
        nodes=graph.nodes,
        adjacency=graph.adjacency,
        features=graph.features + noise,
    )


def train_epoch(
    model: DocumentGCN,
    pairs: list[tuple[SpatialGraph, SpatialGraph, int]],
    criterion: nn.Module,
    optimizer: torch.optim.Optimizer,
    cfg: STGHTrainingConfig,
) -> float:
    """Train one epoch, returns average loss."""
    model.train()
    rng = np.random.default_rng()
    total_loss = 0.0
    n_batches = 0

    for i in range(0, len(pairs), cfg.batch_size):
        batch = pairs[i : i + cfg.batch_size]
        emb1_list, emb2_list, labels = [], [], []
        for g1, g2, label in batch:
            if label == 1 and g1 is g2:
                g2 = augment_graph(g1, cfg.augment_noise_std, rng)
            e1 = encode_graph(model, g1)
            e2 = encode_graph(model, g2)
            emb1_list.append(e1)
            emb2_list.append(e2)
            labels.append(label)

        emb1 = torch.stack(emb1_list)
        emb2 = torch.stack(emb2_list)
        label_t = torch.tensor(labels, dtype=torch.float32)

        loss = criterion(emb1, emb2, label_t)
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), cfg.gradient_clip_norm)
        optimizer.step()

        total_loss += loss.item()
        n_batches += 1

    return total_loss / max(n_batches, 1)


@torch.no_grad()
def evaluate(
    model: DocumentGCN,
    pairs: list[tuple[SpatialGraph, SpatialGraph, int]],
    criterion: nn.Module,
    cfg: STGHTrainingConfig,
) -> dict[str, float]:
    """Evaluate model on a set of pairs."""
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0

    for i in range(0, len(pairs), cfg.batch_size):
        batch = pairs[i : i + cfg.batch_size]
        for g1, g2, label in batch:
            e1 = encode_graph(model, g1)
            e2 = encode_graph(model, g2)
            dist = F.pairwise_distance(e1.unsqueeze(0), e2.unsqueeze(0)).item()
            predicted = 1 if dist < cfg.margin / 2 else 0
            correct += int(predicted == label)
            total += 1
        emb1 = torch.stack([encode_graph(model, g1) for g1, _, _ in batch])
        emb2 = torch.stack([encode_graph(model, g2) for _, g2, _ in batch])
        labels_t = torch.tensor([l for _, _, l in batch], dtype=torch.float32)
        total_loss += criterion(emb1, emb2, labels_t).item()

    n_batches = max(math.ceil(len(pairs) / cfg.batch_size), 1)
    return {
        "loss": total_loss / n_batches,
        "accuracy": correct / max(total, 1),
    }


def train(cfg: STGHTrainingConfig) -> None:
    """Full training pipeline."""
    logger.info("=== STGH GCN Contrastive Training ===")

    # Prepare output directories
    Path(cfg.checkpoint_dir).mkdir(parents=True, exist_ok=True)
    Path(cfg.weights_path).parent.mkdir(parents=True, exist_ok=True)

    # Load data
    documents = load_documents(cfg)
    train_pairs, val_pairs, test_pairs = generate_pairs(documents, cfg)

    if not train_pairs:
        logger.error("No training pairs generated — aborting")
        return

    # Model
    model = DocumentGCN(
        input_dim=cfg.input_dim,
        hidden_dim=cfg.gcn_hidden,
        output_dim=cfg.gcn_output,
    )
    logger.info("Model parameters: %d", sum(p.numel() for p in model.parameters()))

    # Loss
    if cfg.loss == "infonce":
        criterion = InfoNCELoss(temperature=cfg.temperature)
    else:
        criterion = ContrastiveLoss(margin=cfg.margin)

    optimizer = AdamW(model.parameters(), lr=cfg.learning_rate, weight_decay=cfg.weight_decay)
    scheduler = CosineAnnealingLR(optimizer, T_max=cfg.num_epochs - cfg.warmup_epochs)

    # Optional MLflow
    mlflow_run = None
    try:
        import mlflow

        mlflow.set_experiment(cfg.mlflow_experiment)
        mlflow_run = mlflow.start_run()
        mlflow.log_params({
            "input_dim": cfg.input_dim, "gcn_hidden": cfg.gcn_hidden,
            "gcn_output": cfg.gcn_output, "lr": cfg.learning_rate,
            "epochs": cfg.num_epochs, "batch_size": cfg.batch_size,
            "loss": cfg.loss, "temperature": cfg.temperature,
            "margin": cfg.margin, "train_pairs": len(train_pairs),
        })
    except Exception:
        logger.info("MLflow not available — training without experiment tracking")

    best_val_loss = float("inf")
    patience_counter = 0

    for epoch in range(1, cfg.num_epochs + 1):
        train_loss = train_epoch(model, train_pairs, criterion, optimizer, cfg)
        val_metrics = evaluate(model, val_pairs, criterion, cfg) if val_pairs else {"loss": train_loss, "accuracy": 0.0}

        if epoch > cfg.warmup_epochs:
            scheduler.step()

        logger.info(
            "Epoch %3d/%d — train_loss=%.4f  val_loss=%.4f  val_acc=%.4f  lr=%.2e",
            epoch, cfg.num_epochs, train_loss,
            val_metrics["loss"], val_metrics["accuracy"],
            optimizer.param_groups[0]["lr"],
        )

        if mlflow_run:
            import mlflow
            mlflow.log_metrics({
                "train_loss": train_loss,
                "val_loss": val_metrics["loss"],
                "val_accuracy": val_metrics["accuracy"],
            }, step=epoch)

        # Checkpointing & early stopping
        if val_metrics["loss"] < best_val_loss:
            best_val_loss = val_metrics["loss"]
            patience_counter = 0
            torch.save(model.state_dict(), cfg.weights_path)
            logger.info("  → Saved best model (val_loss=%.4f)", best_val_loss)
        else:
            patience_counter += 1
            if patience_counter >= cfg.early_stopping_patience:
                logger.info("Early stopping at epoch %d", epoch)
                break

    # Final evaluation on test set
    if test_pairs:
        model.load_state_dict(torch.load(cfg.weights_path, weights_only=True))
        test_metrics = evaluate(model, test_pairs, criterion, cfg)
        logger.info(
            "Test — loss=%.4f  accuracy=%.4f",
            test_metrics["loss"], test_metrics["accuracy"],
        )
        if mlflow_run:
            import mlflow
            mlflow.log_metrics({
                "test_loss": test_metrics["loss"],
                "test_accuracy": test_metrics["accuracy"],
            })

    if mlflow_run:
        import mlflow
        mlflow.end_run()

    logger.info("Training complete. Weights saved to %s", cfg.weights_path)


# ── Entry point ────────────────────────────────────────────────────────


def main() -> None:
    parser = argparse.ArgumentParser(description="Train STGH GCN with contrastive learning")
    parser.add_argument(
        "--config", default="../configs/stgh_training_config.yaml",
        help="Path to training config YAML",
    )
    args = parser.parse_args()
    cfg = load_config(args.config)
    train(cfg)


if __name__ == "__main__":
    main()
