CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    customer_code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    industry VARCHAR(120),
    country VARCHAR(120),
    relationship_manager VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, customer_code)
);