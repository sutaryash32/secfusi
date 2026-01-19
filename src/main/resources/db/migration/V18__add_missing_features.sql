-- Add missing features from the second feature matrix

-- Configuration Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Browser Configuration', 'CONFIG_BROWSER', 'Browser configuration settings', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'CONFIGURATION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Extension Configuration', 'CONFIG_EXTENSION', 'Extension configuration settings', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'CONFIGURATION';

-- Policy Profile Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Unified Policy', 'POLICY_UNIFIED', 'Unified policy management', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'POLICY_PROFILE';

-- Users & Devices Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Users', 'USERS_MGMT', 'User management features', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'USERS_DEVICES';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Groups / OUs', 'USERS_GROUPS_OUS', 'Group and organizational unit management', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'USERS_DEVICES';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Devices', 'USERS_DEVICES_MGMT', 'Device management features', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'USERS_DEVICES';

-- Insights & Analytics Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Analytics', 'INSIGHTS_ANALYTICS', 'Analytics dashboard and metrics', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'INSIGHTS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'User Activity', 'INSIGHTS_USER_ACTIVITY', 'User activity monitoring and logs', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'INSIGHTS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Browser Activity', 'INSIGHTS_BROWSER_ACTIVITY', 'Browser activity monitoring and logs', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'INSIGHTS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AI Usage', 'INSIGHTS_AI_USAGE', 'AI usage analytics and monitoring', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'INSIGHTS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Compliance', 'INSIGHTS_COMPLIANCE', 'Compliance monitoring and reporting', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'INSIGHTS';

-- Zero Trust - Additional Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Network', 'ZT_NETWORK', 'Network access control', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Access Control', 'ZT_ACCESS_CONTROL', 'Access control management', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'URL Filtering', 'ZT_URL_FILTERING', 'URL filtering and categorization', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'IdP Integration', 'ZT_IDP_INTEGRATION', 'Identity provider integration', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

-- AI Operations - Additional Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AI Guardrails', 'AI_GUARDRAILS', 'AI guardrails and safety controls', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'AI_OPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Prompt Manager', 'AI_PROMPT_MANAGER', 'AI prompt management and templates', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'AI_OPS';

-- Global Settings Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Global Settings', 'GLOBAL_SETTINGS_MAIN', 'Main global settings configuration', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'GLOBAL_SETTINGS';

-- Reports Feature (separate from Reporting group)
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Reports', 'REPORT_MAIN', 'Main reports access', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'REPORTING';
