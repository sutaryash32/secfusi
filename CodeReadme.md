# Secufusion Backend Code Architecture

This document explains the backend microservices in simple language based on the code present in this repository.

## Services At A Glance

| Service | Main Role |
|---|---|
| `sfn-eureka-api` | Service registry |
| `sfn-gateway-api` | API gateway / entry point |
| `sfn-iam-api` | Identity, access, features, subscriptions, SSO metadata |
| `sfn-tenants-api` | Tenant provisioning, tenant settings, policies, notifications |
| `sfn-events-api` | Devices, browser events, security events, incidents, analytics |

---

## 1. Eureka Service

### Service Name
`sfn-eureka-api`

### Purpose
This service is the service registry.

It keeps track of where all backend services are running. Instead of hardcoding IPs or URLs, other services register here and discover each other dynamically.

### Why It Exists
- Makes deployment easier
- Helps services find each other automatically
- Supports scaling and environment changes without changing code

### Tech Stack Used
- Spring Boot
- Netflix Eureka Server

### Communication
- Other services register themselves here
- Other services query it for discovery
- No business REST APIs or Kafka flows were found here

### Dependencies

#### Upstream
- `sfn-gateway-api`
- `sfn-iam-api`
- `sfn-tenants-api`
- `sfn-events-api`

#### Downstream
- None

### Database
- No database ownership found

### Flow Diagram
`Gateway / IAM / Tenants / Events -> Eureka (register + discover)`

---

## 2. API Gateway

### Service Name
`sfn-gateway-api`

### Purpose
This service is the single entry point for frontend clients and external callers.

It receives requests and routes them to the correct backend service:
- `/api/iam/**` -> IAM service
- `/api/tenants/**` -> Tenant service
- `/api/events/**` -> Events service

### Why It Exists
- Gives clients one stable API endpoint
- Centralizes routing
- Handles CORS and gateway-level filters
- Aggregates Swagger/OpenAPI docs

### Tech Stack Used
- Spring Boot
- Spring Cloud Gateway
- Eureka Client
- Resilience4j
- WebFlux

### Communication
- Client -> Gateway via REST/HTTP
- Gateway -> downstream services using Eureka load-balanced routing:
  - `lb://IAM-SERVICE`
  - `lb://TENANT-SERVICE`
  - `lb://EVENTS-SERVICE`
- No Kafka or RabbitMQ usage found here

### Dependencies

#### Upstream
- Frontend / Browser / External clients

#### Downstream
- `sfn-eureka-api`
- `sfn-iam-api`
- `sfn-tenants-api`
- `sfn-events-api`

### Database
- No database ownership found

### Flow Diagram
`Client -> Gateway -> IAM / Tenants / Events`

---

## 3. IAM Service

### Service Name
`sfn-iam-api`

### Purpose
This service handles identity and access management.

From the code, it manages:
- Users
- Roles
- Scopes
- Access levels
- Feature groups and features
- Packages and package-feature mapping
- Pricing and subscriptions
- SSO configuration
- Events groups
- Login audit
- Extension authentication for Azure tenants

### Why It Exists
This service exists to answer:
- Who can log in?
- What can they access?
- What package/features does a tenant have?
- How is SSO configured?
- Which Azure groups are allowed for app or extension access?

### Tech Stack Used
- Spring Boot
- Spring MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Eureka Client
- Kafka
- Keycloak Admin Client
- Microsoft Graph
- Azure Identity / Azure Core
- OpenFeature

### Communication
- Exposes REST APIs through Gateway
- Publishes Kafka messages to topic `device-registration`
- Talks to Keycloak Admin API
- Talks to Microsoft Graph / Azure APIs
- No Feign clients found
- No RabbitMQ found
- No clear direct internal REST calls to Tenants or Events found

### Dependencies

#### Upstream
- `sfn-gateway-api`
- Frontend/admin clients

#### Downstream
- Kafka topic `device-registration`
- Keycloak
- Azure / Microsoft Graph
- PostgreSQL
- Eureka

#### Called By
- Gateway

#### Calls / Feeds
- `sfn-events-api` indirectly through Kafka device registration events

### Database
This service owns IAM/admin/configuration-style tables. Based on entities and migration names, main owned tables include:

- `user`
- `roles`
- `scopes`
- `tenant`
- `tenant_type`
- `auth_provider_config`
- `sso_configuration`
- `sso_provider_url_config`
- `feature_group`
- `feature`
- `feature_type`
- `package`
- `package_type`
- `package_feature_mapping`
- `package_pricing`
- `addon_pricing`
- `tenant_addon_feature`
- `tenant_subscription`
- `events_group`
- `events_group_history`
- `policy_assignment`
- `login_audit_event`
- `device_user`
- `user_device_login`
- `access_level`
- `retention_period`
- `billing_cycle`
- lookup tables like `country`, `states`, `cities`, `region`, `industry`

### Flow Diagram
`Client -> Gateway -> IAM -> PostgreSQL`

`IAM -> Keycloak`

`IAM -> Azure Graph`

`IAM -> Kafka(device-registration) -> Events`

---

## 4. Tenant Service

### Service Name
`sfn-tenants-api`

### Purpose
This service handles tenant lifecycle and tenant-specific configuration.

From the code, it manages:
- Tenant creation and updates
- Tenant provisioning
- Keycloak realm setup
- Session management
- Browser policies
- Network policies
- Extension policies
- Policy assignments
- Notifications
- SMTP config
- Webhooks
- Landing pages / homepage config
- API keys and extension details
- Audit logs
- Login audit

### Why It Exists
This service exists to manage each customer organization after onboarding:
- Provision the tenant
- Configure identity and SSO
- Manage policies enforced on users/devices/extensions
- Maintain tenant-specific settings and operational workflows

### Tech Stack Used
- Spring Boot
- Spring MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Eureka Client
- Kafka
- WebSocket / STOMP
- Keycloak Admin Client
- RestTemplate
- OpenFeature
- Async + Scheduling

### Communication
- Exposes REST APIs through Gateway
- Publishes Kafka events to topic `policy-events`
- Uses WebSocket/STOMP publishers for realtime updates
- Talks heavily to Keycloak for tenant provisioning and session operations
- No Feign clients found
- No RabbitMQ found
- No clear synchronous internal REST calls to IAM or Events found

### Dependencies

#### Upstream
- `sfn-gateway-api`
- Frontend/admin clients

#### Downstream
- Kafka topic `policy-events`
- Keycloak
- PostgreSQL
- WebSocket clients
- Eureka

#### Called By
- Gateway

#### Calls / Feeds
- Mostly external systems rather than other internal services

### Database
This service owns tenant-operations and policy-related tables. Main owned tables include:

- `tenant`
- `tenant_subscription`
- `subscription_package`
- `subscription_history`
- `user`
- `browser_policy`
- `network_policy`
- `extension_policy`
- `policy_assignment`
- `events_group`
- `extension_api_key`
- `extension_detail`
- `managed_extension`
- `browser_device`
- `browser_event`
- `landing_page`
- `homepage`
- `compliance_rules`
- `dlp`
- `network_configuration`
- `notification`
- `notification_preference`
- `smtp_config`
- `webhook_config`
- `webhook_delivery_log`
- `audit_log`
- `login_audit_event`
- `shortcut`
- `url_filter`
- `watermarking`
- `auth_provider_config`
- supporting lookup/config tables like `country`, `states`, `cities`, `region`, `roles`, `scopes`

### Flow Diagram
`Client -> Gateway -> Tenants -> PostgreSQL`

`Tenants -> Keycloak`

`Tenants -> Kafka(policy-events)`

`Tenants -> WebSocket clients`

---

## 5. Events Service

### Service Name
`sfn-events-api`

### Purpose
This service handles security telemetry and SecOps workflows.

From the code, it manages:
- Devices
- Device users
- Browser events
- Security events
- Extension events
- Installed extensions
- Incidents
- Incident assignees and activity
- Playbooks
- Escalation rules
- Event ingestion inbox
- Extension API key management
- Analytics and activity summaries

### Why It Exists
This service exists to store and process operational security data:
- Track browser/device activity
- Store security events
- Generate incident workflows
- Power dashboards and analytics
- Manage extension-side security telemetry

### Tech Stack Used
- Spring Boot
- Spring MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Eureka Client
- Kafka producer and consumer
- Caching
- Scheduling
- Keycloak Admin Client
- Mail support

### Communication
- Exposes REST APIs through Gateway
- Consumes Kafka topic `device-registration` from IAM
- Produces to Kafka topic `quickstart-events`
- Consumes from Kafka topic `quickstart-events`
- Talks to Keycloak for extension API key/client setup and token generation
- Sends notifications/emails in incident flows
- No Feign clients found
- No RabbitMQ found

### Dependencies

#### Upstream
- `sfn-gateway-api`
- Browser extension / clients
- `sfn-iam-api` through Kafka

#### Downstream
- Kafka topic `device-registration`
- Kafka topic `quickstart-events`
- PostgreSQL
- Keycloak
- Email
- Eureka

#### Called By
- Gateway
- Kafka messages from IAM

#### Calls / Feeds
- Self-managed Kafka event pipeline inside Events service

### Database
This service owns telemetry, devices, events, incidents, and playbook tables. Main ones include:

- `device`
- `device_user`
- `event`
- `event_inbox`
- `extension_event`
- `installed_extension`
- `extension_api_key`
- `extension_api_key_configuration`
- `extension_api_key_rotation_history`
- `incident`
- `incident_activity`
- `incident_assignee`
- `incident_event`
- `incident_playbook`
- `incident_playbook_step`
- `playbook_template`
- `escalation_rule`
- `notification`
- `notification_preference`
- `browser_policy`
- `network_policy`
- `extension_policy`
- `events_group`
- `policy_assignment`
- `tenant`
- `user`
- `smtp_config`

Migration files clearly show ownership of:
- device tracking
- event storage
- event aggregation views
- incident tables
- playbook tables
- escalation tables
- extension tracking tables

### Flow Diagram
`Client / Extension -> Gateway -> Events -> PostgreSQL`

`IAM -> Kafka(device-registration) -> Events`

`Events -> Kafka(quickstart-events) -> Events inbox`

`Events -> Keycloak / Email`

---

## Overall System Architecture Summary

This backend is a Spring Boot microservice system with five major parts:

1. `Eureka` for service discovery
2. `Gateway` for routing external requests
3. `IAM` for identity, access, packages, and SSO metadata
4. `Tenants` for tenant provisioning and tenant-level policy/configuration
5. `Events` for telemetry, devices, incidents, and analytics

### Architectural Style
- API Gateway pattern for ingress
- Service discovery using Eureka
- Separate bounded services with their own local domain tables
- Kafka used for async integration where needed
- Keycloak is a major shared external dependency
- Azure/Microsoft Graph is used mainly for SSO and group sync flows

### Communication Patterns

This codebase uses two different communication styles:

#### 1. Synchronous communication
- Done through the API Gateway
- The Gateway uses Eureka service discovery and routes requests using:
  - `lb://IAM-SERVICE`
  - `lb://TENANT-SERVICE`
  - `lb://EVENTS-SERVICE`
- So the most common runtime request path is:

`Client -> Gateway -> Target Service`

#### 2. Asynchronous communication
- Done through Kafka
- Used for event-driven and background processing, not for every request
- Main Kafka flows found in code:
  - `IAM -> device-registration -> Events`
  - `Events -> quickstart-events -> Events consumer`
  - `Tenants -> policy-events`

### Important Clarification

Even though the project has a Eureka Discovery Server, it does not use OpenFeign for business-service calls.

That means Eureka is mainly being used for:
- service registration
- service discovery
- Gateway routing

It is not being used here as proof of direct `IAM -> Tenants` or `Tenants -> Events` REST calls.

### Important Observation
The code does not show heavy synchronous REST calls between business services.

Instead, the services mostly:
- expose their own APIs
- use their own local persistence
- integrate through Kafka and shared infrastructure like Keycloak

So this is not a tightly coupled REST mesh. It is closer to a domain-separated architecture with local read models.

---

## Complete Service Communication Map

### External Request Path
`Client/UI -> Gateway -> IAM`

`Client/UI -> Gateway -> Tenants`

`Client/UI -> Gateway -> Events`

### Synchronous Internal Routing
`Gateway -> IAM`

`Gateway -> Tenants`

`Gateway -> Events`

### Service Discovery
`Gateway -> Eureka`

`IAM -> Eureka`

`Tenants -> Eureka`

`Events -> Eureka`

### Infrastructure / Async Communication
`IAM -> Keycloak`

`IAM -> Azure Graph`

`IAM -> Kafka(device-registration) -> Events`

`Tenants -> Keycloak`

`Tenants -> Kafka(policy-events)`

`Tenants -> WebSocket clients`

`Events -> Keycloak`

`Events -> Kafka(quickstart-events) -> Events`

`Events -> Email`

---

## End-To-End Request Flow

## A. Normal API Request Flow

1. Client sends request to the Gateway.
2. Gateway checks the path and routes the request to the correct service.
3. The target service validates JWT/security using Keycloak-backed configuration.
4. The service executes business logic.
5. The service reads/writes its own PostgreSQL tables.
6. If needed, the service also talks to external systems like Keycloak, Azure, Kafka, or email.
7. Response returns through the Gateway to the client.

### Simple Diagram
`Client -> Gateway -> Target Service -> DB / External System -> Gateway -> Client`

---

## B. Device Registration / Extension Activity Flow

1. User or extension authenticates through the platform.
2. IAM creates a device registration event when relevant login/device data is available.
3. IAM publishes that event to Kafka topic `device-registration`.
4. Events service consumes the Kafka message.
5. Events service updates or creates device records in its own database.

### Diagram
`Extension / User Login -> IAM -> Kafka(device-registration) -> Events -> Devices Table`

---

## C. Security Event Ingestion Flow

1. Events service receives event data through its APIs.
2. Events service publishes the event to Kafka topic `quickstart-events`.
3. Events service consumes the event from Kafka.
4. The event is persisted into the event inbox / event processing store.
5. Incident, analytics, dashboard, and security workflows use that stored data.

### Diagram
`Client / Extension -> Events API -> Kafka(quickstart-events) -> Events Consumer -> Event Inbox / Event Tables`

---

## D. Tenant Provisioning Flow

1. Admin creates a tenant through Tenant service APIs.
2. Tenant service stores tenant metadata in its DB.
3. Tenant service calls Keycloak Admin APIs to create/configure realm, clients, users, and SSO setup.
4. Tenant service may initialize default tenant configuration and policies.
5. Tenant becomes available for login and management.

### Diagram
`Admin -> Gateway -> Tenants -> PostgreSQL + Keycloak -> Tenant Ready`

---

## Final Summary

In simple terms:

- `Gateway` is the front door
- `Eureka` is the directory
- `IAM` controls identity, access, and feature entitlements
- `Tenants` controls organization setup and policy configuration
- `Events` controls devices, telemetry, alerts, incidents, and analytics

The most important real integrations in code are:
- Gateway routing via Eureka
- Keycloak integration across IAM, Tenants, and Events
- Azure Graph integration in IAM
- Kafka-based device registration from IAM to Events
- Kafka-based event ingestion inside Events

So the platform is using:
- Gateway + Eureka for synchronous request routing
- Kafka for asynchronous internal event communication

---

## Notes

- Database names and schema names are environment-driven through properties, so physical DB separation depends on deployment configuration.
- The ownership listed above reflects logical ownership from entities, migrations, and service responsibilities in code.
