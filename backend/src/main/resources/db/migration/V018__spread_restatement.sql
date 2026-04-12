ALTER TABLE spread_items ADD COLUMN restatement_number INT NOT NULL DEFAULT 0;
ALTER TABLE spread_items ADD COLUMN original_spread_id UUID REFERENCES spread_items(id);

CREATE INDEX idx_spread_items_restatement_number ON spread_items(restatement_number);
CREATE INDEX idx_spread_items_original_spread_id ON spread_items(original_spread_id);
