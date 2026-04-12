"""Candidate pruning for NG-MILP.

The production design expects a small graph model to rank likely edges.
This implementation keeps the interface stable and works without weights by
returning a uniform prior while still ordering edges deterministically.
"""

from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from .models import CandidateEdge, CandidateGraph, NGMILPConfig

logger = logging.getLogger("ml-service.ml.ng_milp.gnn_pruner")

try:
    import torch
    from torch import nn
except Exception:  # pragma: no cover - optional dependency path
    torch = None
    nn = None


if nn is not None:

    class _EdgeScorer(nn.Module):
        """Lightweight edge scorer used when trained weights are available."""

        def __init__(self, feature_dim: int):
            super().__init__()
            self.network = nn.Sequential(
                nn.Linear(feature_dim, 32),
                nn.ReLU(),
                nn.Dropout(0.2),
                nn.Linear(32, 16),
                nn.ReLU(),
                nn.Linear(16, 1),
                nn.Sigmoid(),
            )

        def forward(self, features):
            return self.network(features).squeeze(-1)

else:

    class _EdgeScorer:
        """Placeholder scorer used when torch is unavailable."""

        def __init__(self, feature_dim: int):
            self.feature_dim = feature_dim


class GNNPruner:
    """Ranks candidate edges before the exact optimization step."""

    def __init__(self, config: NGMILPConfig):
        self.config = config
        self.model = None
        self.trained = False

        if torch is not None and nn is not None:
            self.model = _EdgeScorer(feature_dim=7)
            self.model.eval()
            self._try_load_weights(config.gnn_weights_path)

    def prune(self, graph: CandidateGraph) -> tuple[CandidateGraph, dict[tuple[int, int], float]]:
        """Keep the top-k addends per candidate sum row."""
        if not graph.edges:
            return graph, {}

        scores = self.score_edges(graph)
        selected: list[CandidateEdge] = []

        for sum_index in sorted({edge.sum_index for edge in graph.edges}):
            edges = graph.edges_for_sum(sum_index)
            edges.sort(
                key=lambda edge: (
                    scores.get((edge.sum_index, edge.addend_index), 0.0),
                    self._heuristic_rank(edge),
                ),
                reverse=True,
            )
            selected.extend(edges[: self.config.max_candidates_per_cell])

        return CandidateGraph(cells=graph.cells, edges=selected), scores

    def score_edges(self, graph: CandidateGraph) -> dict[tuple[int, int], float]:
        """Return a probability score for each edge."""
        if not graph.edges:
            return {}

        if self.trained and self.model is not None and torch is not None:
            feature_matrix = np.asarray(
                [self._edge_features(graph, edge) for edge in graph.edges],
                dtype=np.float32,
            )
            tensor = torch.from_numpy(feature_matrix)
            with torch.no_grad():
                probabilities = self.model(tensor).cpu().numpy().tolist()
            return {
                (edge.sum_index, edge.addend_index): float(score)
                for edge, score in zip(graph.edges, probabilities, strict=False)
            }

        return {
            (edge.sum_index, edge.addend_index): 0.5
            for edge in graph.edges
        }

    def _try_load_weights(self, weights_path: str):
        if not weights_path or self.model is None or torch is None:
            return

        candidate = Path(weights_path)
        if not candidate.exists():
            logger.warning("NG-MILP GNN weights not found: %s", weights_path)
            return

        try:
            state = torch.load(candidate, map_location="cpu")
            self.model.load_state_dict(state)
            self.model.eval()
            self.trained = True
            logger.info("Loaded NG-MILP GNN weights from %s", weights_path)
        except Exception:
            logger.exception("Failed to load NG-MILP GNN weights from %s", weights_path)

    def _edge_features(self, graph: CandidateGraph, edge: CandidateEdge) -> list[float]:
        sum_cell = graph.cells[edge.sum_index]
        addend = graph.cells[edge.addend_index]
        return [
            float(abs(sum_cell.value)),
            float(abs(addend.value)),
            float(edge.spatial_distance),
            float(edge.hierarchy_gap),
            float(edge.value_ratio),
            1.0 if sum_cell.is_total else 0.0,
            1.0 if addend.indent_level > sum_cell.indent_level else 0.0,
        ]

    @staticmethod
    def _heuristic_rank(edge: CandidateEdge) -> float:
        return (
            max(0.0, 10.0 - edge.spatial_distance)
            + (2.0 if edge.hierarchy_gap >= 0 else 0.0)
            + max(0.0, 1.0 - edge.value_ratio)
        )