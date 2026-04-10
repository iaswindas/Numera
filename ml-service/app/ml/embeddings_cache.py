"""Pre-computed embedding cache for model line items.

Caches Sentence-BERT embeddings to disk so they persist across service
restarts. Model template line items rarely change, so this avoids
redundant computation on every startup.
"""

import hashlib
import json
import logging
from pathlib import Path
from typing import Optional

import numpy as np

logger = logging.getLogger("ml-service.ml.embeddings_cache")


class EmbeddingsCache:
    """Disk-backed cache for pre-computed line-item embeddings.

    Cache keys are derived from the MD5 hash of the concatenated labels,
    so any change in the template automatically invalidates the cache.
    """

    def __init__(self, cache_dir: str = "/app/models/embeddings_cache"):
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def _cache_path(self, key: str) -> Path:
        return self.cache_dir / f"{key}.npz"

    @staticmethod
    def compute_key(labels: list[str]) -> str:
        """Compute a stable cache key from a list of labels."""
        content = "|".join(labels)
        return hashlib.md5(content.encode()).hexdigest()

    def get(self, key: str) -> Optional[tuple[np.ndarray, list[int]]]:
        """Retrieve cached embeddings.

        Returns:
            (embeddings, index_map) or None if not cached.
        """
        path = self._cache_path(key)
        if not path.exists():
            return None

        try:
            data = np.load(path, allow_pickle=True)
            embeddings = data["embeddings"]
            index_map = data["index_map"].tolist()
            logger.debug("Cache hit: %s (%d embeddings)", key, len(embeddings))
            return embeddings, index_map
        except Exception:
            logger.warning("Failed to load cache %s — will recompute", key)
            return None

    def put(self, key: str, embeddings: np.ndarray, index_map: list[int]):
        """Store embeddings to disk cache."""
        path = self._cache_path(key)
        try:
            np.savez_compressed(
                path,
                embeddings=embeddings,
                index_map=np.array(index_map),
            )
            logger.debug("Cached %d embeddings to %s", len(embeddings), path)
        except Exception:
            logger.warning("Failed to write cache %s", key)

    def invalidate(self, key: str):
        """Remove a cached entry."""
        path = self._cache_path(key)
        if path.exists():
            path.unlink()
            logger.debug("Invalidated cache: %s", key)

    def clear(self):
        """Remove all cached entries."""
        for f in self.cache_dir.glob("*.npz"):
            f.unlink()
        logger.info("Cleared all embedding caches")
