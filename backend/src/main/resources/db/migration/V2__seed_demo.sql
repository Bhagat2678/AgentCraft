INSERT INTO businesses (id, name, type, industry, location)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', 'Acme Corp', 'SERVICES', 'Technology', 'New York, NY');

-- Demo CEO
INSERT INTO users (id, business_id, display_name, status)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'Alice (CEO)', 'ACTIVE');
INSERT INTO user_phones (user_id, phone_number, is_primary, verified_at)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', '+15550000001', TRUE, NOW());

-- Demo Manager
INSERT INTO users (id, business_id, display_name, status)
VALUES ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'Bob (Manager)', 'ACTIVE');
INSERT INTO user_phones (user_id, phone_number, is_primary, verified_at)
VALUES ('bbbbbbbb-0000-0000-0000-000000000002', '+15550000002', TRUE, NOW());

-- Demo Employee
INSERT INTO users (id, business_id, display_name, status)
VALUES ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000001', 'Carol (Employee)', 'ACTIVE');
INSERT INTO user_phones (user_id, phone_number, is_primary, verified_at)
VALUES ('bbbbbbbb-0000-0000-0000-000000000003', '+15550000003', TRUE, NOW());

-- Seed default roles for Acme Corp
INSERT INTO roles (id, business_id, name, level, is_default) VALUES
('cccccccc-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'CEO', 1, TRUE),
('cccccccc-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'Director', 2, TRUE),
('cccccccc-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000001', 'Manager', 3, TRUE),
('cccccccc-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000001', 'Lead', 4, TRUE),
('cccccccc-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000001', 'Employee', 5, TRUE);

-- CEO Permissions
INSERT INTO role_permissions (role_id, permission) VALUES
('cccccccc-0000-0000-0000-000000000001', 'BUSINESS_CONFIGURE'),
('cccccccc-0000-0000-0000-000000000001', 'USER_MANAGE'),
('cccccccc-0000-0000-0000-000000000001', 'USER_VIEW'),
('cccccccc-0000-0000-0000-000000000001', 'ROLE_MANAGE'),
('cccccccc-0000-0000-0000-000000000001', 'DEPT_MANAGE'),
('cccccccc-0000-0000-0000-000000000001', 'TASK_CREATE'),
('cccccccc-0000-0000-0000-000000000001', 'TASK_ASSIGN'),
('cccccccc-0000-0000-0000-000000000001', 'TASK_APPROVE'),
('cccccccc-0000-0000-0000-000000000001', 'TASK_COMPLETE'),
('cccccccc-0000-0000-0000-000000000001', 'TASK_VIEW_ALL'),
('cccccccc-0000-0000-0000-000000000001', 'TASK_VIEW_OWN'),
('cccccccc-0000-0000-0000-000000000001', 'REPORT_VIEW'),
('cccccccc-0000-0000-0000-000000000001', 'REPORT_EXPORT'),
('cccccccc-0000-0000-0000-000000000001', 'TEMPLATE_MANAGE'),
('cccccccc-0000-0000-0000-000000000001', 'WEBHOOK_MANAGE');

-- Manager Permissions
INSERT INTO role_permissions (role_id, permission) VALUES
('cccccccc-0000-0000-0000-000000000003', 'USER_MANAGE'),
('cccccccc-0000-0000-0000-000000000003', 'USER_VIEW'),
('cccccccc-0000-0000-0000-000000000003', 'DEPT_MANAGE'),
('cccccccc-0000-0000-0000-000000000003', 'TASK_CREATE'),
('cccccccc-0000-0000-0000-000000000003', 'TASK_ASSIGN'),
('cccccccc-0000-0000-0000-000000000003', 'TASK_APPROVE'),
('cccccccc-0000-0000-0000-000000000003', 'TASK_COMPLETE'),
('cccccccc-0000-0000-0000-000000000003', 'TASK_VIEW_ALL'),
('cccccccc-0000-0000-0000-000000000003', 'TASK_VIEW_OWN'),
('cccccccc-0000-0000-0000-000000000003', 'REPORT_VIEW');

-- Employee Permissions
INSERT INTO role_permissions (role_id, permission) VALUES
('cccccccc-0000-0000-0000-000000000005', 'TASK_COMPLETE'),
('cccccccc-0000-0000-0000-000000000005', 'TASK_VIEW_OWN');

-- Assign roles to demo users
INSERT INTO user_roles (user_id, role_id) VALUES
('bbbbbbbb-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001'),
('bbbbbbbb-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000003'),
('bbbbbbbb-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000005');
