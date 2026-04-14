-- Assign ROLE_ADMIN to the admin seed user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@numera.ai' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
