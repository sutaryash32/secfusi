-- Seed feature groups based on the feature matrix
INSERT INTO feature_group (group_name, group_code, description, display_order, is_active) VALUES
('Browser Configuration & Control', 'BROWSER_CONFIG', 'Browser configuration and control features', 1, true),
('Extension Management', 'EXTENSION_MGMT', 'Browser extension management features', 2, true),
('Data Protection (DLP)', 'DATA_PROTECTION', 'Data loss protection and security features', 3, true),
('Policy & Administration', 'POLICY_ADMIN', 'Policy management and administration features', 4, true),
('Reporting & Insights', 'REPORTING', 'Reporting and analytics features', 5, true),
('Zero Trust & Access Control', 'ZERO_TRUST', 'Zero trust security and access control features', 6, true),
('AI Operations', 'AI_OPS', 'AI operations and monitoring features', 7, true),
('Security Operations (SecOps)', 'SECOPS', 'Security operations and monitoring features', 8, true)
ON CONFLICT (group_code) DO NOTHING;
