-- ============================================================
-- V020__covenant_audit_method.sql — Add audit method field
-- ============================================================

ALTER TABLE covenants ADD COLUMN audit_method VARCHAR(50);

-- Index for querying by audit method
CREATE INDEX idx_covenants_audit_method ON covenants(audit_method);
