CREATE TABLE event_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    parent_entity_type VARCHAR(100),
    parent_entity_id VARCHAR(36),
    diff_json TEXT,
    previous_hash VARCHAR(128) NOT NULL,
    current_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);