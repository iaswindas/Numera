-- V032: Add FSO (Federated Subspace Orthogonalization) columns to tenants table.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS fso_enabled      BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fso_last_round   INTEGER            DEFAULT NULL;

COMMENT ON COLUMN tenants.fso_enabled    IS 'Whether Federated Subspace Orthogonalization is enabled for this tenant';
COMMENT ON COLUMN tenants.fso_last_round IS 'Last FSO training round this tenant participated in';
