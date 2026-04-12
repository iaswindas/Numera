-- ============================================================
-- V023__signatures_title_field.sql — Add title to signatures
-- ============================================================

ALTER TABLE signatures ADD COLUMN title VARCHAR(255);
