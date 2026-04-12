"""Document structural fingerprint generation."""

from __future__ import annotations

import hashlib
import logging
import math
import re
from collections import defaultdict
from datetime import datetime, timezone

import numpy as np

from .gcn import DocumentGCN
from .models import DocumentFingerprint, OCRNode, OCRPage, STGHConfig, SpatialGraph

logger = logging.getLogger("ocr-service.ml.stgh.fingerprinter")


class LocalitySensitiveHasher:
    """Random projection based binary hasher."""

    def __init__(self, input_dim: int, hash_bits: int):
        rng = np.random.default_rng(42)
        self.projections = rng.standard_normal((hash_bits, input_dim), dtype=np.float32)
        self.hash_bits = hash_bits

    def hash(self, embedding: np.ndarray) -> str:
        bits = embedding @ self.projections.T >= 0
        bit_string = "".join("1" if bit else "0" for bit in bits.tolist())
        width = math.ceil(self.hash_bits / 4)
        return f"{int(bit_string, 2):0{width}x}"


class STGHFingerprinter:
    """Semantic-topological graph hasher for OCR-derived pages."""

    def __init__(self, config: STGHConfig):
        self.config = config
        input_dim = config.semantic_dim + 4 + 3
        self.gcn = DocumentGCN(
            input_dim=input_dim,
            hidden_dim=config.gcn_hidden,
            output_dim=config.gcn_output,
        )
        self.hasher = LocalitySensitiveHasher(config.gcn_output, config.hash_bits)
        self.sbert_model = None

    def fingerprint(self, page: OCRPage) -> DocumentFingerprint:
        graph = self._build_spatial_graph(page)
        embedding = self.gcn.forward(graph)
        if hasattr(embedding, "detach"):
            embedding = embedding.detach().cpu().numpy().astype(np.float32)
        else:
            embedding = np.asarray(embedding, dtype=np.float32)
        hash_value = self.hasher.hash(embedding)
        return DocumentFingerprint(
            hash=hash_value,
            embedding=embedding,
            page_idx=page.index,
            node_count=len(graph.nodes),
            created_at=datetime.now(timezone.utc).isoformat(),
            table_ids=page.table_ids,
        )

    def fingerprint_document(self, tables: list) -> list[DocumentFingerprint]:
        pages = self.pages_from_tables(tables)
        return [self.fingerprint(page) for page in pages]

    def pages_from_tables(self, tables: list) -> list[OCRPage]:
        grouped: dict[int, list] = defaultdict(list)
        for table in tables:
            grouped[getattr(table, "page_number")].append(table)

        pages: list[OCRPage] = []
        for page_number in sorted(grouped):
            nodes: list[OCRNode] = []
            table_ids: list[str] = []
            for table in grouped[page_number]:
                table_ids.append(getattr(table, "table_id"))
                for cell in getattr(table, "cells"):
                    bbox = (
                        float(cell.bbox.x),
                        float(cell.bbox.y),
                        float(cell.bbox.width),
                        float(cell.bbox.height),
                    )
                    nodes.append(
                        OCRNode(
                            node_id=f"{table.table_id}:{cell.row_index}:{cell.col_index}",
                            text=cell.text,
                            bbox=bbox,
                            page_idx=page_number,
                            row_index=cell.row_index,
                            col_index=cell.col_index,
                            is_header=cell.is_header,
                            cell_type=cell.cell_type,
                        )
                    )
            pages.append(OCRPage(index=page_number, nodes=nodes, table_ids=table_ids))

        return pages

    def similarity(self, fp1: DocumentFingerprint, fp2: DocumentFingerprint) -> float:
        if fp1.embedding.size == 0 or fp2.embedding.size == 0:
            return 0.0

        hash_similarity = 1.0 - self._hamming_distance(fp1.hash, fp2.hash) / max(len(fp1.hash) * 4, 1)
        cosine = float(np.dot(fp1.embedding, fp2.embedding) / max(np.linalg.norm(fp1.embedding) * np.linalg.norm(fp2.embedding), 1e-8))
        return max(0.0, min(1.0, 0.35 * hash_similarity + 0.65 * cosine))

    def _build_spatial_graph(self, page: OCRPage) -> SpatialGraph:
        if not page.nodes:
            empty_adj = np.zeros((0, 0), dtype=np.float32)
            empty_features = np.zeros((0, self.config.semantic_dim + 7), dtype=np.float32)
            return SpatialGraph(nodes=[], adjacency=empty_adj, features=empty_features)

        semantic = self._encode_texts(page.nodes)
        spatial = np.asarray([self._spatial_features(node) for node in page.nodes], dtype=np.float32)
        node_types = np.asarray([self._type_features(node) for node in page.nodes], dtype=np.float32)
        features = np.concatenate([semantic, spatial, node_types], axis=1)
        adjacency = self._build_adjacency(page.nodes)
        return SpatialGraph(nodes=page.nodes, adjacency=adjacency, features=features)

    def _build_adjacency(self, nodes: list[OCRNode]) -> np.ndarray:
        count = len(nodes)
        adjacency = np.zeros((count, count), dtype=np.float32)
        centers = np.asarray([self._bbox_center(node.bbox) for node in nodes], dtype=np.float32)

        for index, node in enumerate(nodes):
            distances = []
            for other_index, other in enumerate(nodes):
                if index == other_index:
                    continue
                distance = float(np.linalg.norm(centers[index] - centers[other_index]))
                row_bonus = 0.1 if node.row_index == other.row_index else 0.0
                col_bonus = 0.1 if node.col_index == other.col_index else 0.0
                distances.append((distance - row_bonus - col_bonus, other_index))

            distances.sort(key=lambda item: item[0])
            for _, other_index in distances[: self.config.k_neighbors]:
                adjacency[index, other_index] = 1.0
                adjacency[other_index, index] = 1.0

            for other_index, other in enumerate(nodes):
                if index == other_index:
                    continue
                if node.row_index == other.row_index or node.col_index == other.col_index:
                    adjacency[index, other_index] = 1.0
                    adjacency[other_index, index] = 1.0

        return adjacency

    def _encode_texts(self, nodes: list[OCRNode]) -> np.ndarray:
        return np.asarray([self._hash_text(self._normalize_text(node)) for node in nodes], dtype=np.float32)

    def _normalize_text(self, node: OCRNode) -> str:
        if node.cell_type == "NUMERIC":
            return "__numeric__"
        text = re.sub(r"\(?[Nn]ote\s*\d+\)?", "", node.text or "")
        text = re.sub(r"\d+[.,]\d+", "", text)
        text = re.sub(r"\s+", " ", text).strip().lower()
        return text or "__empty__"

    def _hash_text(self, text: str) -> np.ndarray:
        vector = np.zeros(self.config.semantic_dim, dtype=np.float32)
        tokens = re.findall(r"[a-z_]+", text)
        if not tokens:
            tokens = [text]

        for token in tokens:
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % self.config.semantic_dim
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            vector[index] += sign

        norm = np.linalg.norm(vector)
        if norm > 0:
            vector /= norm
        return vector

    @staticmethod
    def _spatial_features(node: OCRNode) -> tuple[float, float, float, float]:
        x, y, width, height = node.bbox
        center_x = x + width / 2
        center_y = y + height / 2
        return center_x, center_y, width, height

    @staticmethod
    def _type_features(node: OCRNode) -> tuple[float, float, float]:
        is_numeric = 1.0 if node.cell_type == "NUMERIC" else 0.0
        is_label = 1.0 if node.cell_type in {"TEXT", "MIXED"} else 0.0
        return 1.0 if node.is_header else 0.0, is_numeric, is_label

    @staticmethod
    def _bbox_center(bbox: tuple[float, float, float, float]) -> tuple[float, float]:
        x, y, width, height = bbox
        return x + width / 2, y + height / 2

    @staticmethod
    def _hamming_distance(hash_one: str, hash_two: str) -> int:
        bits_one = bin(int(hash_one, 16))[2:].zfill(len(hash_one) * 4)
        bits_two = bin(int(hash_two, 16))[2:].zfill(len(hash_two) * 4)
        return sum(bit_one != bit_two for bit_one, bit_two in zip(bits_one, bits_two, strict=False))