-- Anomaly detection reports produced by OW-PGGR anomaly detector
CREATE TABLE anomaly_reports (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    spread_item_id  UUID            NOT NULL REFERENCES spread_items(id),
    overall_risk_score NUMERIC(5,4) NOT NULL DEFAULT 0,
    summary         TEXT            NOT NULL DEFAULT '',
    anomalies_json  JSONB           NOT NULL DEFAULT '[]'::jsonb,
    checked_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_anomaly_reports_spread_item_id ON anomaly_reports(spread_item_id);
CREATE INDEX idx_anomaly_reports_checked_at ON anomaly_reports(checked_at DESC);
