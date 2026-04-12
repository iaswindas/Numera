-- V031: Report schedules for automated report delivery
CREATE TABLE report_schedules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID    NOT NULL REFERENCES tenants(id),
    report_name   VARCHAR(255) NOT NULL,
    report_type   VARCHAR(50)  NOT NULL, -- SPREADING, COVENANT, AUDIT
    report_format VARCHAR(10)  NOT NULL DEFAULT 'XLSX', -- XLSX, PDF, CSV
    frequency     VARCHAR(20)  NOT NULL DEFAULT 'DAILY', -- DAILY, WEEKLY, MONTHLY, QUARTERLY
    recipient_emails TEXT     NOT NULL,
    filter_start_offset BIGINT,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at   TIMESTAMPTZ,
    last_run_at   TIMESTAMPTZ,
    last_error    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_report_schedules_tenant ON report_schedules(tenant_id);
CREATE INDEX idx_report_schedules_next_run ON report_schedules(enabled, next_run_at);
