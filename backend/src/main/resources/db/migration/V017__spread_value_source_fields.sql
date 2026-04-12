ALTER TABLE spread_values ADD COLUMN source_document_name VARCHAR(255);
ALTER TABLE spread_values ADD COLUMN source_bbox TEXT;
ALTER TABLE spread_values ADD COLUMN notes TEXT;

CREATE INDEX idx_spread_values_source_document_name ON spread_values(source_document_name);
