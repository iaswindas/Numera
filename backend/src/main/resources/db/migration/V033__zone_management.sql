-- Zone Management for admin panel
CREATE TABLE managed_zones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    color       VARCHAR(20)  NOT NULL DEFAULT '#6366f1',
    description TEXT,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

INSERT INTO managed_zones (id, tenant_id, name, code, color, sort_order, is_active)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Balance Sheet', 'BALANCE_SHEET', '#3b82f6', 1, TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Income Statement', 'INCOME_STATEMENT', '#22c55e', 2, TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Cash Flow', 'CASH_FLOW', '#f59e0b', 3, TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Notes', 'NOTES', '#8b5cf6', 4, TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Ratios', 'RATIOS', '#ef4444', 5, TRUE);
