# Secufusion Local Setup Guide

This document explains how to run the backend locally from the code present in this repository.

It is based on:
- service `pom.xml` files
- `application.properties`
- checked-in SQL migration files
- service startup classes
- Kafka and Eureka configuration found in code

---

## 1. What This Application Does

Secufusion is a multi-tenant browser security platform backend.

The backend supports:
- tenant onboarding and provisioning
- identity and access management
- SSO integration
- browser, extension, and network policy management
- device and browser telemetry
- incidents, alerts, and playbooks
- tenant dashboards and operational settings

---

## 2. Microservices And Responsibilities

## 2.1 `sfn-eureka-api`

Purpose:
- Service registry for backend services

Responsibility:
- lets other services register and discover each other

Runs on:
- `8761` from `application.yml`

---

## 2.2 `sfn-gateway-api`

Purpose:
- single entry point for frontend and clients

Responsibility:
- routes requests to:
  - IAM
  - Tenants
  - Events
- exposes aggregated swagger routes

Gateway routes found in code:
- `/api/iam/**`
- `/api/tenants/**`
- `/api/events/**`

---

## 2.3 `sfn-iam-api`

Purpose:
- identity and access management domain

Responsibility:
- users
- roles
- scopes
- access levels
- features and feature groups
- packages and pricing
- subscriptions
- SSO configuration
- login audit
- Azure group integration
- extension auth checks

Communication:
- REST via gateway
- Kafka producer for `device-registration`
- Keycloak admin calls
- Azure / Microsoft Graph calls

---

## 2.4 `sfn-tenants-api`

Purpose:
- tenant lifecycle and tenant-level operational configuration

Responsibility:
- tenant creation and update
- tenant provisioning
- Keycloak realm setup
- session management
- browser policies
- network policies
- extension policies
- notifications
- SMTP config
- audit logs
- webhooks
- landing pages

Communication:
- REST via gateway
- Kafka producer for `policy-events`
- WebSocket/STOMP publishing
- Keycloak admin calls

---

## 2.5 `sfn-events-api`

Purpose:
- telemetry and security operations domain

Responsibility:
- devices
- device users
- browser events
- security events
- extension events
- incidents
- incident assignees
- playbooks
- escalations
- extension API keys
- analytics

Communication:
- REST via gateway
- Kafka consumer for `device-registration`
- Kafka producer/consumer for `quickstart-events`
- Keycloak integration
- email/notification support

---

## 3. How Services Communicate

There are two communication patterns in this codebase.

## 3.1 Synchronous Communication

Main request flow:

`Client -> Gateway -> Target Service`

Gateway uses Eureka discovery and routes by service name:
- `lb://IAM-SERVICE`
- `lb://TENANT-SERVICE`
- `lb://EVENTS-SERVICE`

So Eureka is mainly used for:
- service registration
- service discovery
- gateway routing

This repo does **not** show OpenFeign-based service-to-service business calls.

## 3.2 Asynchronous Communication

Kafka topics found in code:
- `device-registration`
- `quickstart-events`
- `policy-events`

Main async flows:
- `IAM -> Kafka(device-registration) -> Events`
- `Events -> Kafka(quickstart-events) -> Events consumer`
- `Tenants -> Kafka(policy-events)`

---

## 4. Prerequisites

Install these before running locally:

- Java 17
- PostgreSQL
- Apache Kafka
- Keycloak
- Maven, or use the included `mvnw.cmd`

Not required from this repo:
- Node.js
- Docker Compose

---

## 5. Databases Needed

Create these PostgreSQL databases:

- `secufusion_iam`
- `secufusion_tenants`
- `secufusion_events`

Services without databases:
- `sfn-eureka-api`
- `sfn-gateway-api`

### SQL To Create Databases

```sql
CREATE DATABASE secufusion_iam;
CREATE DATABASE secufusion_tenants;
CREATE DATABASE secufusion_events;
```

### Schemas

No explicit custom PostgreSQL schema is configured in code.

Use the default schema:
- `public`

---

## 6. Environment Variables

Set these in PowerShell before starting services.

```powershell
$env:EUREKA_DEFAULT_ZONE="http://localhost:8761/eureka"

$env:GATEWAY_SERVICE_NAME="GATEWAY-SERVICE"
$env:GATEWAY_SERVICE_PORT="8080"
$env:IAM_SERVICE_URI="http://localhost:8081"
$env:TENANT_SERVICE_URI="http://localhost:8082"
$env:EVENTS_SERVICE_URI="http://localhost:8083"

$env:IAM_SERVICE_NAME="IAM-SERVICE"
$env:IAM_SERVICE_PORT="8081"
$env:IAM_CONTEXT_PATH="/api/iam"

$env:TENANT_SERVICE_NAME="TENANT-SERVICE"
$env:TENANT_SERVICE_PORT="8082"

$env:EVENTS_SERVICE_NAME="EVENTS-SERVICE"
$env:EVENTS_SERVICE_PORT="8083"

$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres"

$env:KAFKA_SERVER="localhost:9092"
$env:KAFKA_SCHEDULE_TIME="60000"

$env:KC_ADMIN_SERVER_URL="http://localhost:8084"
$env:KC_ADMIN_REALM="master"
$env:KC_ADMIN_CLIENT_ID="admin-cli"
$env:KC_ADMIN_USERNAME="admin"
$env:KC_ADMIN_PASSWORD="admin"

$env:REALM_URL="http://localhost:8084/realms"
$env:REDIRECT_URL="http://localhost:3000"
$env:JWK_SET_URI="http://localhost:8084/realms/master/protocol/openid-connect/certs"

$env:SMTP_HOST="localhost"
$env:SMTP_PORT="1025"
$env:SMTP_AUTH="false"
$env:SMTP_STARTTLS="false"
$env:SMTP_USERNAME=""
$env:SMTP_PASSWORD=""
$env:SMTP_MAIL="noreply@example.com"

$env:TENANT_CLIENT_ID="tenant-client"
$env:TENANT_CLIENT_SECRET="tenant-secret"
$env:DOMAIN_EXTENSION=".local"

$env:MASTER_ADMIN_USERNAME="softwareadmin"
$env:MASTER_ADMIN_PASSWORD="Admin@123"
$env:MASTER_ADMIN_FIRSTNAME="Software"
$env:MASTER_ADMIN_LASTNAME="Admin"
$env:MASTER_ADMIN_DOMAIN="local"
$env:MASTER_ADMIN_EMAIL="softwareadmin@example.com"
$env:MASTER_ADMIN_PHONE="9999999999"
$env:MASTER_ADMIN_TENANT_NAME="Master"

$env:HIKARI_MAX_LIFETIME="1800000"
$env:HIKARI_CONNECTION_TIMEOUT="30000"
$env:HIKARI_IDLE_TIMEOUT="600000"
$env:HIKARI_MAX_POOL_SIZE="10"
$env:HIKARI_MIN_IDLE="2"
$env:HIKARI_KEEPALIVE_TIME="300000"
$env:HIKARI_POOL_NAME="SecufusionPool"
$env:HIKARI_LEAK_DETECTION_THRESHOLD="0"

$env:AZURE_CLIENT_ID="dummy-client-id"
$env:AZURE_CLIENT_SECRET="dummy-client-secret"
$env:AZURE_MISMATCH_VALIDATION="false"
$env:AZURE_SSO_ALIAS="azure-sso"

$env:file_path=""
```

### DB URL Per Service

Before starting each DB-backed service, set its DB URL.

IAM:
```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/secufusion_iam"
```

Tenants:
```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/secufusion_tenants"
```

Events:
```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/secufusion_events"
```

---

## 7. Database SQL Present In Repo

The repository contains checked-in SQL migration files under:

- `sfn-iam-api/src/main/resources/db/migration`
- `sfn-tenants-api/src/main/resources/db/migration`
- `sfn-events-api/src/main/resources/db/migration`

There is **no** Flyway or Liquibase dependency configured in the service `pom.xml` files.

That means these SQL files should be treated as manual schema setup scripts unless your environment injects extra migration logic externally.

### 7.1 IAM SQL Files

```text
V1__create_login_audit_event_table.sql
V3__create_feature_group_table.sql
V4__create_access_level_table.sql
V5__create_retention_period_table.sql
V6__create_package_type_table.sql
V7__create_package_table.sql
V8__create_feature_types_table.sql
V9__create_features_table.sql
V10__create_package_feature_mapping_table.sql
V21__create_billing_cycle_table.sql
V22__create_tenant_addon_feature_table.sql
V23__create_package_pricing_table.sql
V24__create_tenant_subscription_table.sql
V26__create_addon_pricing_table.sql
V28__add_device_tracking_columns_and_table.sql
V29__create_events_group_history_table.sql
```

Tables directly created by checked-in IAM SQL:
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

### 7.2 Tenants SQL Files

```text
V1__create_login_audit_event_table.sql
V3__create_subscription_history_table.sql
V11__create_notifications_table.sql
V12__create_notification_preferences_table.sql
V13__create_webhook_tables.sql
V15__cleanup_redundant_indexes_and_add_audit_log.sql
```

Tables directly created by checked-in Tenants SQL:
- `login_audit_event`
- `subscription_history`
- `notifications`
- `notification_preferences`
- `webhook_configs`
- `webhook_delivery_logs`
- `audit_log`

### 7.3 Events SQL Files

```text
V1__create_devices_table.sql
V10__create_incident_tables.sql
V11__create_incident_assignees_table.sql
V13__create_playbook_tables.sql
V14__create_escalation_rules_table.sql
V8__extension_tracking_system.sql
```

Tables directly created by checked-in Events SQL:
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

---

## 8. JPA Tables Referenced By Code

These are the table names explicitly referenced by entity classes using `@Table(name=...)`.

### 8.1 IAM Entity Tables

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

### 8.2 Tenants Entity Tables

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

### 8.3 Events Entity Tables

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

---

## 9. Important Gap In Repo

This repository does **not** contain a single complete bootstrap SQL set for every JPA entity table.

That means:
- the migration SQL is only partial
- some entity tables are referenced in code without checked-in `CREATE TABLE` scripts
- no Flyway/Liquibase dependency is wired in the service builds

So for a true clean-room first-time setup, one of the following must exist outside this repo:
- a pre-existing database dump
- external SQL files not committed here
- environment-specific JPA auto-DDL settings
- deployment-time migration tooling not present in this repo

### Exact Conclusion

You can use the checked-in SQL files as the starting point, but the repo alone does **not** guarantee a full database bootstrap on an empty PostgreSQL instance.

---

## 10. How To Run The SQL Files

### 10.1 Create Databases

```powershell
psql -U postgres -d postgres -c "CREATE DATABASE secufusion_iam;"
psql -U postgres -d postgres -c "CREATE DATABASE secufusion_tenants;"
psql -U postgres -d postgres -c "CREATE DATABASE secufusion_events;"
```

### 10.2 Run IAM SQL

```powershell
Get-ChildItem "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-iam-api 4\sfn-iam-api\src\main\resources\db\migration" -File |
Sort-Object Name |
ForEach-Object { psql -U postgres -d secufusion_iam -f $_.FullName }
```

### 10.3 Run Tenants SQL

```powershell
Get-ChildItem "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-tenants-api 3\sfn-tenants-api\src\main\resources\db\migration" -File |
Sort-Object Name |
ForEach-Object { psql -U postgres -d secufusion_tenants -f $_.FullName }
```

### 10.4 Run Events SQL

```powershell
Get-ChildItem "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-events-api 2\sfn-events-api\src\main\resources\db\migration" -File |
Sort-Object Name |
ForEach-Object { psql -U postgres -d secufusion_events -f $_.FullName }
```

---

## 11. Service Startup Order

Start services and dependencies in this exact order:

1. PostgreSQL
2. Kafka
3. Keycloak
4. Create PostgreSQL databases
5. Run SQL scripts
6. `sfn-eureka-api`
7. `sfn-iam-api`
8. `sfn-tenants-api`
9. `sfn-events-api`
10. `sfn-gateway-api`

Reason:
- DB-backed services need PostgreSQL first
- Kafka-backed services need Kafka first
- IAM, Tenants, and Events depend on Keycloak config
- Gateway should start after downstream services are available

---

## 12. Exact Commands To Start Each Service

## 12.1 Eureka

```powershell
cd "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-eureka-api 3\sfn-eureka-api"
.\mvnw.cmd spring-boot:run
```

Expected URL:
- `http://localhost:8761`

## 12.2 IAM

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/secufusion_iam"
cd "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-iam-api 4\sfn-iam-api"
.\mvnw.cmd spring-boot:run
```

## 12.3 Tenants

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/secufusion_tenants"
cd "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-tenants-api 3\sfn-tenants-api"
.\mvnw.cmd spring-boot:run
```

## 12.4 Events

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/secufusion_events"
cd "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-events-api 2\sfn-events-api"
.\mvnw.cmd spring-boot:run
```

## 12.5 Gateway

```powershell
cd "C:\Users\Yash\OneDrive - Motivity Labs\Desktop\Secufusionn\sfn-gateway-api\sfn-gateway-api"
.\mvnw.cmd spring-boot:run
```

---

## 13. Local URLs

Eureka:
- `http://localhost:8761`

Gateway:
- `http://localhost:8080`

IAM through gateway:
- `http://localhost:8080/api/iam`

Tenants through gateway:
- `http://localhost:8080/api/tenants`

Events through gateway:
- `http://localhost:8080/api/events`

---

## 14. Docker / Docker Compose

No `Dockerfile`, `docker-compose.yml`, `compose.yml`, or equivalent runtime container config was found in this repository.

So there is no checked-in one-command Docker startup available here.

---

## 15. Minimal Working Local Checklist

Before you start the services, verify:

- Java 17 installed
- PostgreSQL running
- Kafka running on `localhost:9092`
- Keycloak running on `http://localhost:8084`
- databases created
- SQL migration files executed
- environment variables set in current PowerShell session

---

## 16. Final Practical Note

This repo is sufficient to document:
- service boundaries
- startup order
- environment variables
- checked-in migration SQL

But it is not sufficient by itself to guarantee a first-time empty-environment bootstrap because the full DB creation scripts are not fully present in the repository.

If you want a truly reproducible local bootstrap next, the next useful step is:
- generate a consolidated SQL bootstrap from the entities and checked-in migrations
- or identify the missing external schema dump/config from your deployment environment
