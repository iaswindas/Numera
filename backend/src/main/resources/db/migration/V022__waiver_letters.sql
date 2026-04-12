-- ============================================================
-- V022__waiver_letters.sql — Waiver letter persistence
-- ============================================================

CREATE TABLE waiver_letters (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    monitoring_item_id  UUID NOT NULL REFERENCES covenant_monitoring_items(id) ON DELETE CASCADE,
    waiver_type         VARCHAR(20) NOT NULL CHECK (waiver_type IN ('INSTANCE', 'PERMANENT')),
    waived              BOOLEAN NOT NULL,
    letter_content      TEXT NOT NULL,
    template_id         UUID,
    signature_id        UUID,
    comments            TEXT,
    generated_by        UUID REFERENCES users(id),
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMPTZ,
    sent_by             UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for querying
CREATE INDEX idx_waiver_letters_tenant ON waiver_letters(tenant_id);
CREATE INDEX idx_waiver_letters_monitoring_item ON waiver_letters(monitoring_item_id);
CREATE INDEX idx_waiver_letters_generated_at ON waiver_letters(generated_at);
