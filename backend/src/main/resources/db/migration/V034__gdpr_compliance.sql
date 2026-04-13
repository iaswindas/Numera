-- V034: GDPR Compliance Tables
-- Consent management and data sovereignty support

CREATE TABLE IF NOT EXISTS gdpr_consents (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    consent_type    VARCHAR(64)  NOT NULL,
    granted         BOOLEAN      NOT NULL DEFAULT true,
    granted_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMP,
    ip_address      VARCHAR(45),
    CONSTRAINT fk_gdpr_consent_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_gdpr_consents_user    ON gdpr_consents (user_id, tenant_id);
CREATE INDEX idx_gdpr_consents_type    ON gdpr_consents (consent_type, tenant_id);
CREATE INDEX idx_gdpr_consents_active  ON gdpr_consents (user_id, tenant_id, consent_type) WHERE revoked_at IS NULL;

-- Soft-delete / anonymisation support on users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted    BOOLEAN   DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Session tracking for erasure
CREATE TABLE IF NOT EXISTS user_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_user_sessions_user ON user_sessions (user_id, tenant_id);

-- Data processing audit log (separate from main audit for GDPR compliance officers)
CREATE TABLE IF NOT EXISTS gdpr_processing_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    action          VARCHAR(32)  NOT NULL,  -- EXPORT, ERASURE, CONSENT_GRANT, CONSENT_REVOKE
    target_user_id  UUID         NOT NULL,
    performed_by    VARCHAR(255) NOT NULL,
    details         JSONB,
    performed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_gdpr_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_gdpr_processing_log_user   ON gdpr_processing_log (target_user_id, tenant_id);
CREATE INDEX idx_gdpr_processing_log_action ON gdpr_processing_log (action, tenant_id);

-- Comments table reference (add author FK if not present)
-- Note: comments table may already exist; this is safe for idempotent migration
CREATE TABLE IF NOT EXISTS comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    author_id   UUID         NOT NULL,
    entity_type VARCHAR(64)  NOT NULL,
    entity_id   UUID         NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_comment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);
