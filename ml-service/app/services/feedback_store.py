"""Persistent feedback storage — PostgreSQL with in-memory fallback.

Stores analyst corrections for ML model retraining. Uses PostgreSQL when
available, falls back to in-memory list for dev/demo.

Architecture note: In Phase 2+, this will be replaced by routing feedback
through the Kotlin backend API. The interface is kept simple so the
transition is a drop-in replacement.
"""

import logging
from datetime import datetime, timezone
from typing import Optional

logger = logging.getLogger("ml-service.services.feedback_store")


class FeedbackStore:
    """Unified feedback storage with PostgreSQL + in-memory fallback."""

    def __init__(self):
        self._memory_store: list[dict] = []
        self._pg_available = False

    async def init(self, pool):
        """Initialise with an asyncpg pool (may be None)."""
        self._pool = pool
        self._pg_available = pool is not None
        if self._pg_available:
            logger.info("FeedbackStore using PostgreSQL")
        else:
            logger.info("FeedbackStore using in-memory fallback")

    async def save_corrections(self, corrections: list[dict]) -> int:
        """Save a batch of analyst corrections.

        Args:
            corrections: List of correction dicts with keys matching ml_feedback columns.

        Returns:
            Number of corrections saved.
        """
        if self._pg_available:
            return await self._save_to_pg(corrections)
        return self._save_to_memory(corrections)

    async def _save_to_pg(self, corrections: list[dict]) -> int:
        """Insert corrections into PostgreSQL."""
        try:
            async with self._pool.acquire() as conn:
                for c in corrections:
                    await conn.execute(
                        """
                        INSERT INTO ml_feedback (
                            document_id, tenant_id, analyst_id,
                            source_text, source_zone_type,
                            suggested_item_id, suggested_item_label, suggested_confidence,
                            corrected_item_id, corrected_item_label,
                            correction_type, model_version, created_at
                        ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
                        """,
                        c.get("document_id", ""),
                        c.get("tenant_id"),
                        c.get("analyst_id"),
                        c.get("source_text", ""),
                        c.get("source_zone_type"),
                        c.get("suggested_item_id", ""),
                        c.get("suggested_item_label"),
                        c.get("suggested_confidence"),
                        c.get("corrected_item_id", ""),
                        c.get("corrected_item_label"),
                        c.get("correction_type", "REMAPPED"),
                        c.get("model_version"),
                        datetime.now(timezone.utc),
                    )
            return len(corrections)
        except Exception:
            logger.exception("PostgreSQL save failed — falling back to memory")
            return self._save_to_memory(corrections)

    def _save_to_memory(self, corrections: list[dict]) -> int:
        """Fallback: save to in-memory list."""
        for c in corrections:
            c["created_at"] = datetime.now(timezone.utc).isoformat()
            self._memory_store.append(c)
        return len(corrections)

    async def export_since(
        self,
        since: Optional[datetime] = None,
        tenant_id: Optional[str] = None,
        limit: int = 10000,
    ) -> list[dict]:
        """Export feedback records for Colab retraining.

        Args:
            since: Only records after this datetime.
            tenant_id: Filter to specific tenant.
            limit: Max records to return.

        Returns:
            List of feedback record dicts.
        """
        if self._pg_available:
            return await self._export_from_pg(since, tenant_id, limit)
        return self._export_from_memory(since, tenant_id, limit)

    async def _export_from_pg(
        self, since: Optional[datetime], tenant_id: Optional[str], limit: int
    ) -> list[dict]:
        """Export from PostgreSQL."""
        try:
            async with self._pool.acquire() as conn:
                query = "SELECT * FROM ml_feedback WHERE 1=1"
                args = []
                idx = 1

                if since:
                    query += f" AND created_at >= ${idx}"
                    args.append(since)
                    idx += 1

                if tenant_id:
                    query += f" AND tenant_id = ${idx}"
                    args.append(tenant_id)
                    idx += 1

                query += f" ORDER BY created_at DESC LIMIT ${idx}"
                args.append(limit)

                rows = await conn.fetch(query, *args)
                return [dict(row) for row in rows]
        except Exception:
            logger.exception("PostgreSQL export failed")
            return []

    def _export_from_memory(
        self, since: Optional[datetime], tenant_id: Optional[str], limit: int
    ) -> list[dict]:
        """Export from in-memory store."""
        results = self._memory_store.copy()

        if since:
            since_str = since.isoformat()
            results = [
                r for r in results if r.get("created_at", "") >= since_str
            ]

        if tenant_id:
            results = [r for r in results if r.get("tenant_id") == tenant_id]

        return results[:limit]

    async def get_stats(self, tenant_id: Optional[str] = None) -> dict:
        """Get feedback statistics."""
        if self._pg_available:
            return await self._stats_from_pg(tenant_id)
        return self._stats_from_memory(tenant_id)

    async def _stats_from_pg(self, tenant_id: Optional[str]) -> dict:
        """Stats from PostgreSQL."""
        try:
            async with self._pool.acquire() as conn:
                where = "WHERE tenant_id = $1" if tenant_id else ""
                args = [tenant_id] if tenant_id else []

                total = await conn.fetchval(
                    f"SELECT COUNT(*) FROM ml_feedback {where}", *args
                )
                docs = await conn.fetchval(
                    f"SELECT COUNT(DISTINCT document_id) FROM ml_feedback {where}", *args
                )
                tenants = await conn.fetchval(
                    "SELECT COUNT(DISTINCT tenant_id) FROM ml_feedback"
                )

                type_rows = await conn.fetch(
                    f"SELECT correction_type, COUNT(*) as cnt FROM ml_feedback {where} GROUP BY correction_type",
                    *args,
                )
                by_type = {row["correction_type"]: row["cnt"] for row in type_rows}

                return {
                    "total_corrections": total,
                    "unique_documents": docs,
                    "unique_tenants": tenants,
                    "by_correction_type": by_type,
                    "storage": "postgresql",
                }
        except Exception:
            logger.exception("PostgreSQL stats failed")
            return {"total_corrections": 0, "storage": "postgresql", "error": "query failed"}

    def _stats_from_memory(self, tenant_id: Optional[str]) -> dict:
        """Stats from in-memory store."""
        records = self._memory_store
        if tenant_id:
            records = [r for r in records if r.get("tenant_id") == tenant_id]

        from collections import Counter

        by_type = Counter(r.get("correction_type", "REMAPPED") for r in records)
        return {
            "total_corrections": len(records),
            "unique_documents": len({r.get("document_id") for r in records}),
            "unique_tenants": len({r.get("tenant_id") for r in records if r.get("tenant_id")}),
            "by_correction_type": dict(by_type),
            "storage": "memory",
        }

    async def count_by_tenant(self, tenant_id: str) -> int:
        """Count corrections for a specific tenant (for client model threshold)."""
        if self._pg_available:
            try:
                async with self._pool.acquire() as conn:
                    return await conn.fetchval(
                        "SELECT COUNT(*) FROM ml_feedback WHERE tenant_id = $1",
                        tenant_id,
                    )
            except Exception:
                return 0
        return sum(
            1 for r in self._memory_store if r.get("tenant_id") == tenant_id
        )
