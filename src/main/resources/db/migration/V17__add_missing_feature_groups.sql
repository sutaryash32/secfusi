-- Add missing feature groups from the second feature matrix
INSERT INTO feature_group (group_name, group_code, description, display_order, is_active) VALUES
('Configuration', 'CONFIGURATION', 'Browser and extension configuration features', 9, true),
('Policy Profile', 'POLICY_PROFILE', 'Policy profile and unified policy features', 10, true),
('Users & Devices', 'USERS_DEVICES', 'User, group, and device management features', 11, true),
('Insights & Analytics', 'INSIGHTS', 'Analytics and activity insights features', 12, true),
('Global Settings', 'GLOBAL_SETTINGS', 'Global settings and configuration features', 13, true)
ON CONFLICT (group_code) DO NOTHING;
