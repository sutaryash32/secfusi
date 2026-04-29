# Missing Schema Report

This document explains the gap between:

- tables referenced by JPA entities in code
- tables explicitly created by checked-in SQL migration files

This matters because if you create a brand new empty PostgreSQL database and run only the SQL files present in this repository, some services may still fail if required tables are not created.

---

## How This Report Was Derived

This report is based on:
- `@Table(name = "...")` values from entity classes
- checked-in SQL files under `src/main/resources/db/migration`
- table names explicitly created by `CREATE TABLE ...`

Important:
- This report only checks what is visible in this repository
- It does not assume any external DB dump, runtime auto-DDL, or deployment-only SQL

---

## Summary

### What Is Present

The repo contains:
- partial SQL migrations for IAM
- partial SQL migrations for Tenants
- partial SQL migrations for Events

### What Is Missing

The repo does **not** contain a full schema bootstrap for every table referenced by the entity model.

That means:
- some tables are referenced in code but not created in repo SQL
- some shared reference tables appear to be assumed to already exist
- some services likely rely on external database setup not committed here

---

## 1. IAM Service Schema Gap

### Entity Tables Referenced By Code

- `access_level`
- `addon_pricing`
- `Address`
- `api_flags`
- `auth_provider_config`
- `billing_cycle`
- `browserpolicy`
- `cities`
- `country`
- `device_user`
- `events`
- `events_groups`
- `events_groups_device_user_map`
- `events_group_history`
- `extension_policy`
- `features`
- `feature_group`
- `feature_types`
- `groups`
- `industry`
- `login_audit_event`
- `networkpolicy`
- `package`
- `package_feature_mapping`
- `package_pricing`
- `package_type`
- `policy_assignments`
- `region`
- `retention_period`
- `roles`
- `scopes`
- `sso_configurations`
- `sso_provider_url_config`
- `states`
- `Tenant`
- `tenant_addon_feature`
- `tenant_api_mappings`
- `tenant_subscription`
- `tenant_types`
- `Users`
- `user_device_login`

### Tables Explicitly Created By Checked-In IAM SQL

- `login_audit_event`
- `feature_group`
- `access_level`
- `retention_period`
- `package_type`
- `package`
- `feature_types`
- `features`
- `package_feature_mapping`
- `billing_cycle`
- `tenant_addon_feature`
- `package_pricing`
- `tenant_subscription`
- `addon_pricing`
- `addon_bundles`
- `addon_bundle_features`
- `user_device_login`
- `events_group_history`

### IAM Tables Referenced In Code But Not Created By Checked-In IAM SQL

- `Address`
- `api_flags`
- `auth_provider_config`
- `browserpolicy`
- `cities`
- `country`
- `device_user`
- `events`
- `events_groups`
- `events_groups_device_user_map`
- `extension_policy`
- `groups`
- `industry`
- `networkpolicy`
- `policy_assignments`
- `region`
- `roles`
- `scopes`
- `sso_configurations`
- `sso_provider_url_config`
- `states`
- `Tenant`
- `tenant_api_mappings`
- `tenant_types`
- `Users`

### IAM Observation

IAM has a fairly large number of shared/core tables referenced by entities that are not created by the checked-in IAM migrations.

Most likely explanations:
- they come from an external bootstrap SQL
- they are shared tables created elsewhere outside this repo
- they were created historically but their migrations are not committed here

---

## 2. Tenants Service Schema Gap

### Entity Tables Referenced By Code

- `Address`
- `api_flags`
- `audit_log`
- `auth_provider_config`
- `billing_cycle`
- `devices`
- `events`
- `browserpolicy`
- `cities`
- `compliancerules`
- `country`
- `dlp`
- `events_groups`
- `extension_api_keys`
- `extension_detail`
- `extension_policy`
- `groups`
- `homepage`
- `landingpage`
- `login_audit_event`
- `managed_extensions`
- `networkconfiguration`
- `networkpolicy`
- `notifications`
- `notification_preferences`
- `package_pricing`
- `package_type`
- `policy_assignments`
- `region`
- `roles`
- `scopes`
- `shortcut`
- `smtp_config`
- `states`
- `subscription_history`
- `package`
- `Tenant`
- `tenant_api_mappings`
- `tenant_subscription`
- `TenantTypes`
- `urlfilter`
- `Users`
- `watermarking`
- `webhook_configs`
- `webhook_delivery_logs`

### Tables Explicitly Created By Checked-In Tenants SQL

- `login_audit_event`
- `subscription_history`
- `notifications`
- `notification_preferences`
- `webhook_configs`
- `webhook_delivery_logs`
- `audit_log`

### Tenants Tables Referenced In Code But Not Created By Checked-In Tenants SQL

- `Address`
- `api_flags`
- `auth_provider_config`
- `billing_cycle`
- `devices`
- `events`
- `browserpolicy`
- `cities`
- `compliancerules`
- `country`
- `dlp`
- `events_groups`
- `extension_api_keys`
- `extension_detail`
- `extension_policy`
- `groups`
- `homepage`
- `landingpage`
- `managed_extensions`
- `networkconfiguration`
- `networkpolicy`
- `package_pricing`
- `package_type`
- `policy_assignments`
- `region`
- `roles`
- `scopes`
- `shortcut`
- `smtp_config`
- `states`
- `package`
- `Tenant`
- `tenant_api_mappings`
- `tenant_subscription`
- `TenantTypes`
- `urlfilter`
- `Users`
- `watermarking`

### Tenants Observation

Tenants has the largest schema gap relative to its checked-in SQL.

The SQL present in repo covers mostly:
- login audit
- subscription history
- notifications
- webhooks
- audit log

But the entity model expects many more operational tables for:
- policies
- tenant master data
- extension config
- network config
- users and roles

So the checked-in Tenants SQL alone is clearly not enough for a fresh empty database.

---

## 3. Events Service Schema Gap

### Entity Tables Referenced By Code

- `api_flags`
- `auth_provider_config`
- `browserpolicy`
- `devices`
- `device_user`
- `escalation_rules`
- `events`
- `event_inbox`
- `events_groups`
- `events_groups_device_user_map`
- `extension_api_keys`
- `api_key_configuration`
- `extension_api_key_rotation_history`
- `extension_events`
- `extension_policy`
- `groups`
- `incidents`
- `incident_activities`
- `incident_assignees`
- `incident_events`
- `incident_playbooks`
- `incident_playbook_steps`
- `installed_extensions`
- `networkpolicy`
- `notifications`
- `notification_preferences`
- `playbook_templates`
- `policy_assignments`
- `roles`
- `scopes`
- `smtp_config`
- `Tenant`
- `tenant_api_mappings`
- `tenant_types`
- `Users`

### Tables Explicitly Created By Checked-In Events SQL

- `devices`
- `incidents`
- `incident_activities`
- `incident_events`
- `incident_assignees`
- `playbook_templates`
- `incident_playbooks`
- `incident_playbook_steps`
- `escalation_rules`
- `extension_events`
- `installed_extensions`

### Events Tables Referenced In Code But Not Created By Checked-In Events SQL

- `api_flags`
- `auth_provider_config`
- `browserpolicy`
- `device_user`
- `events`
- `event_inbox`
- `events_groups`
- `events_groups_device_user_map`
- `extension_api_keys`
- `api_key_configuration`
- `extension_api_key_rotation_history`
- `extension_policy`
- `groups`
- `networkpolicy`
- `notifications`
- `notification_preferences`
- `policy_assignments`
- `roles`
- `scopes`
- `smtp_config`
- `Tenant`
- `tenant_api_mappings`
- `tenant_types`
- `Users`

### Events Observation

Events has better SQL coverage for its core incident/device domain than Tenants does, but still misses several supporting and shared tables, especially:
- user/tenant reference tables
- extension API key config tables
- event inbox table
- group/policy tables

---

## 4. Cross-Service Shared Tables That Look Externally Managed

These tables appear in multiple services and are strong candidates for being expected from a shared external bootstrap:

- `Users`
- `Tenant`
- `tenant_types`
- `tenant_api_mappings`
- `roles`
- `scopes`
- `groups`
- `auth_provider_config`
- `browserpolicy`
- `networkpolicy`
- `extension_policy`
- `policy_assignments`
- `events_groups`
- `events_groups_device_user_map`
- `device_user`
- `smtp_config`
- `api_flags`
- `country`
- `states`
- `cities`
- `region`

This is one of the main signs that the repo is missing part of the DB provisioning story.

---

## 5. What This Means For Local Setup

If you create a brand new empty PostgreSQL instance and run only the checked-in SQL files:

- some migration scripts will work
- some service-owned tables will exist
- but many entity-backed tables will still be missing

Possible consequences:
- app startup failure
- runtime query failure
- missing relation/table exceptions
- missing seed/master data

---

## 6. Recommended Next Steps

To make local setup truly reproducible, one of these should be done:

### Option 1
Find the missing original DB bootstrap SQL from your deployment or DevOps environment.

### Option 2
Enable and standardize a proper migration tool in code:
- Flyway
- Liquibase

### Option 3
Generate a consolidated bootstrap SQL file by combining:
- checked-in migration SQL
- entity metadata
- foreign key expectations
- seed data requirements

### Option 4
Check if a shared database dump exists outside this repo and document it in local setup.

---

## 7. Bottom Line

This repository contains enough information to understand:
- the services
- the environment variables
- the startup order
- some of the database schema

But it does **not** contain a fully complete, self-sufficient database bootstrap for all entity tables referenced by the code.

That is the core reason this report exists.
