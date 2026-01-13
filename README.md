# Secufusion IAM API

A comprehensive Identity and Access Management (IAM) microservice built with Spring Boot 3.3.4, providing enterprise-grade authentication, authorization, and multi-tenant support with seamless integration to Keycloak and Azure Active Directory.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Coding Standards](#coding-standards)
- [External Integrations](#external-integrations)
- [Security](#security)
- [Development Guidelines](#development-guidelines)

---

## Overview

The Secufusion IAM API is a production-ready microservice designed to handle:

- **Multi-tenant Identity Management** - Isolated tenant environments with per-tenant authentication
- **Role-Based Access Control (RBAC)** - Hierarchical permission model (Users → Groups → Roles → Scopes)
- **Federated Authentication** - Integration with Azure AD, OIDC, SAML providers via Keycloak
- **Dynamic Feature Flags** - Per-tenant API enablement using OpenFeature SDK
- **Azure AD Synchronization** - Automatic mapping of Azure App Roles and Security Groups

### Built For

- Enterprise SaaS applications requiring tenant isolation
- Organizations using Keycloak for centralized authentication
- Systems integrating with Azure Active Directory for federated identity
- Applications needing fine-grained permission control

---

## Key Features

### 1. User Management
- Create, read, update, delete users with automatic Keycloak provisioning
- User validation (uniqueness checks for username, email, phone)
- Resend verification emails
- User-to-Group mapping with transactional consistency
- Multi-tenant user isolation

### 2. Role-Based Access Control (RBAC)
- Create and manage roles with scope assignments
- Default and super role designation
- Activate/deactivate roles
- Hierarchical permission model: `User → Groups → Roles → Scopes`

### 3. Group Management
- Create user groups with multiple role assignments
- Admin and default group flags
- Tenant-scoped groups for isolation

### 4. OAuth Scopes & Permissions
- Define application scopes with menu/submenu organization
- Multi-tenant scope assignment via tenant types
- Scope discovery by menu and submenu
- Fine-grained access control

### 5. Single Sign-On (SSO) Configuration
- Support for multiple identity providers:
  - Azure AD (OpenID Connect)
  - Generic OIDC providers
  - SAML 2.0 providers
- Dynamic SSO provider activation/deactivation
- Azure App Role and Security Group integration via Microsoft Graph API
- Automatic attribute mapping to Keycloak JWT tokens

### 6. Feature Flags & API Control
- Database-backed feature flags using OpenFeature SDK
- Per-tenant API enablement/disablement
- Feature flag caching with refresh mechanisms
- Safe deployment with gradual rollout capabilities

### 7. Multi-Tenant Architecture
- Complete tenant isolation at data and authentication levels
- Support for parent-child tenant relationships
- Domain-based tenant resolution
- Per-tenant Keycloak realms

---

## Technology Stack

### Core Framework
```
├── Spring Boot 3.3.4          # Application framework
├── Spring Cloud 2023.0.3      # Cloud-native patterns
├── Java 17                    # Programming language
└── Maven                      # Build tool
```

### Spring Ecosystem
```
├── Spring Boot Starter Web              # REST API framework
├── Spring Boot Starter Data JPA         # ORM and database access
├── Spring Boot Starter Security         # Authentication & authorization
├── Spring Boot Starter OAuth2 Resource Server  # JWT validation
├── Spring Boot Starter Validation       # Input validation
├── Spring Boot Starter Actuator         # Monitoring and health checks
└── Spring Cloud Netflix Eureka Client   # Service discovery
```

### Authentication & Identity
```
├── Keycloak Admin Client 26.0.5    # Programmatic realm management
├── Azure Identity 1.14.0           # Azure service principal auth
└── Microsoft Graph SDK 6.19.0      # Azure AD integration
```

### Database
```
└── PostgreSQL                      # Primary data store
```

### API Documentation
```
└── SpringDoc OpenAPI 2.5.0        # Swagger UI & OpenAPI 3.0 spec
```

### Feature Management
```
└── OpenFeature SDK 1.18.2         # Unified feature flag interface
```

### Developer Tools
```
└── Lombok                         # Boilerplate code reduction
```

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Applications                      │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ JWT Bearer Token
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                     IAM API (This Service)                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Filters: JWT Validation → Tenant Validation → Feature    │  │
│  │           Flag Evaluation                                 │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Controllers: Users, Roles, Groups, Scopes, SSO, etc.     │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Services: Business Logic & Transaction Management        │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Repositories: Spring Data JPA Repositories               │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────┬──────────────────────────┬─────────────────────────┘
             │                          │
             │                          │
             ▼                          ▼
┌─────────────────────────┐  ┌──────────────────────────┐
│     PostgreSQL DB        │  │   Keycloak Server        │
│  - Tenants               │  │  - Multi-realm setup     │
│  - Users                 │  │  - JWT token issuance    │
│  - Roles/Groups/Scopes   │  │  - Identity providers    │
│  - SSO Configurations    │  │  - User federation       │
│  - Feature Flags         │  └──────────┬───────────────┘
└──────────────────────────┘             │
                                         │
                                         ▼
                              ┌──────────────────────────┐
                              │   Azure Active Directory │
                              │  - Federated auth        │
                              │  - App Roles             │
                              │  - Security Groups       │
                              └──────────────────────────┘
```

### Authentication Flow

```
1. User → Login Request → Keycloak
2. Keycloak → Validates Credentials (Local or Federated via Azure AD)
3. Keycloak → Issues JWT Token
4. User → API Request with JWT Bearer Token
5. IAM API → Validates JWT signature using tenant-specific JWK Set URI
6. IAM API → Extracts tenant and user from JWT claims
7. IAM API → Validates tenant and user status (ACTIVE)
8. IAM API → Resolves user scopes: User → Groups → Roles → Scopes
9. IAM API → Evaluates feature flags for the requested endpoint
10. IAM API → Grants/Denies access based on scopes and flags
```

---

## Project Structure

```
sfn-iam-api/
├── src/main/java/com/secufusion/iam/
│   ├── config/                          # Configuration Classes
│   │   ├── CustomJwtAuthenticationConverter.java
│   │   ├── DbJwtAuthenticationManagerResolver.java
│   │   ├── KeycloakAdminConfig.java
│   │   ├── MultitenantDataSourceConfig.java
│   │   ├── SecurityConfig.java
│   │   └── SwaggerConfig.java
│   │
│   ├── controller/                      # REST API Endpoints
│   │   ├── ApiAdminFlagController.java
│   │   ├── AuthenticationController.java
│   │   ├── DropdownsController.java
│   │   ├── EventController.java
│   │   ├── FeaturesController.java
│   │   ├── GroupsController.java
│   │   ├── PackagesController.java
│   │   ├── RolesController.java
│   │   ├── ScopesController.java
│   │   ├── SsoConfigurationController.java
│   │   └── UsersController.java
│   │
│   ├── dto/                             # Data Transfer Objects
│   │   ├── AzureResourceDto.java
│   │   ├── CreateScopeRequest.java
│   │   ├── ResponseDto.java
│   │   └── [other DTOs...]
│   │
│   ├── entity/                          # JPA Entities
│   │   ├── Address.java                 # Embeddable address
│   │   ├── ApiFlag.java                 # Feature flag definitions
│   │   ├── AuthProviderConfig.java      # Legacy auth config
│   │   ├── Event.java                   # Audit logs
│   │   ├── Feature.java                 # Feature definitions
│   │   ├── Groups.java                  # User groups
│   │   ├── Package.java                 # Tenant packages
│   │   ├── Roles.java                   # Roles
│   │   ├── Scopes.java                  # OAuth scopes/permissions
│   │   ├── SsoConfiguration.java        # SSO provider configs
│   │   ├── Tenant.java                  # Multi-tenant root
│   │   ├── TenantApiMapping.java        # Tenant-specific API flags
│   │   ├── TenantType.java              # Tenant classification
│   │   └── User.java                    # User accounts
│   │
│   ├── exception/                       # Exception Handling
│   │   ├── GlobalException.java
│   │   ├── GlobalExceptionHandler.java
│   │   ├── InvalidTokenException.java
│   │   ├── KeycloakOperationException.java
│   │   ├── ResourceConflictException.java
│   │   └── [other exceptions...]
│   │
│   ├── filter/                          # Security Filters
│   │   ├── JwtTenantUserValidationFilter.java
│   │   └── JwtTokenParser.java
│   │
│   ├── listener/                        # Event Listeners
│   │   └── EntityListener.java          # Audit logging listener
│   │
│   ├── repository/                      # Data Access Layer
│   │   ├── ApiAdminFlagRepository.java
│   │   ├── AuthProviderConfigRepository.java
│   │   ├── EventRepository.java
│   │   ├── FeatureRepository.java
│   │   ├── GroupRepository.java
│   │   ├── PackageRepository.java
│   │   ├── RoleRepository.java
│   │   ├── ScopeRepository.java
│   │   ├── SsoConfigurationRepository.java
│   │   ├── TenantApiMappingRepository.java
│   │   ├── TenantRepository.java
│   │   ├── TenantTypeRepository.java
│   │   └── UserRepository.java
│   │
│   ├── service/                         # Business Logic Layer
│   │   ├── ApiAdminService.java
│   │   ├── AuthProviderConfigService.java
│   │   ├── AzureGraphService.java       # Microsoft Graph API client
│   │   ├── EventService.java
│   │   ├── FeatureService.java
│   │   ├── FlagManagementService.java   # OpenFeature integration
│   │   ├── GroupsService.java
│   │   ├── PackageService.java
│   │   ├── RolesService.java
│   │   ├── ScopesService.java
│   │   ├── SsoConfigurationService.java
│   │   ├── TenantService.java
│   │   └── UsersService.java
│   │
│   ├── util/                            # Utility Classes
│   │   ├── JwtTokenParserUtil.java
│   │   └── KeycloakAdminUtil.java       # Keycloak API wrapper
│   │
│   └── IamApplication.java              # Spring Boot main class
│
├── src/main/resources/
│   └── application.properties           # Application configuration
│
├── pom.xml                              # Maven build configuration
├── azure-pipelines.yml                  # CI/CD pipeline
└── README.md                            # This file
```

### Package Organization

| Package | Purpose |
|---------|---------|
| `config` | Spring configuration beans (Security, Keycloak, DataSource, Swagger) |
| `controller` | REST API endpoint definitions using `@RestController` |
| `dto` | Data Transfer Objects for request/response bodies |
| `entity` | JPA entities mapped to database tables |
| `exception` | Custom exceptions and global exception handler |
| `filter` | Request filters for JWT validation and tenant resolution |
| `listener` | JPA entity listeners for cross-cutting concerns (e.g., audit logging) |
| `repository` | Spring Data JPA repositories for database access |
| `service` | Business logic and transaction management |
| `util` | Reusable utility classes for JWT parsing, Keycloak operations, etc. |

---

## Getting Started

### Prerequisites

- **Java 17** or higher
- **Maven 3.6+** for building
- **PostgreSQL 12+** database
- **Keycloak 22+** server
- **Azure AD App Registration** (optional, for Azure SSO)

### Environment Variables

Create a `.env` file or set the following environment variables:

```bash
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/iam_db
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password

# Keycloak Admin Configuration
KC_ADMIN_CLIENT_ID=admin-cli
KC_ADMIN_CLIENT_SECRET=your_admin_secret
KC_ADMIN_REALM=master
KC_ADMIN_USERNAME=admin
KC_ADMIN_PASSWORD=admin_password
REALM_URL=http://localhost:8080/realms/{realm}
REDIRECT_URL=http://localhost:3000/*

# SMTP Configuration (for email verification)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_USERNAME=your_email@gmail.com
SMTP_PASSWORD=your_email_password
SMTP_MAIL=noreply@secufusion.com

# Eureka Service Discovery (optional)
EUREKA_DEFAULT_ZONE=http://localhost:8761/eureka/
IAM_SERVICE_NAME=iam-service

# Master Admin Setup (initial superuser)
MASTER_ADMIN_EMAIL=admin@secufusion.com
MASTER_ADMIN_USERNAME=master_admin
MASTER_ADMIN_PASSWORD=SecurePassword123!
MASTER_ADMIN_PHONE=+1234567890
```

### Build & Run

```bash
# Clone the repository
git clone <repository-url>
cd sfn-iam-api

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Or run the JAR directly
java -jar target/IAM-0.0.1-SNAPSHOT.jar
```

### Access Points

- **API Base URL:** `http://localhost:9002/api/iam`
- **Swagger UI:** `http://localhost:9002/swagger-ui/index.html`
- **OpenAPI Spec:** `http://localhost:9002/v3/api-docs`
- **Health Check:** `http://localhost:9002/actuator/health`

---

## Configuration

### application.properties

Key configuration properties:

```properties
# Server Configuration
server.port=9002
server.servlet.context-path=/api/iam

# Database Configuration
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# Keycloak Configuration
keycloak.admin.client-id=${KC_ADMIN_CLIENT_ID}
keycloak.admin.client-secret=${KC_ADMIN_CLIENT_SECRET}
keycloak.admin.realm=${KC_ADMIN_REALM}
keycloak.admin.server-url=${KC_ADMIN_SERVER_URL}

# JWT Configuration
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${JWK_SET_URI}

# SMTP Configuration
spring.mail.host=${SMTP_HOST}
spring.mail.port=${SMTP_PORT}
spring.mail.username=${SMTP_USERNAME}
spring.mail.password=${SMTP_PASSWORD}

# Eureka Configuration
eureka.client.service-url.defaultZone=${EUREKA_DEFAULT_ZONE}
spring.application.name=${IAM_SERVICE_NAME}
```

### Database Schema

The application uses JPA with Hibernate for automatic schema generation. On first run with `ddl-auto=update`, it will create the following tables:

- `tenant` - Tenant records
- `user` - User accounts
- `groups` - User groups
- `roles` - Role definitions
- `scopes` - Permission scopes
- `sso_configuration` - SSO provider configs
- `auth_provider_config` - Legacy auth configs
- `feature` - Feature definitions
- `api_flag_entity` - API flag definitions
- `tenant_api_mapping_entity` - Tenant-specific API flags
- `user_group_map` - User-to-Group mapping
- `group_role_map` - Group-to-Role mapping
- `role_scope_mapping` - Role-to-Scope mapping
- `tenant_type_scope_map` - TenantType-to-Scope mapping
- `event` - Audit log entries

---

## API Documentation

### Swagger UI

Access the interactive API documentation at:
```
http://localhost:9002/swagger-ui/index.html
```

### Core API Endpoints

#### Authentication
```http
GET  /tenant-config/v1?domain={domain}     # Get tenant config with validation
POST /login                                # Authenticate user
```

#### Users
```http
POST   /users                              # Create user
GET    /users                              # List all users
GET    /users/{userId}                     # Get user by ID
PUT    /users/{userId}                     # Update user
DELETE /users/{userId}                     # Delete user
GET    /users/check?username={username}    # Check username availability
POST   /users/tenants/{tenantId}/users/{userId}/resend-verification
```

#### Roles
```http
POST /roles                                # Create role
GET  /roles                                # List all roles
GET  /roles/{id}                           # Get role by ID
PUT  /roles/{id}                           # Update role
POST /roles/activate                       # Activate role
```

#### Groups
```http
POST /groups                               # Create group with roles
GET  /groups                               # List all groups
GET  /groups/{id}                          # Get group by ID
PUT  /groups/{id}                          # Update group
```

#### Scopes
```http
POST   /scopes                             # Create scope
GET    /scopes                             # List all scopes
GET    /scopes/{scopeId}                   # Get scope by ID
GET    /scopes/menu/{menuName}             # Get scopes by menu
DELETE /scopes/{scopeId}                   # Delete scope
PUT    /scopes/{scopeId}/tenant-types      # Update scope tenant types
```

#### SSO Configuration
```http
POST   /sso-configurations                 # Create SSO provider
GET    /sso-configurations                 # List SSO configs
GET    /sso-configurations/{id}            # Get SSO config
PUT    /sso-configurations/{id}            # Update SSO config
DELETE /sso-configurations/{id}            # Delete SSO config
PUT    /sso-configurations/{id}/activate   # Activate SSO config
GET    /sso-configurations/roles           # Get Azure app roles
GET    /sso-configurations/groups?search=  # Search Azure groups
```

#### Feature Flags
```http
GET  /features                             # List all features
POST /features                             # Create feature
PUT  /features/{id}                        # Update feature
```

### Response Format

All API responses follow this structure:

```json
{
  "results": { /* response data */ },
  "message": "Success message",
  "code": "200"
}
```

Error responses:

```json
{
  "success": false,
  "errorCode": "404",
  "message": "Resource not found",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Coding Standards

This project follows strict coding standards to ensure maintainability, readability, and consistency.

### 1. Code Organization

#### Package Structure
- **One class per file** with the file name matching the class name
- **Organize by feature** (layered architecture: controller → service → repository)
- **Separate DTOs** from entities to prevent over-exposure of domain model

#### Naming Conventions
| Type | Convention | Example |
|------|-----------|---------|
| Classes | PascalCase | `UserService`, `RolesController` |
| Interfaces | PascalCase | `UserRepository` |
| Methods | camelCase | `getUserById()`, `createRole()` |
| Variables | camelCase | `tenantId`, `userName` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Packages | lowercase | `com.secufusion.iam.service` |

### 2. Lombok Usage

Use Lombok annotations to reduce boilerplate code:

```java
@Data                    // Auto-generate getters, setters, equals, hashCode, toString
@Slf4j                   // SLF4J logger injection
@RequiredArgsConstructor // Constructor injection for final fields
@Builder                 // Builder pattern for entity construction
@NoArgsConstructor       // No-args constructor (required by JPA)
@AllArgsConstructor      // All-args constructor
```

**Example:**
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class UsersService {
    private final UserRepository userRepository;
    private final KeycloakAdminUtil keycloakUtil;

    public User createUser(UserDto dto) {
        log.info("Creating user: {}", dto.getUsername());
        // business logic
    }
}
```

### 3. Dependency Injection

**Preferred: Constructor Injection**
```java
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class RolesService {
    private final RoleRepository roleRepository;
    private final ScopeRepository scopeRepository;
}
```

**Avoid: Field Injection** (unless necessary for backward compatibility)
```java
// Avoid this pattern
@Autowired
private UserRepository userRepository;
```

### 4. Response Pattern

All controller methods return `ResponseEntity<ResponseDto<T>>`:

```java
@PostMapping
public ResponseEntity<ResponseDto<User>> createUser(@RequestBody UserDto dto) {
    User user = userService.createUser(dto);
    return ResponseEntity.ok(new ResponseDto<>(user, "User created", "201"));
}
```

### 5. Exception Handling

**Use custom exceptions** for domain-specific errors:

```java
// Service layer
if (existingUser != null) {
    throw new ResourceConflictException("User already exists");
}

// Global exception handler catches and maps to HTTP response
@ExceptionHandler(ResourceConflictException.class)
public ResponseEntity<ErrorResponse> handleConflict(ResourceConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(false, "409", ex.getMessage()));
}
```

### 6. Transaction Management

Use `@Transactional` for multi-step operations:

```java
@Transactional
public User createUser(UserDto dto) {
    User user = userRepository.save(toEntity(dto));
    keycloakUtil.createKeycloakUser(user);  // External call in same transaction
    return user;
}
```

### 7. Logging Standards

```java
log.info("Creating user for tenant: {}", tenantId);           // High-level operations
log.debug("User details: username={}, email={}", user, email); // Intermediate state
log.warn("User already exists: {}", username);                // Recoverable issues
log.error("Failed to create Keycloak user: {}", e.getMessage(), e); // Exceptions
```

**Guidelines:**
- Use **parameterized logging** (not string concatenation)
- Include **context** (tenant ID, user ID) in messages
- Log **exceptions with stack traces** using `, e` parameter
- **Avoid logging sensitive data** (passwords, tokens)

### 8. Validation

**Use Bean Validation annotations:**

```java
public class UserDto {
    @NotBlank(message = "Username is required")
    private String username;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number")
    private String phoneNo;
}
```

**Service-level validation:**
```java
if (userRepository.existsByUsername(dto.getUsername())) {
    throw new ResourceConflictException("Username already taken");
}
```

### 9. Entity Design

**Use JPA annotations properly:**

```java
@Entity
@Table(name = "user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID pkUserId;

    @Column(nullable = false, unique = true)
    private String username;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    @JsonIgnore  // Prevent infinite loops
    private Tenant tenant;
}
```

### 10. Repository Pattern

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    List<User> findByFkTenantId(UUID tenantId);
    boolean existsByEmail(String email);
}
```

### 11. Service Layer Guidelines

- **Single Responsibility:** Each service handles one domain entity
- **Transaction Boundaries:** Use `@Transactional` for data modifications
- **Validation First:** Validate inputs before processing
- **Fail Fast:** Throw exceptions early for invalid states
- **Logging:** Log at start, decision points, and completion

### 12. Controller Guidelines

- **Thin Controllers:** Delegate all logic to services
- **Use DTOs:** Never expose entities directly
- **Validation:** Use `@Valid` on request bodies
- **Documentation:** Annotate with Swagger/OpenAPI annotations

```java
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for user CRUD operations")
public class UsersController {

    private final UsersService userService;

    @PostMapping
    @Operation(summary = "Create a new user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
    public ResponseEntity<ResponseDto<User>> createUser(@Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(new ResponseDto<>(userService.createUser(dto)));
    }
}
```

### 13. API Design Standards

- **RESTful conventions:** Use proper HTTP methods (GET, POST, PUT, DELETE)
- **Resource naming:** Plural nouns (`/users`, not `/user`)
- **Status codes:** Return appropriate HTTP status codes
  - 200 OK - Success
  - 201 Created - Resource created
  - 400 Bad Request - Validation error
  - 401 Unauthorized - Authentication required
  - 403 Forbidden - Insufficient permissions
  - 404 Not Found - Resource not found
  - 409 Conflict - Resource already exists
  - 500 Internal Server Error - Server error

### 14. Git Commit Standards

- Use **present tense** ("Add feature" not "Added feature")
- Use **imperative mood** ("Move cursor to..." not "Moves cursor to...")
- Limit first line to **72 characters**
- Reference **issue numbers** when applicable

**Examples:**
```
Add Azure AD group synchronization
Fix user creation validation bug
Update role mapping documentation
Refactor SSO configuration service
```

---

## External Integrations

### 1. Keycloak Integration

**Purpose:** Centralized authentication and identity provider management

#### Setup

1. Install and run Keycloak server
2. Create master admin user
3. Set environment variables:
   ```
   KC_ADMIN_SERVER_URL=http://localhost:8080
   KC_ADMIN_USERNAME=admin
   KC_ADMIN_PASSWORD=admin
   ```

#### Operations Supported
- Create/update/delete users
- Configure identity providers (Azure AD, OIDC, SAML)
- Manage attribute mappers
- Configure authentication flows

### 2. Azure Active Directory Integration

**Purpose:** Federated authentication and directory synchronization

#### Setup

1. Register an application in Azure AD
2. Create a client secret
3. Grant Microsoft Graph API permissions:
   - `Application.Read.All`
   - `Directory.Read.All`
   - `Group.Read.All`
4. Store credentials in SSO Configuration

#### Operations Supported
- Fetch Azure App Roles
- Search Security Groups
- Query user directory information

### 3. PostgreSQL Database

**Schema Management:** Automatic via JPA Hibernate (ddl-auto=update)

**Connection Pooling:** HikariCP (Spring Boot default)

**Configuration:**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/iam_db
spring.datasource.username=postgres
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update
```

### 4. SMTP Email Integration

**Purpose:** Send verification and notification emails

**Configuration:**
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@gmail.com
spring.mail.password=your_app_password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### 5. Eureka Service Discovery

**Purpose:** Service registration in microservice architecture

**Configuration:**
```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
spring.application.name=iam-service
eureka.instance.prefer-ip-address=true
```

---

## Security

### Authentication

**JWT Bearer Token Authentication:**
- Tokens issued by Keycloak
- Per-tenant JWK Set URI for signature validation
- Claims: `sub`, `email`, `preferred_username`, `realm_access`, custom claims

### Authorization

**Multi-Level Access Control:**

1. **Endpoint Security:** All endpoints except `/public/**`, `/swagger-ui/**`, `/actuator/**` require authentication
2. **Tenant Isolation:** Users can only access data within their tenant
3. **User Status Validation:** Only ACTIVE users can access APIs
4. **Scope-Based Authorization:** User permissions resolved through: `User → Groups → Roles → Scopes`
5. **Feature Flags:** Per-tenant API enablement/disablement

### Security Filters

**Request Flow:**
```
HTTP Request
  → CORS Filter
  → JWT Authentication Filter (validates signature)
  → JwtTenantUserValidationFilter (validates tenant/user status, resolves scopes)
  → Feature Flag Filter (evaluates API enablement)
  → Controller (processes request)
```

### Best Practices

- **Never log sensitive data** (passwords, tokens)
- **Use HTTPS** in production
- **Rotate secrets regularly** (client secrets, database passwords)
- **Implement rate limiting** (recommended: Spring Cloud Gateway or API Gateway)
- **Validate all inputs** to prevent injection attacks
- **Sanitize user-generated content** to prevent XSS

---

## Development Guidelines

### Adding a New Entity

1. Create entity class in `entity/` package with JPA annotations
2. Create repository interface extending `JpaRepository`
3. Create service class in `service/` with business logic
4. Create controller in `controller/` with REST endpoints
5. Create DTOs in `dto/` for request/response bodies
6. Add Swagger/OpenAPI documentation
7. Update this README if it's a major feature

### Adding a New API Endpoint

1. Define method in appropriate controller
2. Annotate with `@Operation`, `@ApiResponses`
3. Implement business logic in service layer
4. Add validation annotations to DTOs
5. Handle exceptions properly
6. Test with Postman/Swagger UI
7. Document in README (if public-facing)

### Database Migrations

**Currently:** Using Hibernate auto-update (not recommended for production)

**Recommended for Production:** Use Flyway or Liquibase

### Adding External Integration

1. Add dependency to `pom.xml`
2. Create configuration class in `config/`
3. Create service wrapper in `service/`
4. Add connection details to `application.properties`
5. Document in README under "External Integrations"

---

## Troubleshooting

### Common Issues

#### 1. Keycloak Connection Failed
```
Error: Unable to connect to Keycloak server
```
**Solution:** Verify `KC_ADMIN_SERVER_URL` is correct and Keycloak is running.

#### 2. JWT Validation Failed
```
Error: Invalid token signature
```
**Solution:** Check that `JWK_SET_URI` in tenant's `AuthProviderConfig` is correct.

#### 3. Database Connection Error
```
Error: Connection to localhost:5432 refused
```
**Solution:** Ensure PostgreSQL is running and credentials are correct.

#### 4. Azure Graph API 401 Unauthorized
```
Error: Failed to authenticate with Azure AD
```
**Solution:** Verify Azure app client ID, secret, and tenant ID in SSO configuration.

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -m 'Add new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Create a Pull Request

---

## License

Proprietary - Secufusion IAM API

---

## Support

For issues, questions, or feature requests:
- **Email:** support@secufusion.com
- **Issue Tracker:** GitHub Issues
- **Documentation:** Internal Wiki

---

## Changelog

### Version 0.0.1-SNAPSHOT (Current)
- Multi-tenant IAM with Keycloak integration
- Azure AD federated authentication
- Role-based access control (RBAC)
- Feature flag management with OpenFeature
- SSO configuration management
- Microsoft Graph API integration for Azure resources

---

**Built with ❤️ by the Secufusion Team**
