-- ============================================================
-- V012__sso_mfa.sql  — SSO + MFA support
-- ============================================================

-- Add SSO and MFA fields to users table
ALTER TABLE users
    ADD COLUMN last_login_at     TIMESTAMPTZ,
    ADD COLUMN sso_provider      VARCHAR(50),
    ADD COLUMN sso_subject_id    VARCHAR(255),
    ADD COLUMN mfa_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret        VARCHAR(255),
    ADD COLUMN mfa_verified      BOOLEAN NOT NULL DEFAULT FALSE;

-- SSO provider configurations per tenant
CREATE TABLE sso_configurations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    provider_type   VARCHAR(20) NOT NULL,  -- OIDC, SAML
    provider_name   VARCHAR(100) NOT NULL,
    client_id       VARCHAR(255),
    client_secret   VARCHAR(255),
    issuer_uri      VARCHAR(500),
    authorization_uri VARCHAR(500),
    token_uri       VARCHAR(500),
    userinfo_uri    VARCHAR(500),
    jwks_uri        VARCHAR(500),
    saml_metadata_url VARCHAR(500),
    saml_entity_id  VARCHAR(500),
    saml_acs_url    VARCHAR(500),
    attribute_mapping JSONB NOT NULL DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, provider_name)
);

-- MFA backup codes
CREATE TABLE mfa_backup_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(255) NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sso_config_tenant ON sso_configurations(tenant_id);
CREATE INDEX idx_mfa_backup_user ON mfa_backup_codes(user_id);
CREATE UNIQUE INDEX idx_users_sso ON users(sso_provider, sso_subject_id) WHERE sso_provider IS NOT NULL;
