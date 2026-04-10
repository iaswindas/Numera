"""PostgreSQL connection pool management.

Provides async connection pooling via asyncpg. Creates the ml_feedback
table on startup if it doesn't exist. Falls back gracefully when
PostgreSQL is unavailable (dev mode keeps feedback in-memory only).

Architecture note: This is a direct DB connection for Phase 1. When the
Kotlin backend is ready, feedback persistence will be routed through it
via HTTP callback, and this module will be replaced by an API client.
"""

import logging
from typing import Optional

logger = logging.getLogger("ml-service.services.database")

# Async pool instance (module-level singleton)
_pool = None


async def init_pool(dsn: str, min_size: int = 2, max_size: int = 10):
    """Initialise the asyncpg connection pool.

    Args:
        dsn: PostgreSQL connection string.
        min_size: Minimum pool connections.
        max_size: Maximum pool connections.
    """
    global _pool
    try:
        import asyncpg

        _pool = await asyncpg.create_pool(
            dsn=dsn,
            min_size=min_size,
            max_size=max_size,
            command_timeout=30,
        )
        logger.info("PostgreSQL pool created: %s (pool=%d-%d)", dsn.split("@")[-1], min_size, max_size)
        await _ensure_tables()
        return True
    except Exception as exc:
        logger.warning(
            "PostgreSQL not available — feedback will be in-memory only: %s", exc
        )
        _pool = None
        return False


async def close_pool():
    """Close the connection pool."""
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None
        logger.info("PostgreSQL pool closed")


def get_pool():
    """Return the current pool (may be None if PG is unavailable)."""
    return _pool


async def _ensure_tables():
    """Create tables if they don't exist (idempotent)."""
    if _pool is None:
        return

    async with _pool.acquire() as conn:
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS ml_feedback (
                id              BIGSERIAL       PRIMARY KEY,
                document_id     TEXT            NOT NULL,
                tenant_id       TEXT,
                analyst_id      TEXT,
                source_text     TEXT            NOT NULL,
                source_zone_type TEXT,
                suggested_item_id TEXT          NOT NULL,
                suggested_item_label TEXT,
                suggested_confidence REAL,
                corrected_item_id TEXT          NOT NULL,
                corrected_item_label TEXT,
                correction_type TEXT            NOT NULL DEFAULT 'REMAPPED',
                model_version   TEXT,
                created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
            );

            CREATE INDEX IF NOT EXISTS idx_feedback_tenant
                ON ml_feedback (tenant_id);
            CREATE INDEX IF NOT EXISTS idx_feedback_created
                ON ml_feedback (created_at);
            CREATE INDEX IF NOT EXISTS idx_feedback_document
                ON ml_feedback (document_id);
        """)
        logger.info("ml_feedback table ensured")


async def health_check() -> dict:
    """Check PostgreSQL connectivity."""
    if _pool is None:
        return {"status": "unavailable", "reason": "pool not initialised"}
    try:
        async with _pool.acquire() as conn:
            row = await conn.fetchrow("SELECT 1 AS ok")
            return {"status": "healthy", "pool_size": _pool.get_size()}
    except Exception as exc:
        return {"status": "unhealthy", "error": str(exc)}
