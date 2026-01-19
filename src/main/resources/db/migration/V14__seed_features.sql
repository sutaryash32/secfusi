-- Seed features with group assignments

-- Browser Configuration & Control Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Homepage / startup control', 'BROWSER_HOMEPAGE_CONTROL', 'Control browser homepage and startup pages', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'BROWSER_CONFIG';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Force browser sign-in / profile', 'BROWSER_FORCE_SIGNIN', 'Force users to sign in to browser profile', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'BROWSER_CONFIG';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Disable incognito mode', 'BROWSER_DISABLE_INCOGNITO', 'Disable incognito/private browsing mode', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'BROWSER_CONFIG';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Disable password saving', 'BROWSER_DISABLE_PASSWORD_SAVE', 'Disable browser password saving feature', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'BROWSER_CONFIG';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Disable developer tools', 'BROWSER_DISABLE_DEVTOOLS', 'Disable browser developer tools', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'BROWSER_CONFIG';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Lock browser settings', 'BROWSER_LOCK_SETTINGS', 'Lock browser settings from user modification', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'BROWSER_CONFIG';

-- Extension Management Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Extension inventory & visibility', 'EXT_INVENTORY', 'View and track installed extensions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Allow / block extensions', 'EXT_ALLOW_BLOCK', 'Allow or block specific extensions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Approved extension list', 'EXT_APPROVED_LIST', 'Manage approved extension whitelist', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Force-install extensions', 'EXT_FORCE_INSTALL', 'Force install required extensions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Lock extension settings', 'EXT_LOCK_SETTINGS', 'Lock extension settings', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Control extension permissions', 'EXT_CONTROL_PERMS', 'Control extension permissions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Extension risk scoring', 'EXT_RISK_SCORING', 'Score extensions based on risk', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Conditional extension policies', 'EXT_CONDITIONAL_POLICIES', 'Apply conditional policies to extensions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Auto-remediation', 'EXT_AUTO_REMEDIATION', 'Automatic remediation of risky extensions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'EXTENSION_MGMT';

-- Data Protection (DLP) Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'PII detection', 'DLP_PII_DETECTION', 'Detect personally identifiable information', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Warn user', 'DLP_WARN_USER', 'Warn users about data protection violations', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Block uploads', 'DLP_BLOCK_UPLOADS', 'Block sensitive data uploads', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Block downloads', 'DLP_BLOCK_DOWNLOADS', 'Block sensitive data downloads', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Domain allowlist / exceptions', 'DLP_DOMAIN_ALLOWLIST', 'Configure allowed domains and exceptions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AD group-based DLP policies', 'DLP_AD_GROUP_POLICIES', 'Apply DLP policies based on AD groups', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Clipboard controls (copy/paste)', 'DLP_CLIPBOARD_CONTROLS', 'Control clipboard copy/paste operations', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Context-aware DLP', 'DLP_CONTEXT_AWARE', 'Context-aware data loss protection', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Adaptive enforcement', 'DLP_ADAPTIVE_ENFORCEMENT', 'Adaptive DLP enforcement based on risk', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AI prompt / response inspection', 'DLP_AI_INSPECTION', 'Inspect AI prompts and responses for sensitive data', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'DATA_PROTECTION';

-- Policy & Administration Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Central policy management', 'POLICY_CENTRAL_MGMT', 'Centralized policy management', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'POLICY_ADMIN';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Policy exceptions', 'POLICY_EXCEPTIONS', 'Configure policy exceptions', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'POLICY_ADMIN';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Global settings', 'POLICY_GLOBAL_SETTINGS', 'Configure global settings', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'POLICY_ADMIN';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Role-based admin access', 'POLICY_RBAC', 'Role-based administrative access control', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'POLICY_ADMIN';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Audit logs', 'POLICY_AUDIT_LOGS', 'View and manage audit logs', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'POLICY_ADMIN';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Advanced audit & compliance', 'POLICY_ADVANCED_AUDIT', 'Advanced audit and compliance features', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'POLICY_ADMIN';

-- Reporting & Insights Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Standard reports', 'REPORT_STANDARD', 'Access standard reports', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'REPORTING';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Export reports', 'REPORT_EXPORT', 'Export reports to various formats', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'REPORTING';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Usage analytics', 'REPORT_USAGE_ANALYTICS', 'View usage analytics', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'REPORTING';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Custom reports', 'REPORT_CUSTOM', 'Create custom reports', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'REPORTING';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Risk & compliance insights', 'REPORT_RISK_COMPLIANCE', 'View risk and compliance insights', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'REPORTING';

-- Zero Trust & Access Control Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Identity-based access (AD / IdP)', 'ZT_IDENTITY_ACCESS', 'Identity-based access control', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Browser Access Control (Managed Devices)', 'ZT_BROWSER_ACCESS', 'Browser access control for managed devices', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Domain allowlist / blocklist (access)', 'ZT_DOMAIN_ACCESS', 'Domain-based access control lists', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'IDP Configuration', 'ZT_IDP_CONFIG', 'Identity provider configuration', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Context-aware access (multi-signal)', 'ZT_CONTEXT_ACCESS', 'Context-aware access using multiple signals', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'ZERO_TRUST';

-- AI Operations Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AI Usage Monitoring', 'AI_USAGE_MONITORING', 'Monitor AI tool usage', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'AI_OPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'LLM Access Controls', 'AI_LLM_CONTROLS', 'Control access to LLM services', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'AI_OPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Prompt Management', 'AI_PROMPT_MGMT', 'Manage and control AI prompts', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'AI_OPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AI Agent Controls', 'AI_AGENT_CONTROLS', 'Control AI agent behavior', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'AI_OPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AI Workspace Management', 'AI_WORKSPACE_MGMT', 'Manage AI workspaces', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'AI_OPS';

-- Security Operations (SecOps) Features
INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Security Event Monitoring', 'SECOPS_SECURITY_EVENTS', 'Monitor security events', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'SECOPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'AI Event Monitoring', 'SECOPS_AI_EVENTS', 'Monitor AI-related security events', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'SECOPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Alerting & notifications', 'SECOPS_ALERTING', 'Configure alerts and notifications', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'SECOPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'Incident management', 'SECOPS_INCIDENT_MGMT', 'Manage security incidents', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'SECOPS';

INSERT INTO features (feature_name, feature_code, description, fk_feature_group_id, is_active)
SELECT 'MITRE ATT&CK mapping', 'SECOPS_MITRE_MAPPING', 'Map threats to MITRE ATT&CK framework', pk_feature_group_id, true
FROM feature_group WHERE group_code = 'SECOPS';
