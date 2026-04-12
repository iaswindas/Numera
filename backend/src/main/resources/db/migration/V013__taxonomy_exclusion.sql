-- ============================================================
-- V013__taxonomy_exclusion.sql  — Taxonomy bulk & exclusion list
-- ============================================================

-- Taxonomy entries with full CRUD support
CREATE TABLE IF NOT EXISTS taxonomy_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    item_code       VARCHAR(100) NOT NULL,
    label           VARCHAR(500) NOT NULL,
    category        VARCHAR(100),
    parent_code     VARCHAR(100),
    synonyms        TEXT[],
    language        VARCHAR(10) NOT NULL DEFAULT 'en',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, item_code, language)
);

-- Exclusion list: configurable text cleaning rules
CREATE TABLE exclusion_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    category        VARCHAR(100) NOT NULL,
    pattern         VARCHAR(500) NOT NULL,
    pattern_type    VARCHAR(20) NOT NULL DEFAULT 'EXACT', -- EXACT, CONTAINS, REGEX, PREFIX, SUFFIX
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Predefined exclusion categories
COMMENT ON TABLE exclusion_rules IS 'Categories: HEADER_FOOTER, PAGE_NUMBER, WATERMARK, DISCLAIMER, SIGNATURE, DATE_STAMP, LOGO_TEXT, BOILERPLATE, ANNOTATION, CURRENCY_SYMBOL, UNIT_LABEL, METADATA';

CREATE INDEX idx_taxonomy_tenant ON taxonomy_entries(tenant_id);
CREATE INDEX idx_taxonomy_code ON taxonomy_entries(item_code);
CREATE INDEX idx_taxonomy_category ON taxonomy_entries(category);
CREATE INDEX idx_exclusion_tenant ON exclusion_rules(tenant_id);
CREATE INDEX idx_exclusion_category ON exclusion_rules(category);

-- Subsequent spreading: base period tracking
ALTER TABLE spread_items
    ADD COLUMN IF NOT EXISTS base_spread_id UUID REFERENCES spread_items(id),
    ADD COLUMN IF NOT EXISTS period_sequence INTEGER DEFAULT 0;
