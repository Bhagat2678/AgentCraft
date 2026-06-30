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
