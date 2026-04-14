-- ============================================================
-- V015__account_status.sql  — User account lifecycle states
-- ============================================================

-- Add account_status column to users table
ALTER TABLE users
    ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Create enum type for account status
CREATE TYPE account_status_enum AS ENUM ('PENDING', 'ACTIVE', 'INACTIVE', 'REJECTED');

-- Must drop default before type conversion, then re-add it
ALTER TABLE users ALTER COLUMN account_status DROP DEFAULT;
ALTER TABLE users
    ALTER COLUMN account_status TYPE account_status_enum USING account_status::account_status_enum;
ALTER TABLE users ALTER COLUMN account_status SET DEFAULT 'ACTIVE';

-- Create index for account status queries
CREATE INDEX idx_users_account_status ON users(account_status);
CREATE INDEX idx_users_tenant_account_status ON users(tenant_id, account_status);
