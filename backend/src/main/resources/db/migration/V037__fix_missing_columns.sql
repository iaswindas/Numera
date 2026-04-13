-- V037: Add missing columns required by Hibernate entity validation
-- Fixes BaseEntity.updatedAt and other missing columns

-- #1 covenant_contacts: missing updated_at (BaseEntity)
ALTER TABLE covenant_contacts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- #2 covenant_documents: missing created_at and updated_at (BaseEntity)
ALTER TABLE covenant_documents
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE covenant_documents
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- #3 group_members: missing updated_at (BaseEntity)
ALTER TABLE group_members
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- #4 group_customer_access: missing updated_at (BaseEntity)
ALTER TABLE group_customer_access
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- #5 sync_records: missing next_retry_at
ALTER TABLE sync_records
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;
