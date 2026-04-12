"""Pure PyTorch document GCN used by STGH."""

from __future__ import annotations

import numpy as np

try:
    import torch
    from torch import nn
    from torch.nn import functional as F
except Exception:  # pragma: no cover - optional dependency path
    torch = None
    nn = None
    F = None


if nn is not None:

    class GraphConvolution(nn.Module):
        """Standard GCN layer using dense or sparse adjacency."""

        def __init__(self, in_features: int, out_features: int):
            super().__init__()
            self.weight = nn.Parameter(torch.empty(in_features, out_features))
            self.bias = nn.Parameter(torch.empty(out_features))
            nn.init.xavier_uniform_(self.weight)
            nn.init.zeros_(self.bias)

        def forward(self, text, adj):
            support = torch.mm(text, self.weight)
            if adj.is_sparse:
                output = torch.sparse.mm(adj, support)
            else:
                output = torch.mm(adj, support)
            return output + self.bias


    class DocumentGCN(nn.Module):
        """Three-layer graph encoder for document structure."""

        def __init__(self, input_dim: int, hidden_dim: int = 128, output_dim: int = 256):
            super().__init__()
            torch.manual_seed(42)
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
            self.eval()

        def forward(self, graph):
            if graph.features.size == 0:
                return torch.zeros(self.readout[-1].out_features, dtype=torch.float32)

            features = torch.as_tensor(graph.features, dtype=torch.float32)
            adjacency = torch.as_tensor(graph.adjacency, dtype=torch.float32)
            adjacency = self._normalize(adjacency)

            x = F.relu(self.bn1(self.gc1(features, adjacency)))
            x = F.relu(self.bn2(self.gc2(x, adjacency)))
            x = F.relu(self.gc3(x, adjacency))
            pooled = x.mean(dim=0)
            return F.normalize(self.readout(pooled), p=2, dim=0)

        @staticmethod
        def _normalize(adjacency: torch.Tensor) -> torch.Tensor:
            identity = torch.eye(adjacency.size(0), dtype=adjacency.dtype)
            adjacency_hat = adjacency + identity
            degrees = adjacency_hat.sum(dim=1)
            degrees = torch.where(degrees == 0, torch.ones_like(degrees), degrees)
            inv_sqrt = torch.pow(degrees, -0.5)
            diagonal = torch.diag(inv_sqrt)
            return diagonal @ adjacency_hat @ diagonal

else:

    class GraphConvolution:
        """Numpy fallback for environments without torch."""

        def __init__(self, in_features: int, out_features: int):
            rng = np.random.default_rng(in_features * 1000 + out_features)
            self.weight = rng.standard_normal((in_features, out_features), dtype=np.float32)
            self.bias = np.zeros(out_features, dtype=np.float32)

        def forward(self, text, adj):
            support = text @ self.weight
            return adj @ support + self.bias


    class DocumentGCN:
        """Numpy fallback encoder for environments without torch."""

        def __init__(self, input_dim: int, hidden_dim: int = 128, output_dim: int = 256):
            self.gc1 = GraphConvolution(input_dim, hidden_dim)
            self.gc2 = GraphConvolution(hidden_dim, 64)
            self.gc3 = GraphConvolution(64, 32)
            rng = np.random.default_rng(42)
            self.readout_1 = rng.standard_normal((32, output_dim), dtype=np.float32)
            self.readout_2 = rng.standard_normal((output_dim, output_dim), dtype=np.float32)
            self.output_dim = output_dim

        def forward(self, graph):
            if graph.features.size == 0:
                return np.zeros(self.output_dim, dtype=np.float32)

            features = np.asarray(graph.features, dtype=np.float32)
            adjacency = self._normalize(np.asarray(graph.adjacency, dtype=np.float32))

            x = self._relu(self.gc1.forward(features, adjacency))
            x = self._relu(self.gc2.forward(x, adjacency))
            x = self._relu(self.gc3.forward(x, adjacency))
            pooled = x.mean(axis=0)
            hidden = self._relu(pooled @ self.readout_1)
            return self._normalize_vector(hidden @ self.readout_2)

        @staticmethod
        def _normalize(adjacency: np.ndarray) -> np.ndarray:
            identity = np.eye(adjacency.shape[0], dtype=np.float32)
            adjacency_hat = adjacency + identity
            degrees = adjacency_hat.sum(axis=1)
            degrees = np.where(degrees == 0, 1.0, degrees)
            inv_sqrt = np.power(degrees, -0.5, dtype=np.float32)
            diagonal = np.diag(inv_sqrt)
            return diagonal @ adjacency_hat @ diagonal

        @staticmethod
        def _relu(values: np.ndarray) -> np.ndarray:
            return np.maximum(values, 0)

        @staticmethod
        def _normalize_vector(values: np.ndarray) -> np.ndarray:
            norm = np.linalg.norm(values)
            if norm > 0:
                values = values / norm
            return values.astype(np.float32)