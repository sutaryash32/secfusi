# Introduction

This service is a Spring Boot application that implements a complete "I AM" (Identity and Access Management) flow. It bootstraps a default tenant and users on startup, manages users, groups, roles and scopes, integrates with Keycloak for authentication/authorization, exposes REST controllers with Swagger documentation, and provides utilities and logging for JWT and Keycloak operations.

# Features

1. Generates a default tenant and default users on startup.
2. Full user, group, role and scope management (CRUD + assignments).
3. Integrated Keycloak administration for realm, client and role sync.
4. Swagger \(/OpenAPI\) UI for every controller.
5. Centralized logging and util classes for JWT and Keycloak interactions.
6. Exception handling via custom exceptions and a global handler.
7. Default and overrideable values provided in `application.properties`.

# Detailed component overview

- Controllers
  1. `TenantController` — `createTenant`, `getTenant`, `updateTenant`, `deleteTenant`, `listTenants`.
  2. `UserController` — `createUser`, `getUser`, `updateUser`, `deleteUser`, `searchUsers`, `assignRoles`, `assignGroups`.
  3. `GroupController` — `createGroup`, `getGroup`, `updateGroup`, `deleteGroup`, `addUserToGroup`, `removeUserFromGroup`.
  4. `RoleController` — `createRole`, `getRole`, `updateRole`, `deleteRole`, `assignRoleToUser`, `assignRoleToGroup`.
  5. `ScopeController` — `createScope`, `getScope`, `updateScope`, `deleteScope`, `listScopes`.

- Services
  1. `TenantService` — bootstrap and tenant lifecycle management.
  2. `UserService` — user CRUD, password handling, role/group assignments and searches.
  3. `GroupService` — group CRUD and membership management.
  4. `RoleService` — role CRUD and assignment logic.
  5. `ScopeService` — scope CRUD and validation.
  6. `KeycloakAdminService` — realm/client/role sync, remote user provisioning and reconciliation.
  7. `BootstrapService` — startup listener that creates the default tenant and default users.

- Utilities & Security
  1. `JwtUtil` — token parsing, validation and claim extraction (`parseToken`, `validateToken`, `getClaims`).
  2. `KeycloakUtil` — helper wrappers for Keycloak REST/Admin calls.
  3. `SecurityConfig` — Spring Security configuration, mapping Keycloak roles/scopes to authorities.
  4. `SwaggerConfig` — OpenAPI setup for controller documentation.

- Error handling & logging
  1. `GlobalExceptionHandler` (`@ControllerAdvice`) — maps `ResourceNotFoundException`, `BadRequestException`, `ConflictException` and others to consistent HTTP responses.
  2. Centralized logging via configured `logback`/`log4j2` with contextual logs in controllers and services.

# Project setup

Prerequisites (Windows):
1. Java JDK 17\+ installed and `JAVA_HOME` set.
2. Maven 3.6\+ installed.
3. Running Keycloak server (tested with Keycloak 21\+). Example: run Keycloak locally or use a container.
4. Optional: PostgreSQL or other supported DB if not using in-memory DB.

Configuration steps:
1. Clone repository and open in `IntelliJ IDEA 2025.2.4`.
2. Edit `src/main/resources/application.properties` (or `application.yml`) and set Keycloak and DB properties. Example keys:
   - `keycloak.server-url`
   - `keycloak.realm`
   - `keycloak.resource` (client id)
   - `keycloak.credentials.secret`
   - `app.default-tenant.id`
   - `app.default-tenant.name`
   - `app.default-user.username`
   - `app.default-user.password`
   - `spring.datasource.*`
3. Ensure Keycloak has a management client configured with the provided credentials, or allow the app to bootstrap required clients/roles if enabled.

Build and run (Windows CMD or PowerShell):
1. Build:
   - mvn clean package
2. Run via Spring Boot:
   - mvn spring-boot:run
   - or: java -jar target\your-app-name.jar
3. Application will attempt to connect to Keycloak and create default tenant/users and required realm/client/roles on first startup.

# Endpoints & API docs

- Swagger UI (OpenAPI): `http://localhost:8080/swagger-ui/index.html`
- Health and actuator endpoints (if enabled): `http://localhost:8080/actuator/health`

# Exception handling

1. Custom exceptions included (examples):
   - `ResourceNotFoundException`
   - `BadRequestException`
   - `ConflictException`
2. Global exception handling implemented via `@ControllerAdvice` (e.g. `GlobalExceptionHandler`) to map exceptions to HTTP responses and consistent error payloads.
3. Default values and error messages can be configured in `application.properties` to avoid hardcoded strings.

# Key components

1. Bootstrapping:
   - Startup listener/service that creates a default tenant and default users using values from `application.properties`.
2. Keycloak integration:
   - `KeycloakAdminService` for creating realms, clients, roles and synchronizing application users with Keycloak.
3. Security & JWT:
   - JWT utilities to parse/validate tokens and extract claims.
   - Spring Security configuration to secure endpoints and map Keycloak roles/scopes to authorities.
4. Controllers:
   - REST controllers for tenants, users, groups, roles and scopes. Each controller is documented with Swagger annotations.
5. Logging:
   - Centralized logging configuration (logback or log4j2). Controllers and services log key operations and errors.
6. Utilities:
   - Reusable util classes for Keycloak operations, token handling and common response builders.

# Properties (recommended keys)

Add or verify the following in `src/main/resources/application.properties`:
- `keycloak.server-url=http://localhost:8080`
- `keycloak.realm=master`
- `keycloak.resource=admin-cli`
- `keycloak.credentials.secret=<secret>`
- `app.default-tenant.id=default-tenant`
- `app.default-tenant.name=Default Tenant`
- `app.default-user.username=admin`
- `app.default-user.password=admin`
- `spring.datasource.url=jdbc:postgresql://localhost:5432/iamdb`
- `spring.datasource.username=...`
- `spring.datasource.password=...`
- `spring.profiles.active=dev`

# Tests

1. Unit tests: `mvn test`
2. Integration tests may require a running Keycloak instance or can use a Keycloak testcontainer.

# Logs & troubleshooting

1. Check application logs for bootstrap progress (tenant/user creation and Keycloak sync).
2. Common issues:
   - Invalid Keycloak credentials → verify `keycloak.credentials.secret` and client configuration.
   - DB connection errors → verify `spring.datasource.*` properties.
   - Port conflicts → change `server.port` in properties.

# Contributing

1. Follow the existing code style and tests.
2. Add controller-level Swagger annotations for new endpoints.
3. Update the bootstrap and Keycloak sync logic when adding new resource types.
4. Run full test suite before creating PR.

# License & references

Refer to project root for license. For Keycloak integration examples and Swagger setup consult:
1. Keycloak docs: https://www.keycloak.org
2. Springdoc OpenAPI / Swagger for Spring Boot.