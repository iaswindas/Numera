-- ============================================================
-- V021__covenant_reminder_config.sql — Add reminder config
-- ============================================================

ALTER TABLE covenants 
    ADD COLUMN reminder_days_before INT NOT NULL DEFAULT 7,
    ADD COLUMN reminder_days_after INT NOT NULL DEFAULT 3;

-- Index for reminder queries
CREATE INDEX idx_covenant_monitoring_items_due_date_status 
    ON covenant_monitoring_items(due_date, status);
