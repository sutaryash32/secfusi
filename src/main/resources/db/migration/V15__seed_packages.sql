-- Ensure the 5 package types exist
INSERT INTO package_type (package_type_name) VALUES
('Trial')
ON CONFLICT DO NOTHING;

INSERT INTO package_type (package_type_name) VALUES
('Freemium')
ON CONFLICT DO NOTHING;

INSERT INTO package_type (package_type_name) VALUES
('Basic')
ON CONFLICT DO NOTHING;

INSERT INTO package_type (package_type_name) VALUES
('Standard')
ON CONFLICT DO NOTHING;

INSERT INTO package_type (package_type_name) VALUES
('Premium')
ON CONFLICT DO NOTHING;

-- Seed packages (using subqueries to get package_type_id)
INSERT INTO package (package_name, description, package_type_id, created_at, updated_at)
SELECT 'Trial', 'Trial package with limited features for evaluation', pk_package_type_id, NOW(), NOW()
FROM package_type WHERE package_type_name = 'Trial'
ON CONFLICT DO NOTHING;

INSERT INTO package (package_name, description, package_type_id, created_at, updated_at)
SELECT 'Freemium', 'Free tier with basic features', pk_package_type_id, NOW(), NOW()
FROM package_type WHERE package_type_name = 'Freemium'
ON CONFLICT DO NOTHING;

INSERT INTO package (package_name, description, package_type_id, created_at, updated_at)
SELECT 'Basic', 'Basic subscription package with essential features', pk_package_type_id, NOW(), NOW()
FROM package_type WHERE package_type_name = 'Basic'
ON CONFLICT DO NOTHING;

INSERT INTO package (package_name, description, package_type_id, created_at, updated_at)
SELECT 'Standard', 'Standard subscription package with advanced features', pk_package_type_id, NOW(), NOW()
FROM package_type WHERE package_type_name = 'Standard'
ON CONFLICT DO NOTHING;

INSERT INTO package (package_name, description, package_type_id, created_at, updated_at)
SELECT 'Premium', 'Premium subscription with all features and priority support', pk_package_type_id, NOW(), NOW()
FROM package_type WHERE package_type_name = 'Premium'
ON CONFLICT DO NOTHING;
