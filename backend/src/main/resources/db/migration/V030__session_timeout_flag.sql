-- Add flag_value column to tenant_feature_flags for non-boolean configuration values
ALTER TABLE tenant_feature_flags ADD COLUMN flag_value VARCHAR(255);

-- Insert default session timeout flag (30 minutes) as a global default.
-- Tenants can override this by inserting a row with their tenant_id and desired value.
COMMENT ON COLUMN tenant_feature_flags.flag_value IS 'Optional string value for flags that carry a configurable value (e.g. timeout minutes)';
