"""ChromaDB-backed vector store for copilot RAG retrieval.

Collections:
  - spreads    – indexed spread values with metadata
  - documents  – OCR text paragraphs / chunks
  - covenants  – covenant definitions, thresholds, conditions
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

import chromadb
from chromadb.config import Settings as ChromaSettings
from sentence_transformers import SentenceTransformer

from app.config import MlSettings

logger = logging.getLogger(__name__)

COLLECTION_NAMES = ("spreads", "documents", "covenants")


@dataclass
class SearchResult:
    """A single vector-store hit."""

    id: str
    text: str
    score: float
    metadata: dict[str, Any] = field(default_factory=dict)


class VectorStore:
    """Thin wrapper around ChromaDB with SBERT embeddings."""

    def __init__(self, settings: MlSettings | None = None) -> None:
        self._settings = settings or MlSettings()
        self._client: chromadb.ClientAPI | None = None
        self._model: SentenceTransformer | None = None
        self._collections: dict[str, chromadb.Collection] = {}

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    def initialise(self) -> None:
        """Create the Chroma client and ensure collections exist."""
        persist_dir = f"{self._settings.model_cache_dir}/chroma_copilot"
        self._client = chromadb.Client(
            ChromaSettings(
                anonymized_telemetry=False,
                is_persistent=True,
                persist_directory=persist_dir,
            )
        )
        for name in COLLECTION_NAMES:
            self._collections[name] = self._client.get_or_create_collection(
                name=name,
                metadata={"hnsw:space": "cosine"},
            )
        self._model = SentenceTransformer(self._settings.sbert_hf_fallback)
        logger.info("VectorStore initialised – collections: %s", list(self._collections))

    def _ensure_ready(self) -> None:
        if self._client is None or self._model is None:
            self.initialise()

    # ------------------------------------------------------------------
    # Embedding helper
    # ------------------------------------------------------------------

    def _embed(self, texts: list[str]) -> list[list[float]]:
        self._ensure_ready()
        assert self._model is not None
        vectors = self._model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
        return vectors.tolist()

    # ------------------------------------------------------------------
    # Indexing
    # ------------------------------------------------------------------

    def index_spread(
        self,
        spread_id: str,
        customer_id: str,
        items: list[dict[str, Any]],
    ) -> int:
        """Index spread values into the *spreads* collection."""
        self._ensure_ready()
        col = self._collections["spreads"]
        ids: list[str] = []
        docs: list[str] = []
        metas: list[dict[str, Any]] = []

        for item in items:
            doc_id = f"spread-{spread_id}-{item.get('itemCode', '')}"
            text = f"{item.get('label', '')} = {item.get('mappedValue', '')} ({item.get('section', '')})"
            ids.append(doc_id)
            docs.append(text)
            metas.append({
                "spread_id": spread_id,
                "customer_id": customer_id,
                "item_code": str(item.get("itemCode", "")),
                "section": str(item.get("section", "")),
            })

        embeddings = self._embed(docs)
        col.upsert(ids=ids, embeddings=embeddings, documents=docs, metadatas=metas)
        logger.info("Indexed %d spread items for spread=%s", len(ids), spread_id)
        return len(ids)

    def index_document(
        self,
        document_id: str,
        customer_id: str,
        chunks: list[dict[str, Any]],
    ) -> int:
        """Index document OCR text chunks into the *documents* collection."""
        self._ensure_ready()
        col = self._collections["documents"]
        ids: list[str] = []
        docs: list[str] = []
        metas: list[dict[str, Any]] = []

        for idx, chunk in enumerate(chunks):
            doc_id = f"doc-{document_id}-{idx}"
            text = str(chunk.get("text", ""))
            ids.append(doc_id)
            docs.append(text)
            metas.append({
                "document_id": document_id,
                "customer_id": customer_id,
                "page": int(chunk.get("page", 0)),
                "chunk_index": idx,
            })

        embeddings = self._embed(docs)
        col.upsert(ids=ids, embeddings=embeddings, documents=docs, metadatas=metas)
        logger.info("Indexed %d document chunks for doc=%s", len(ids), document_id)
        return len(ids)

    def index_covenants(
        self,
        covenant_id: str,
        customer_id: str,
        definition: str,
        metadata: dict[str, Any] | None = None,
    ) -> int:
        """Index a single covenant definition."""
        self._ensure_ready()
        col = self._collections["covenants"]
        doc_id = f"cov-{covenant_id}"
        embedding = self._embed([definition])
        meta = {
            "covenant_id": covenant_id,
            "customer_id": customer_id,
            **(metadata or {}),
        }
        col.upsert(ids=[doc_id], embeddings=embedding, documents=[definition], metadatas=[meta])
        logger.info("Indexed covenant %s", covenant_id)
        return 1

    # ------------------------------------------------------------------
    # Search
    # ------------------------------------------------------------------

    def search(
        self,
        query: str,
        collections: list[str] | None = None,
        top_k: int = 5,
        where: dict[str, Any] | None = None,
    ) -> list[SearchResult]:
        """Search across one or more collections and return ranked results."""
        self._ensure_ready()
        target_cols = collections or list(COLLECTION_NAMES)
        query_embedding = self._embed([query])[0]
        results: list[SearchResult] = []

        for col_name in target_cols:
            col = self._collections.get(col_name)
            if col is None:
                continue
            try:
                raw = col.query(
                    query_embeddings=[query_embedding],
                    n_results=top_k,
                    where=where,
                    include=["documents", "metadatas", "distances"],
                )
            except Exception:
                logger.warning("Query failed on collection %s", col_name, exc_info=True)
                continue

            ids_list = raw.get("ids", [[]])[0]
            docs_list = raw.get("documents", [[]])[0]
            metas_list = raw.get("metadatas", [[]])[0]
            dists_list = raw.get("distances", [[]])[0]

            for rid, rdoc, rmeta, rdist in zip(ids_list, docs_list, metas_list, dists_list):
                score = max(0.0, 1.0 - rdist)  # cosine distance → similarity
                results.append(SearchResult(
                    id=rid,
                    text=rdoc or "",
                    score=score,
                    metadata={"collection": col_name, **(rmeta or {})},
                ))

        results.sort(key=lambda r: r.score, reverse=True)
        return results[:top_k]

    # ------------------------------------------------------------------
    # Stats
    # ------------------------------------------------------------------

    def stats(self) -> dict[str, int]:
        """Return document counts per collection."""
        self._ensure_ready()
        return {name: col.count() for name, col in self._collections.items()}
