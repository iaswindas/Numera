-- V036: Performance indexes for common query patterns

-- Customer search optimization
CREATE INDEX IF NOT EXISTS idx_customer_tenant_name
    ON customers(tenant_id, LOWER(name) varchar_pattern_ops);

-- Audit log query optimization  
CREATE INDEX IF NOT EXISTS idx_audit_tenant_date
    ON event_log(tenant_id, created_at DESC);

-- Document listing by status
CREATE INDEX IF NOT EXISTS idx_document_tenant_status
    ON documents(tenant_id, status);

-- Spread items by customer
CREATE INDEX IF NOT EXISTS idx_spread_item_customer
    ON spread_items(customer_id, created_at DESC);

-- Covenant monitoring by status
CREATE INDEX IF NOT EXISTS idx_covenant_monitoring_status
    ON covenant_monitoring_items(covenant_id, status);

-- Workflow instances by status
CREATE INDEX IF NOT EXISTS idx_workflow_instance_status
    ON workflow_instances(tenant_id, status);

-- Spread values by spread item for fast lookup
CREATE INDEX IF NOT EXISTS idx_spread_value_item
    ON spread_values(spread_item_id);

-- Document zones by document
CREATE INDEX IF NOT EXISTS idx_zone_document
    ON detected_zones(document_id);
