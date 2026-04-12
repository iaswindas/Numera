-- External integration systems registered per tenant
CREATE TABLE external_systems (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(50) NOT NULL,
    base_url    VARCHAR(2048) NOT NULL,
    api_key     VARCHAR(1024),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    config_json TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_external_systems_tenant_id ON external_systems(tenant_id);
CREATE INDEX idx_external_systems_type ON external_systems(type);

-- Synchronisation records tracking push/pull operations
CREATE TABLE sync_records (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    external_system_id  UUID        NOT NULL REFERENCES external_systems(id),
    entity_type         VARCHAR(50) NOT NULL,
    entity_id           UUID        NOT NULL,
    external_id         VARCHAR(512),
    direction           VARCHAR(10) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key     VARCHAR(255) NOT NULL,
    retry_count         INT         NOT NULL DEFAULT 0,
    max_retries         INT         NOT NULL DEFAULT 3,
    last_error          TEXT,
    synced_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sync_records_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_sync_records_status ON sync_records(status);
CREATE INDEX idx_sync_records_external_system_id ON sync_records(external_system_id);
CREATE INDEX idx_sync_records_entity ON sync_records(entity_type, entity_id);
CREATE INDEX idx_sync_records_tenant_id ON sync_records(tenant_id);
