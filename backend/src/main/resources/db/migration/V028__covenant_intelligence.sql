-- V028: Covenant Intelligence — materialised risk heatmap table
-- Supports fast dashboard queries for the covenant risk heatmap view.

CREATE TABLE IF NOT EXISTS risk_heatmap_entries (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    customer_id UUID         NOT NULL,
    customer_name VARCHAR(500) NOT NULL,
    covenant_id UUID         NOT NULL,
    covenant_name VARCHAR(500) NOT NULL,
    breach_probability NUMERIC(6,4) NOT NULL DEFAULT 0.5,
    current_value      NUMERIC(20,4),
    threshold          NUMERIC(20,4),
    status      VARCHAR(50)  NOT NULL DEFAULT 'DUE',
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_heatmap_tenant ON risk_heatmap_entries(tenant_id);
CREATE INDEX idx_risk_heatmap_customer ON risk_heatmap_entries(tenant_id, customer_id);
CREATE INDEX idx_risk_heatmap_covenant ON risk_heatmap_entries(tenant_id, covenant_id);
