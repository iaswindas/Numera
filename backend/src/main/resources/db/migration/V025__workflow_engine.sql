-- Workflow Engine tables
-- V025: workflow_definitions, workflow_step_definitions, workflow_instances, workflow_tasks

-- ============================================================
-- Workflow Definitions
-- ============================================================
CREATE TABLE workflow_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    version         INT          NOT NULL DEFAULT 1,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_def_tenant_type ON workflow_definitions (tenant_id, type);
CREATE INDEX idx_workflow_def_type_active ON workflow_definitions (type, active);

-- ============================================================
-- Workflow Step Definitions
-- ============================================================
CREATE TABLE workflow_step_definitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_definition_id      UUID         NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    step_order                  INT          NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    type                        VARCHAR(50)  NOT NULL,
    required_role               VARCHAR(100),
    sla_hours                   INT,
    escalate_to                 VARCHAR(100),
    auto_approve_on_sla_expiry  BOOLEAN      NOT NULL DEFAULT FALSE,
    condition_expression        TEXT,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_wf_step_def_workflow ON workflow_step_definitions (workflow_definition_id);
CREATE UNIQUE INDEX idx_wf_step_def_order ON workflow_step_definitions (workflow_definition_id, step_order);

-- ============================================================
-- Workflow Instances
-- ============================================================
CREATE TABLE workflow_instances (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    workflow_definition_id  UUID         NOT NULL REFERENCES workflow_definitions(id),
    entity_type             VARCHAR(100) NOT NULL,
    entity_id               UUID         NOT NULL,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    current_step_order      INT          NOT NULL DEFAULT 0,
    started_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at            TIMESTAMP WITH TIME ZONE,
    started_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_wf_inst_entity ON workflow_instances (entity_type, entity_id);
CREATE INDEX idx_wf_inst_status_tenant ON workflow_instances (status, tenant_id);
CREATE INDEX idx_wf_inst_definition ON workflow_instances (workflow_definition_id);

-- ============================================================
-- Workflow Tasks
-- ============================================================
CREATE TABLE workflow_tasks (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_instance_id    UUID         NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_order              INT          NOT NULL,
    step_name               VARCHAR(255) NOT NULL,
    assignee                VARCHAR(255),
    assigned_role           VARCHAR(100),
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    outcome                 VARCHAR(100),
    comment                 TEXT,
    due_at                  TIMESTAMP WITH TIME ZONE,
    completed_at            TIMESTAMP WITH TIME ZONE,
    completed_by            VARCHAR(255),
    escalated_at            TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_wf_task_instance_status ON workflow_tasks (workflow_instance_id, status);
CREATE INDEX idx_wf_task_role_status ON workflow_tasks (assigned_role, status);
CREATE INDEX idx_wf_task_due_at ON workflow_tasks (due_at) WHERE status IN ('PENDING', 'IN_PROGRESS');

-- ============================================================
-- Seed: Default Spread Approval workflow
-- ============================================================
INSERT INTO workflow_definitions (id, tenant_id, name, type, version, active, description)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'Spread Approval - Standard',
    'SPREAD_APPROVAL',
    1,
    TRUE,
    'Default spread approval workflow with analyst submit, manager review, and senior approval steps.'
);

INSERT INTO workflow_step_definitions (id, workflow_definition_id, step_order, name, type, required_role, sla_hours, escalate_to, auto_approve_on_sla_expiry)
VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 0, 'Submit',          'START',    'ANALYST',  NULL,  NULL,            FALSE),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 1, 'Manager Review',   'APPROVAL', 'MANAGER',  24,    'SENIOR_MANAGER', FALSE),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 2, 'Senior Approval',  'APPROVAL', 'SENIOR_MANAGER', 48, NULL,          TRUE),
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 3, 'Complete',         'END',       NULL,       NULL,  NULL,            FALSE);

-- ============================================================
-- Seed: Default Covenant Waiver workflow
-- ============================================================
INSERT INTO workflow_definitions (id, tenant_id, name, type, version, active, description)
VALUES (
    '10000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'Covenant Waiver Workflow',
    'COVENANT_WAIVER',
    1,
    TRUE,
    'Default covenant waiver workflow: trigger, analyst review, committee decision, close.'
);

INSERT INTO workflow_step_definitions (id, workflow_definition_id, step_order, name, type, required_role, sla_hours, escalate_to, auto_approve_on_sla_expiry)
VALUES
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 0, 'Trigger',            'START',    NULL,       NULL, NULL,           FALSE),
    ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 1, 'Analyst Review',     'APPROVAL', 'ANALYST',  48,   'MANAGER',      FALSE),
    ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 2, 'Committee Decision', 'APPROVAL', 'MANAGER',  72,   'SENIOR_MANAGER', FALSE),
    ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 3, 'Close',              'END',       NULL,       NULL, NULL,           FALSE);
