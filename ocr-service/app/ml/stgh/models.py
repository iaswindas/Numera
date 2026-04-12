"""Data models for Semantic-Topological Graph Hashing."""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np


@dataclass
class OCRNode:
    """A structural node derived from OCR or table extraction."""

    node_id: str
    text: str
    bbox: tuple[float, float, float, float]
    page_idx: int
    row_index: int = 0
    col_index: int = 0
    is_header: bool = False
    cell_type: str = "TEXT"


@dataclass
class OCRPage:
    """A document page prepared for fingerprint generation."""

    index: int
    nodes: list[OCRNode] = field(default_factory=list)
    table_ids: list[str] = field(default_factory=list)


@dataclass
class SpatialGraph:
    """Graph representation of a document page."""

    nodes: list[OCRNode]
    adjacency: np.ndarray
    features: np.ndarray


@dataclass
class DocumentFingerprint:
    """Fingerprint output for a document page."""

    hash: str
    embedding: np.ndarray
    page_idx: int
    node_count: int
    created_at: str
    table_ids: list[str] = field(default_factory=list)


@dataclass
class STGHConfig:
    """Runtime configuration for STGH fingerprint generation."""

    hash_bits: int = 256
    k_neighbors: int = 6
    gcn_hidden: int = 128
    gcn_output: int = 256
    semantic_dim: int = 128
    sbert_model: str = "BAAI/bge-small-en-v1.5"
    similarity_threshold: float = 0.85
    use_semantic_model: bool = False