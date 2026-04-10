CREATE TABLE spread_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    document_id UUID NOT NULL REFERENCES documents(id),
    template_id UUID NOT NULL REFERENCES model_templates(id),
    statement_date DATE NOT NULL,
    frequency VARCHAR(30) NOT NULL,
    audit_method VARCHAR(30),
    source_currency VARCHAR(10),
    consolidation VARCHAR(30),
    status VARCHAR(30) NOT NULL,
    current_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE spread_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spread_item_id UUID NOT NULL REFERENCES spread_items(id) ON DELETE CASCADE,
    line_item_id UUID NOT NULL REFERENCES model_line_items(id),
    item_code VARCHAR(50) NOT NULL,
    label VARCHAR(255) NOT NULL,
    mapped_value NUMERIC(20,4),
    raw_value NUMERIC(20,4),
    expression_type VARCHAR(30),
    expression_detail_json JSONB,
    scale_factor NUMERIC(12,4),
    confidence_score NUMERIC(8,4),
    confidence_level VARCHAR(20),
    source_page INT,
    source_text TEXT,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    autofilled BOOLEAN NOT NULL DEFAULT FALSE,
    formula_cell BOOLEAN NOT NULL DEFAULT FALSE,
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    override_comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (spread_item_id, item_code)
);

CREATE TABLE spread_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spread_item_id UUID NOT NULL REFERENCES spread_items(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    action VARCHAR(30) NOT NULL,
    comments TEXT,
    snapshot_json JSONB NOT NULL,
    cells_changed INT NOT NULL DEFAULT 0,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (spread_item_id, version_number)
);

CREATE TABLE expression_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    template_id UUID NOT NULL REFERENCES model_templates(id),
    item_code VARCHAR(50) NOT NULL,
    pattern_type VARCHAR(30) NOT NULL,
    pattern_json JSONB NOT NULL,
    usage_count INT NOT NULL DEFAULT 0,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, customer_id, template_id, item_code)
);