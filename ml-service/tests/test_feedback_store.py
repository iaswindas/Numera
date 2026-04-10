"""Tests for PostgreSQL feedback store with in-memory fallback."""

import pytest
from app.services.feedback_store import FeedbackStore


@pytest.fixture
def store():
    """Create a feedback store with in-memory fallback (no PG)."""
    s = FeedbackStore()
    import asyncio
    asyncio.get_event_loop().run_until_complete(s.init(None))
    return s


class TestFeedbackStoreMemory:
    def test_save_corrections(self, store):
        import asyncio
        loop = asyncio.get_event_loop()

        corrections = [
            {
                "document_id": "doc-1",
                "tenant_id": "tenant-1",
                "analyst_id": "analyst-1",
                "source_text": "Turnover",
                "source_zone_type": "INCOME_STATEMENT",
                "suggested_item_id": "item-1",
                "corrected_item_id": "item-2",
                "correction_type": "REMAPPED",
            }
        ]
        count = loop.run_until_complete(store.save_corrections(corrections))
        assert count == 1

    def test_export_since(self, store):
        import asyncio
        loop = asyncio.get_event_loop()

        # Save first
        loop.run_until_complete(store.save_corrections([
            {"document_id": "doc-1", "source_text": "Revenue", "suggested_item_id": "i1", "corrected_item_id": "i2"},
            {"document_id": "doc-2", "source_text": "Sales", "suggested_item_id": "i3", "corrected_item_id": "i4", "tenant_id": "t1"},
        ]))

        # Export all
        records = loop.run_until_complete(store.export_since())
        assert len(records) == 2

        # Export by tenant
        records = loop.run_until_complete(store.export_since(tenant_id="t1"))
        assert len(records) == 1

    def test_get_stats(self, store):
        import asyncio
        loop = asyncio.get_event_loop()

        loop.run_until_complete(store.save_corrections([
            {"document_id": "doc-1", "source_text": "Revenue", "suggested_item_id": "i1", "corrected_item_id": "i2", "correction_type": "REMAPPED"},
        ]))

        stats = loop.run_until_complete(store.get_stats())
        assert stats["total_corrections"] == 1
        assert stats["storage"] == "memory"

    def test_count_by_tenant(self, store):
        import asyncio
        loop = asyncio.get_event_loop()

        loop.run_until_complete(store.save_corrections([
            {"document_id": "d1", "source_text": "A", "suggested_item_id": "i1", "corrected_item_id": "i2", "tenant_id": "t1"},
            {"document_id": "d2", "source_text": "B", "suggested_item_id": "i3", "corrected_item_id": "i4", "tenant_id": "t1"},
            {"document_id": "d3", "source_text": "C", "suggested_item_id": "i5", "corrected_item_id": "i6", "tenant_id": "t2"},
        ]))

        t1_count = loop.run_until_complete(store.count_by_tenant("t1"))
        assert t1_count == 2

        t2_count = loop.run_until_complete(store.count_by_tenant("t2"))
        assert t2_count == 1
