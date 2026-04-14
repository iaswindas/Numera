-- ============================================================
-- V010__covenants.sql  — Covenant module tables
-- ============================================================

-- Covenant-specific customer profile (extends the core customer)
CREATE TABLE covenant_customers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    rim_id              VARCHAR(100),
    cl_entity_id        VARCHAR(100),
    financial_year_end  DATE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, customer_id)
);

-- Internal (system users) and external (email-only) contacts per covenant customer
CREATE TABLE covenant_contacts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    covenant_customer_id    UUID NOT NULL REFERENCES covenant_customers(id) ON DELETE CASCADE,
    contact_type            VARCHAR(20) NOT NULL CHECK (contact_type IN ('INTERNAL', 'EXTERNAL')),
    user_id                 UUID REFERENCES users(id),
    name                    VARCHAR(255) NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Covenant definitions — both financial and non-financial
CREATE TABLE covenants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    covenant_customer_id    UUID NOT NULL REFERENCES covenant_customers(id) ON DELETE CASCADE,
    covenant_type           VARCHAR(20) NOT NULL CHECK (covenant_type IN ('FINANCIAL', 'NON_FINANCIAL')),
    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    frequency               VARCHAR(30) NOT NULL
                                CHECK (frequency IN ('MONTHLY','QUARTERLY','SEMI_ANNUALLY','ANNUALLY','FY_TO_DATE')),
    -- Financial covenant fields
    formula                 TEXT,
    operator                VARCHAR(10) CHECK (operator IN ('GTE','LTE','EQ','BETWEEN')),
    threshold_value         DECIMAL(20,4),
    threshold_min           DECIMAL(20,4),
    threshold_max           DECIMAL(20,4),
    -- Non-financial covenant fields
    document_type           VARCHAR(100),
    item_type               VARCHAR(100),
    -- Common
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_by              UUID REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per-period monitoring items auto-generated from covenant definitions
CREATE TABLE covenant_monitoring_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES tenants(id),
    covenant_id                 UUID NOT NULL REFERENCES covenants(id) ON DELETE CASCADE,
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,
    due_date                    DATE NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'DUE',
    -- Financial value fields
    calculated_value            DECIMAL(20,4),
    manual_value                DECIMAL(20,4),
    manual_value_justification  TEXT,
    -- Workflow tracking
    submitted_by                UUID REFERENCES users(id),
    submitted_at                TIMESTAMPTZ,
    approved_by                 UUID REFERENCES users(id),
    approved_at                 TIMESTAMPTZ,
    checker_comments            TEXT,
    -- Predictive intelligence
    breach_probability          DECIMAL(5,4),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Documents uploaded for non-financial covenant compliance
CREATE TABLE covenant_documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    monitoring_item_id  UUID NOT NULL REFERENCES covenant_monitoring_items(id) ON DELETE CASCADE,
    file_name           VARCHAR(500) NOT NULL,
    storage_key         VARCHAR(1000) NOT NULL,
    file_size           BIGINT,
    content_type        VARCHAR(200),
    uploaded_by         UUID REFERENCES users(id),
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Email / letter templates for covenant notifications
CREATE TABLE email_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    name                VARCHAR(255) NOT NULL,
    covenant_type       VARCHAR(20),
    template_category   VARCHAR(100),
    subject             VARCHAR(500),
    body_html           TEXT NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Manager signatures appended to generated letters
CREATE TABLE signatures (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    html_content    TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_covenant_customers_tenant     ON covenant_customers(tenant_id);
CREATE INDEX idx_covenant_customers_customer   ON covenant_customers(customer_id);
CREATE INDEX idx_covenants_cc                  ON covenants(covenant_customer_id);
CREATE INDEX idx_covenants_tenant_type         ON covenants(tenant_id, covenant_type);
CREATE INDEX idx_monitoring_items_covenant     ON covenant_monitoring_items(covenant_id);
CREATE INDEX idx_monitoring_items_tenant_status ON covenant_monitoring_items(tenant_id, status);
CREATE INDEX idx_monitoring_items_due_date     ON covenant_monitoring_items(due_date);
CREATE INDEX idx_covenant_documents_item       ON covenant_documents(monitoring_item_id);
CREATE INDEX idx_email_templates_tenant        ON email_templates(tenant_id);
CREATE INDEX idx_signatures_tenant             ON signatures(tenant_id);
