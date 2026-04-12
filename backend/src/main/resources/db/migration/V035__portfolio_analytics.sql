-- Portfolio Analytics: ratio snapshots and shared dashboards
-- V035

CREATE TABLE portfolio_ratio_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    customer_id     UUID        NOT NULL,
    customer_name   VARCHAR(500) NOT NULL,
    spread_item_id  UUID        NOT NULL,
    statement_date  DATE        NOT NULL,
    ratio_code      VARCHAR(50) NOT NULL,
    ratio_label     VARCHAR(100) NOT NULL,
    value           NUMERIC(18,6) NOT NULL DEFAULT 0,
    previous_value  NUMERIC(18,6),
    change_percent  NUMERIC(10,4),
    snapshot_version INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_prs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_prs_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_prs_spread_item FOREIGN KEY (spread_item_id) REFERENCES spread_items(id)
);

CREATE INDEX idx_prs_tenant ON portfolio_ratio_snapshots(tenant_id);
CREATE INDEX idx_prs_tenant_customer ON portfolio_ratio_snapshots(tenant_id, customer_id);
CREATE INDEX idx_prs_tenant_ratio ON portfolio_ratio_snapshots(tenant_id, ratio_code);
CREATE INDEX idx_prs_tenant_date ON portfolio_ratio_snapshots(tenant_id, statement_date);
CREATE INDEX idx_prs_change ON portfolio_ratio_snapshots(tenant_id, change_percent) WHERE change_percent IS NOT NULL;

CREATE TABLE shared_dashboards (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    token               VARCHAR(100) NOT NULL UNIQUE,
    created_by          UUID         NOT NULL,
    dashboard_config_json TEXT       NOT NULL DEFAULT '{}',
    title               VARCHAR(500),
    expires_at          TIMESTAMPTZ  NOT NULL,
    view_count          INT          NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_sd_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_sd_token ON shared_dashboards(token);
CREATE INDEX idx_sd_tenant ON shared_dashboards(tenant_id);
