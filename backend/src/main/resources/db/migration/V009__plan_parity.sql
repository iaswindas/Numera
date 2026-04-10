ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS language VARCHAR(20) NOT NULL DEFAULT 'en',
    ADD COLUMN IF NOT EXISTS pdf_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS backend_used VARCHAR(40),
    ADD COLUMN IF NOT EXISTS total_pages INT,
    ADD COLUMN IF NOT EXISTS processing_time_ms INT,
    ADD COLUMN IF NOT EXISTS uploaded_by_name VARCHAR(255);

UPDATE documents
SET original_filename = COALESCE(original_filename, file_name),
    uploaded_by_name = COALESCE(uploaded_by_name, uploaded_by);

ALTER TABLE model_line_items
    ADD COLUMN IF NOT EXISTS zone VARCHAR(80) NOT NULL DEFAULT 'INCOME_STATEMENT',
    ADD COLUMN IF NOT EXISTS category VARCHAR(255),
    ADD COLUMN IF NOT EXISTS indent_level INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sign_convention VARCHAR(30) NOT NULL DEFAULT 'NATURAL';
