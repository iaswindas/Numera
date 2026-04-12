-- ============================================================
-- V014__password_policies.sql  — Tenant password policy and history tracking
-- ============================================================

CREATE TABLE IF NOT EXISTS password_policies (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES tenants(id),
    min_length                  INTEGER NOT NULL DEFAULT 12,
    require_uppercase           BOOLEAN NOT NULL DEFAULT TRUE,
    require_lowercase           BOOLEAN NOT NULL DEFAULT TRUE,
    require_digit               BOOLEAN NOT NULL DEFAULT TRUE,
    require_special_character   BOOLEAN NOT NULL DEFAULT TRUE,
    expiry_days                 INTEGER NOT NULL DEFAULT 90,
    history_size                INTEGER NOT NULL DEFAULT 5,
    enabled                     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id)
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS password_history JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE users
SET password_changed_at = COALESCE(password_changed_at, created_at),
    password_history = COALESCE(password_history, '[]'::jsonb)
WHERE password_changed_at IS NULL OR password_history IS NULL;

ALTER TABLE users
    ALTER COLUMN password_changed_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_password_policies_tenant ON password_policies(tenant_id);