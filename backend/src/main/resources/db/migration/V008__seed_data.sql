INSERT INTO tenants (id, code, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Tenant')
ON CONFLICT (id) DO NOTHING;

INSERT INTO roles (tenant_id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'ROLE_ADMIN'),
       ('00000000-0000-0000-0000-000000000001', 'ROLE_ANALYST')
ON CONFLICT DO NOTHING;

INSERT INTO users (tenant_id, email, password_hash, full_name, enabled)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin@numera.ai', '$2b$10$8jDkifrsjqn6xGY2Y1wx8eVw28Gi0oyv6Skfi.an7qd0WxLhcbfn6', 'Demo Admin', true)
ON CONFLICT DO NOTHING;