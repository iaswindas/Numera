CREATE TABLE model_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    currency VARCHAR(10) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model_line_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES model_templates(id) ON DELETE CASCADE,
    item_code VARCHAR(50) NOT NULL,
    label VARCHAR(255) NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    formula TEXT,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    total BOOLEAN NOT NULL DEFAULT FALSE,
    aliases_json JSONB,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (template_id, item_code)
);

CREATE TABLE model_validations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES model_templates(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    expression TEXT NOT NULL,
    severity VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);