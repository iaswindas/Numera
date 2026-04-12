ALTER TABLE model_templates ADD COLUMN customer_id UUID;
ALTER TABLE model_templates ADD COLUMN parent_template_id UUID REFERENCES model_templates(id);
ALTER TABLE model_templates ADD COLUMN is_global BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_model_templates_customer_id ON model_templates(customer_id);
CREATE INDEX idx_model_templates_parent_template_id ON model_templates(parent_template_id);
