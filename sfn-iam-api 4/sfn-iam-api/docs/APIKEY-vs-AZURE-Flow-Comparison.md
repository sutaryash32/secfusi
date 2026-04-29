# SecuFusion Extension Auth Flow — APIKEY vs AZURE Tenant

**Project:** sfn-iam-api + sfn-events-api
**Date:** 2026-03-14
**Branch:** tenant-column-missmatch

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Diagram](#2-architecture-diagram)
3. [How Extension Detects the Flow](#3-how-extension-detects-the-flow)
4. [Setup Phase Comparison](#4-setup-phase-comparison)
5. [Runtime Login Flow — APIKEY](#5-runtime-login-flow--apikey)
6. [Runtime Login Flow — AZURE](#6-runtime-login-flow--azure)
7. [Group Management Comparison](#7-group-management-comparison)
8. [Policy Assignment Comparison](#8-policy-assignment-comparison)
9. [Device User Management Comparison](#9-device-user-management-comparison)
10. [Token & Auth Lifecycle Comparison](#10-token--auth-lifecycle-comparison)
11. [Request Filter Flow (Both Tenants)](#11-request-filter-flow-both-tenants)
12. [Unauthorize & Delete Comparison](#12-unauthorize--delete-comparison)
13. [Side-by-Side Comparison Table](#13-side-by-side-comparison-table)
14. [Data Model Comparison](#14-data-model-comparison)
15. [Key Business Rules](#15-key-business-rules)
16. [API Endpoints Reference](#16-api-endpoints-reference)

---

## 1. Overview

SecuFusion supports two tenant authentication modes for the browser extension:

| Mode | ssoType | Auth Mechanism | Group Type |
|------|---------|----------------|------------|
| **APIKEY** | `"APIKEY"` | Raw API Key → Keycloak `client_credentials` JWT | `APIKEY_GROUP` |
| **AZURE** | `"AZURE"` | Azure AD SSO → Azure JWT with `groups` claim | `AZURE_GROUP` |

The **same JWT validation pipeline** (`JwtTenantUserValidationFilter`) handles both after the initial login. The difference is only in **how the JWT is obtained** and **how group membership is checked**.

---

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        APIKEY TENANT                                │
│                                                                     │
│  Browser Extension                                                  │
│       │                                                             │
│       ├─ 1. Has embedded apiKey: "sk_AbCd..."                       │
│       ├─ 2. POST /api/events/public/extension/generate-token        │
│       │         (sfn-events-api)                                    │
│       │         → Validates API key hash                            │
│       │         → Creates/updates DeviceUser                        │
│       │         → Assigns to default APIKEY_GROUP                   │
│       │         → Calls Keycloak client_credentials                 │
│       │         → Returns JWT                                       │
│       │                                                             │
│       ├─ 3. POST /login/extension?token=JWT                         │
│       │         (sfn-iam-api)                                       │
│       │         → Checks: email in authorized APIKEY_GROUP?         │
│       │         → Returns LoginResponseDto { ssoType="APIKEY" }     │
│       │                                                             │
│       └─ 4. All requests: Authorization: Bearer JWT                 │
│                 (JwtTenantUserValidationFilter validates)            │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        AZURE TENANT                                 │
│                                                                     │
│  Browser Extension                                                  │
│       │                                                             │
│       ├─ 1. No apiKey in config                                     │
│       ├─ 2. User logs in via Azure AD SSO                           │
│       │         → Azure AD issues JWT with "groups" claim           │
│       │            (groups = Azure AD group OIDs the user belongs)  │
│       │                                                             │
│       ├─ 3. POST /login/extension?token=JWT                         │
│       │         (sfn-iam-api)                                       │
│       │         → Checks: JWT groups claim in authorized            │
│       │                   AZURE_GROUP (events_group table)?         │
│       │         → Returns LoginResponseDto { ssoType="AZURE" }      │
│       │                                                             │
│       └─ 4. All requests: Authorization: Bearer JWT                 │
│                 (JwtTenantUserValidationFilter validates)            │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. How Extension Detects the Flow

### Step 1 — Extension calls `/tenant-config` on boot

```
GET /api/iam/tenant-config?host=acme.motivitylabs.net

Response: AuthDetailsDto {
  tenantId:    "abc-123",
  tenantKey:   "acme",
  tenantType:  "ENTERPRISE",     ← business type (NOT auth type)
  keycloakUrl: "https://idp.motivitylabs.net",
  realm:       "tenant_acme",
  clientId:    "acme-extension-client",
  issuer:      "https://idp.motivitylabs.net/realms/tenant_acme",
  jwkUri:      "https://idp.motivitylabs.net/realms/.../certs",
  tokenUri:    "https://idp.motivitylabs.net/realms/.../token",
  domain:      "acme.motivitylabs.net",
  status:      "ACTIVE"
}
```

> **Note:** `AuthDetailsDto` does NOT contain an explicit `ssoType` field.
> The extension infers the flow from its **own embedded config** + the **issuer URL**.

### Step 2 — Extension decision tree

```
Extension boots
      │
      ├─ GET /tenant-config → loads keycloakUrl, realm, clientId, issuer
      │
      ├── apiKey present in extension config?
      │
      ├── YES ──→  APIKEY FLOW
      │               POST /api/events/public/extension/generate-token
      │               { apiKey, email, displayName }
      │
      └── NO  ──→  Check issuer URL
                      │
                      ├── issuer contains "login.microsoftonline.com"
                      │       → AZURE SSO FLOW
                      │         user logs in via Azure AD popup
                      │
                      └── issuer contains Keycloak URL
                              → KEYCLOAK SSO FLOW
                                user logs in via Keycloak popup
```

### Step 3 — `ssoType` confirmed after login

`POST /login/extension` returns `LoginResponseDto.ssoType`:
- `"APIKEY"` → stored in extension/Redux state
- `"AZURE"` → stored in extension/Redux state
- Used for token refresh strategy on expiry

---

## 4. Setup Phase Comparison

### APIKEY Tenant Setup

| Step | Action | Endpoint | Service |
|------|--------|----------|---------|
| 1 | Admin creates APIKEY_GROUP | `POST /events-groups` | sfn-iam-api |
| 2 | Group auto-authorized (`authorized=true`) | — | EventsGroupService |
| 3 | Default policies auto-assigned (browser/network/extension) | — | AzureGroupSyncService |
| 4 | Admin generates API key | `POST /api/events/extension-api-keys` | sfn-events-api |
| 5 | Keycloak client auto-created for tenant | — | KeycloakClientService |
| 6 | Admin distributes `rawKey` to extension (shown once only) | — | Manual |
| 7 | Mark one group as `isDefault=true` for auto-assignment | — | DB/admin |

```
ExtensionApiKey stored in DB:
  keyHash   = SHA-256(rawKey)   ← only hash stored, never raw key
  keyPrefix = first 12 chars    ← for display
  clientId  = "{tenantName}-extension-client"
  clientSecret (encrypted)
  status = ACTIVE
  expiresAt = now + DEFAULT_EXPIRY_DAYS
```

### AZURE Tenant Setup

| Step | Action | Endpoint | Service |
|------|--------|----------|---------|
| 1 | Admin triggers Azure group sync | `POST /events-groups/sync` | sfn-iam-api |
| 2 | Async fetch all groups from Microsoft Graph API | — | AzureGroupSyncService |
| 3 | Groups stored with `authorized=false` by default | — | EventsGroupService |
| 4 | Admin reviews groups and authorizes selected ones | `PUT /events-groups/groups/{groupId}/authorize` | sfn-iam-api |
| 5 | Default policies auto-assigned on authorization | — | AzureGroupSyncService |
| 6 | Azure AD members synced to DeviceUser records | — | AzureGroupSyncService |
| 7 | Users log in via Azure AD SSO (no manual distribution needed) | — | Azure AD |

```
Sync cooldown: 10 minutes between syncs
Sync status tracked in-memory: IN_PROGRESS / COMPLETED / FAILED
Azure groups fetched via: GET /groups (Microsoft Graph API)
Members fetched via: GET /groups/{id}/members (Microsoft Graph API)
```

---

## 5. Runtime Login Flow — APIKEY

```
STEP 1: Extension → generate-token (sfn-events-api)
─────────────────────────────────────────────────────
POST /api/events/public/extension/generate-token
  {
    apiKey: "sk_AbCdEfGh...",
    deviceUserDetails: {
      email:       "user@company.com",
      userName:    "jdoe",
      displayName: "John Doe",
      source:      "EXTENSION"
    }
  }
  (No Authorization header required — PUBLIC endpoint)

  Inside ExtensionTokenService.generateToken():
  │
  ├── 4a. Validate API key
  │     keyHash = SHA-256(rawKey)
  │     Find by keyHash in extension_api_keys table
  │     Check: status = "ACTIVE"
  │     Check: not expired (expiresAt > now)
  │     Update: lastUsedAt = now
  │
  ├── 4b. Create or update DeviceUser
  │     Find by tenantId + email
  │     FOUND  → update lastSeenAt, userName, displayName
  │     NOT FOUND → create new DeviceUser { status=ACTIVE, source=EXTENSION }
  │
  ├── 4c. Assign to default APIKEY_GROUP
  │     Find group where isDefault=true for tenant
  │     If not already assigned → create EventsGroupDeviceUserMapping
  │     assignedBy = "SYSTEM_API_KEY"
  │
  └── 4d. Generate Keycloak JWT (client_credentials grant)
        POST {keycloakUrl}/realms/{realm}/protocol/openid-connect/token
          grant_type    = client_credentials
          client_id     = "{tenantName}-extension-client"
          client_secret = decryptedSecret
        → Returns: access_token (JWT), expires_in=3600

Response:
  {
    accessToken:         "<Keycloak JWT>",
    tokenType:           "Bearer",
    expiresIn:           3600,
    apiKeyExpiresAt:     "2027-01-01T00:00:00",
    apiKeyDaysRemaining: 90,
    warningMessage:      null   (or "expires in N days" if close)
  }


STEP 2: Extension → /login/extension (sfn-iam-api)
────────────────────────────────────────────────────
POST /login/extension?token=<Keycloak JWT>
  body: { deviceFingerprint, deviceId, deviceName, browserType, osInfo }

  Inside AuthConfigService:
  │
  ├── validateExtensionGroupAuthorization()
  │     ├─ Path 1: Azure groups in JWT "groups" claim?
  │     │     APIKEY JWT → no Azure groups → skip
  │     │
  │     └─ Path 2: APIKEY check by email
  │           email = JwtUtil.getEmail(request)
  │           eventsGroupDeviceUserMappingRepository
  │               .existsInAuthorizedGroup(tenantId, email)
  │           → email NOT found in authorized group → 403
  │           → email found → PASS ✓
  │
  └── login()
        Build LoginResponseDto:
          userId, username, email, firstName, lastName
          accessToken = JWT
          userType    = "APIKEY"
          ssoType     = "APIKEY"
          mappedGroups → groups → roles
          permissionMatrix → { menu → { submenu → [actions] } }

        loginAuditService.logLoginWithDevice()
          → records: tenantId, email, ip, deviceFingerprint,
                     deviceId, deviceName, browserType, osInfo

Response: LoginResponseDto
```

---

## 6. Runtime Login Flow — AZURE

```
STEP 1: User logs in via Azure AD SSO
───────────────────────────────────────
  User → Azure AD login page (MSAL / Keycloak SSO bridge)
  Azure AD issues JWT containing:
    "email":  "user@company.com"
    "groups": ["oid-group-1", "oid-group-2"]  ← Azure group OIDs
    "azp":    Keycloak client ID              ← identifies tenant
    "tid":    Azure tenant ID                 ← Azure AD tenant


STEP 2: Extension → /login/sso (sfn-iam-api) [optional pre-check]
───────────────────────────────────────────────────────────────────
POST /login/sso?token=<Azure JWT>
  (bypasses JwtTenantUserValidationFilter)

  Inside AuthConfigService.ssoLogin():
  │
  ├─ Extract "tid" claim → azureTenantId
  ├─ tenantRepository.findByAzureTenantId(azureTenantId)
  │     → not found → unauthorized "Tenant not registered"
  ├─ Validate tenant.azureTenantId == token.tid
  ├─ ssoConfigurationRepository.findByFkTenantIdAndActive()
  │     → ssoConfig.enabled=false → unauthorized "SSO disabled"
  └─ Response: { authorized: true, username, tenantName, alias }


STEP 3: Extension → /login/extension (sfn-iam-api)
─────────────────────────────────────────────────────
POST /login/extension?token=<Azure JWT>
  body: { deviceFingerprint, deviceId, deviceName, browserType, osInfo }

  Inside AuthConfigService:
  │
  ├── validateExtensionGroupAuthorization()
  │     ├─ Path 1: AZURE — check "groups" claim (PRIMARY PATH)
  │     │     azureGroupIds = JwtUtil.getGroupsFromToken(token)
  │     │     eventsGroupRepository
  │     │         .existsAuthorizedAzureGroup(tenantId, azureGroupIds)
  │     │     SQL: WHERE tenant_id=:tid
  │     │          AND   azure_group_id IN (:ids)
  │     │          AND   authorized=true
  │     │          AND   group_type='AZURE_GROUP'
  │     │     → found → PASS ✓
  │     │     → not found → fall to Path 2
  │     │
  │     └─ Path 2: fallback by email
  │           eventsGroupDeviceUserMappingRepository
  │               .existsInAuthorizedGroup(tenantId, email)
  │           → not found → 403 AccessDeniedException
  │
  └── login()
        Build LoginResponseDto:
          userType    = "AZURE" (or business tenantType)
          ssoType     = "AZURE"
          permissionMatrix filtered by tenantType = "AZURE"

        loginAuditService.logLoginWithDevice()

Response: LoginResponseDto { ssoType="AZURE" }
```

---

## 7. Group Management Comparison

### Creating Groups

| Aspect | APIKEY | AZURE |
|--------|--------|-------|
| Who creates | Admin manually via API | Azure AD sync (automatic) |
| Endpoint | `POST /events-groups` | `POST /events-groups/sync` |
| Default `authorized` | `true` (auto-authorized) | `false` (requires admin approval) |
| Group type | `APIKEY_GROUP` | `AZURE_GROUP` |
| Name source | Admin input | Azure AD `displayName` |
| Can update name | Yes | No (only via sync) |
| Default group flag | Required (for auto-assign) | Not used |

### Authorizing Groups

**APIKEY:**
```
PUT /events-groups/groups/{groupId}/authorize
  → eventsGroupService.authorizeGroup()
  → group.authorized = true
  → assignDefaultPolicies()
  → group stays in DB
```

**AZURE:**
```
PUT /events-groups/groups/{groupId}/authorize
  → azureGroupSyncService.authorizeAzureGroupById()
  │
  ├── Case A: groupId = DB ID (already synced)
  │     group.authorized = true
  │     assignDefaultPolicies()
  │     syncGroupMembers()        ← fetch members from Azure AD
  │
  └── Case B: groupId = Azure OID (not yet in DB)
        AzureGraphService.getGroupById() → fetch from Microsoft Graph
        eventsGroupService.authorizeAndPersistAzureGroup()
          → create new EventsGroup with authorized=true
        assignDefaultPolicies()
        syncGroupMembers()
```

---

## 8. Policy Assignment Comparison

### assignDefaultPolicies() — Same for Both

```
Called after: createGroup (APIKEY) / authorizeGroup (both)

AzureGroupSyncService.assignDefaultPolicies(group, tenantId, performedBy):
  │
  ├─ Skip if group already has PolicyAssignment rows
  │
  ├─ browserPolicyRepository.findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc()
  │     fallback → findAllByFkTenantIdIsNullAndIsActiveTrueOrderByCreatedAtAsc()
  │
  ├─ networkPolicyRepository    (same fallback logic)
  ├─ extensionPolicyRepository  (same fallback logic)
  │
  └─ Save up to 3 PolicyAssignment rows:
        azureResourceId   = groupId
        azureResourceName = groupName
        assignmentType    = "GROUP"
        fkTenantId        = tenantId

PolicyAssignment rows per group:
  Row 1: browserPolicy  (FK to browser_policy)
  Row 2: networkPolicy  (FK to network_policy)
  Row 3: extensionPolicy (FK to extension_policy)
```

### Policy Resolution for a Device User (Same for Both)

```
DeviceUserGroupMappingService.resolvePoliciesForDeviceUser(tenantId, deviceUserId):
  │
  ├─ Step 1: Get all groupIds for this user
  │     mappingRepository.findGroupIdsByDeviceUserId(deviceUserId)
  │
  ├─ Step 2: Get all PolicyAssignment rows for those groups
  │     policyAssignmentRepository.findByEventsGroupIdsAndTenantId(groupIds, tenantId)
  │
  └─ Step 3: Deduplicate — first-wins per type
        browserPolicy   ← first found across all groups
        networkPolicy   ← first found across all groups
        extensionPolicy ← first found across all groups

Returns: max 3 PolicyAssignment objects (1 per type)
```

---

## 9. Device User Management Comparison

### How DeviceUsers Are Created

| Aspect | APIKEY | AZURE |
|--------|--------|-------|
| Creation trigger | First API key token generation | Azure AD member sync |
| Created by | `ExtensionTokenService` (sfn-events-api) | `AzureGroupSyncService` (sfn-iam-api) |
| Source field | `"EXTENSION"` | `"AZURE"` |
| Email source | Extension sends in request | Fetched from Microsoft Graph API |
| Auto-assign to group | Yes — default APIKEY_GROUP (`isDefault=true`) | Yes — assigned during `syncGroupMembers()` |
| `assignedBy` in mapping | `"SYSTEM_API_KEY"` | admin email or `"AZURE_SYNC_SERVICE"` |

### APIKEY — DeviceUser creation in generate-token

```
deviceUserRepository.findByTenantIdAndEmail(tenantId, email)
  FOUND:
    user.lastSeenAt = now
    user.userName   = updated if provided
    user.displayName = updated if provided
  NOT FOUND:
    create DeviceUser {
      pkDeviceUserId = UUID,
      tenantId, email, userName, displayName,
      status = "ACTIVE",
      source = "EXTENSION",
      firstSeenAt = lastSeenAt = now
    }
```

### AZURE — DeviceUser matched during syncGroupMembers()

```
azureGraphService.getGroupMemberEmails(tenant, azureGroupId, azureTenantId)
  → List<String> memberEmails from Microsoft Graph

deviceUserRepository.findByFkTenantIdAndEmailIn(tenantId, memberEmails)
  → only matches EXISTING DeviceUser records
  → unmatched emails = Azure members with no DeviceUser yet (ignored until they log in)

For matched users:
  if not already in group → create EventsGroupDeviceUserMapping
```

### Assigning DeviceUsers to Groups

**APIKEY — Admin manual assignment:**
```
POST /events-groups/{groupId}/device-users
  { deviceUserId: "..." }           ← single
  { deviceUserIds: ["...", "..."] } ← bulk

DeviceUserGroupMappingService.bulkAssignDeviceUsersToGroup()
  → skip if already assigned (no error)
  → create EventsGroupDeviceUserMapping
```

**AZURE — Auto-sync from Azure AD:**
```
AzureGroupSyncService.syncGroupMembers(tenant, group, assignedBy)
  → fetches member emails from Microsoft Graph
  → matches to DeviceUser records by email
  → creates EventsGroupDeviceUserMapping for matched users
```

---

## 10. Token & Auth Lifecycle Comparison

### Token Generation

| Aspect | APIKEY | AZURE |
|--------|--------|-------|
| Token issuer | Keycloak (`client_credentials`) | Azure AD |
| Extension initiates | `POST /public/extension/generate-token` | Azure AD SSO popup |
| Auth credential | Raw API key `sk_...` | User's Azure AD identity |
| JWT `groups` claim | Not present | Contains Azure group OIDs |
| Token expiry | ~1 hour (Keycloak default) | Azure AD configured |
| Token refresh | Call `generate-token` again with apiKey | Azure AD silent refresh (MSAL) |
| API key expiry | Configurable (DEFAULT_EXPIRY_DAYS) | N/A |
| Key rotation | `POST /extension-api-keys/{id}/rotate` | N/A |

### API Key Lifecycle States

```
ACTIVE   → INACTIVE (manually disabled)
ACTIVE   → REVOKED  (permanently disabled, cannot undo)
ACTIVE   → EXPIRED  (expiresAt passed)
EXPIRED  → ACTIVE   (via extend-expiry)
INACTIVE → ACTIVE   (via reactivate)
ACTIVE   → ROTATED  (old key marked ROTATED, new ACTIVE key created)
```

---

## 11. Request Filter Flow (Both Tenants)

Every protected API request goes through the same filter:

```
JwtTenantUserValidationFilter.doFilterInternal()

  Bypass conditions (skip filter):
    → No "Authorization: Bearer" header
    → POST /events
    → POST /login/sso

  Step 1: Extract JWT
    JwtUtil.getUserFromRequest()   → User entity from DB
    JwtUtil.getTenantFromRequest() → Tenant entity via "azp" claim
    missing → InvalidTokenException (401)

  Step 2: Validate Tenant
    tenantRepository.findById(tenantId)
    tenant.status != "ACTIVE" → AccessDeniedException

  Step 3: Validate User
    userService.findByEmailAndTenant(email, tenantId)
    user.status != "ACTIVE" → AccessDeniedException

  Step 4: Resolve Scopes
    user.groups → roles → scopes → Set<scopeName>
    empty → AccessDeniedException

  Step 5: Set Spring Security Context
    UsernamePasswordAuthenticationToken(email, null, scopes)
    → SecurityContextHolder

  Step 6: Feature Flag Check (OpenFeature → DbFeatureFlagProvider)
    path = request.getRequestURI()
    EvaluationContext { tenantId }
    apiFlagRepository.findByPath(path) → find ApiFlagEntity
    tenantApiMappingRepository.findByApiIdAndTenantId()
      not found → allow (default true)
    mapping.enabled=true → block 404 "API disabled for tenant"

  → filterChain.doFilter() → controller
```

**Feature flag cache refreshed every 30 seconds by `ApiFlagRefresher`.**

---

## 12. Unauthorize & Delete Comparison

### Unauthorize Group

| Aspect | APIKEY | AZURE |
|--------|--------|-------|
| Effect | `authorized = false` | **HARD DELETE** from DB |
| Group remains in DB | Yes | No |
| Policies removed | No (stay assigned) | Yes — cascade delete |
| User mappings removed | No (stay assigned) | Yes — cascade delete |
| Can re-authorize | Yes | Admin must re-authorize from Azure AD |
| Default group protection | Cannot unauthorize | Cannot unauthorize |

**APIKEY Unauthorize:**
```
EventsGroupService.unauthorizeGroup()
  → group.authorized = false
  → group stays in DB
  → policies and mappings UNTOUCHED
```

**AZURE Unauthorize:**
```
EventsGroupService.unauthorizeAndRemoveAzureGroup()
  → groupRepository.delete(group)         ← HARD DELETE
  → mappingRepository.deleteByGroup()     ← remove all user mappings
  → policyAssignmentRepository.deleteByEventsGroupId()  ← remove policies
  → group still exists in Azure AD (only local record removed)
```

### Delete Group

```
DELETE /events-groups/{groupId}
EventsGroupService.deleteGroup()
  → group.isActive = false      ← SOFT DELETE (both tenant types)
  → mappingRepository.deleteByGroup(groupId)
  → policyAssignmentRepository.deleteByEventsGroupId(groupId)
  Default group → throws IllegalStateException (cannot delete)
```

---

## 13. Side-by-Side Comparison Table

| Aspect | APIKEY Tenant | AZURE Tenant |
|--------|--------------|--------------|
| **ssoType value** | `"APIKEY"` | `"AZURE"` |
| **Auth provider** | Keycloak (client_credentials) | Azure AD / Keycloak SSO bridge |
| **How extension gets JWT** | Exchanges raw API key via `generate-token` | Azure AD SSO login popup |
| **JWT `groups` claim** | Not present | Contains Azure group OIDs |
| **Group type** | `APIKEY_GROUP` | `AZURE_GROUP` |
| **Group creation** | Admin manually via `POST /events-groups` | Azure AD sync via Microsoft Graph |
| **Default authorization** | `authorized=true` (auto on create) | `authorized=false` (admin must approve) |
| **Default group** | Required (`isDefault=true`) | Not used |
| **DeviceUser creation** | On first `generate-token` call | On `syncGroupMembers()` by email match |
| **DeviceUser source** | `"EXTENSION"` | `"AZURE"` |
| **Auto-assign to group** | Yes — `SYSTEM_API_KEY` assigns to default group | Yes — `syncGroupMembers()` assigns |
| **Login authorization check** | `existsInAuthorizedGroup(tenantId, email)` | `existsAuthorizedAzureGroup(tenantId, groupOIDs)` |
| **Fallback login check** | N/A | email-based mapping check |
| **Token refresh strategy** | Re-call `generate-token` with `apiKey` | Azure AD silent token refresh (MSAL) |
| **API key management** | Full lifecycle (create/rotate/revoke/expire) | Not applicable |
| **Unauthorize effect** | `authorized=false`, stays in DB | Hard delete from DB |
| **Delete effect** | Soft delete (`isActive=false`) | Soft delete (`isActive=false`) |
| **Policy auto-assign** | On `createGroup` and `authorizeGroup` | On `authorizeGroup` |
| **Member sync** | Admin manually assigns DeviceUsers | Microsoft Graph API member sync |
| **Web UI `ssoType` selector** | `selectSsoType(state)` returns `"APIKEY"` | `selectSsoType(state)` returns `"AZURE"` |
| **KEYCLOAK tenants** | No EventsGroups used | N/A |
| **Azure group OID stored** | No | Yes (`azure_group_id` column) |
| **syncedAt tracked** | No | Yes (last Azure AD sync time) |
| **Sync cooldown** | N/A | 10 minutes between syncs |

---

## 14. Data Model Comparison

### Tables Involved

| Table | APIKEY | AZURE | Purpose |
|-------|--------|-------|---------|
| `events_group` | `APIKEY_GROUP` rows | `AZURE_GROUP` rows | Group definitions |
| `device_user` | Created on `generate-token` | Created/matched on sync | Extension users |
| `events_group_device_user_mapping` | Admin/SYSTEM_API_KEY assigns | Sync assigns | User-Group membership |
| `policy_assignment` | Same structure | Same structure | Policies per group |
| `extension_api_keys` | Managed here | Not used | API key storage |
| `auth_provider_config` | `ssoType="APIKEY"` | `ssoType="AZURE"` | Auth config per tenant |

### events_group Key Fields by Type

| Field | APIKEY_GROUP | AZURE_GROUP |
|-------|-------------|-------------|
| `group_type` | `APIKEY_GROUP` | `AZURE_GROUP` |
| `authorized` | `true` (default) | `false` (default) |
| `is_default` | One group = `true` | Always `false` |
| `azure_group_id` | NULL | Azure OID |
| `azure_group_display_name` | NULL | Display name from Azure |
| `synced_at` | NULL | Last sync timestamp |

### extension_api_keys Fields (APIKEY only)

| Field | Description |
|-------|-------------|
| `key_hash` | SHA-256 of raw key (raw key NEVER stored) |
| `key_prefix` | First 12 chars (display only) |
| `client_id` | Keycloak client ID for token generation |
| `client_secret` | Encrypted Keycloak client secret |
| `status` | ACTIVE / INACTIVE / REVOKED / EXPIRED / ROTATED |
| `expires_at` | Expiry date (configurable) |
| `last_used_at` | Last validation timestamp |

---

## 15. Key Business Rules

### Both Tenants

- Group names must be unique within a tenant
- Default group (`isDefault=true`) cannot be deleted or unauthorized
- Deleting a group removes all user mappings and policy assignments
- Duplicate user-to-group assignments are silently skipped
- Policy conflict resolution: first-wins per type (browser/network/extension) across multiple groups
- All operations enforce tenant security boundary (tenantId always required)
- Tenant must be `ACTIVE`; user must be `ACTIVE` for any request

### APIKEY Only

- API key raw value shown **once** at creation — never stored in DB
- All API keys for same tenant share the **same Keycloak client credentials**
- Maximum active keys per tenant enforced (`MAX_KEYS_PER_TENANT` config)
- Revoked API keys cannot be reactivated
- Expired keys can be reactivated by extending expiry
- One group must have `isDefault=true` for auto-assignment on `generate-token`

### AZURE Only

- AZURE_GROUP defaults to `authorized=false` — admin must explicitly authorize
- Unauthorized Azure groups are hard deleted (not soft deleted)
- Azure group names cannot be manually updated — only via sync
- 10-minute sync cooldown after a successful sync
- Failed syncs can be retried immediately (no cooldown)
- Member sync only matches **existing** DeviceUser records by email
- Unauthorizing an Azure group removes all its policies and user mappings

---

## 16. API Endpoints Reference

### sfn-iam-api

| Method | Endpoint | Description | Tenant |
|--------|----------|-------------|--------|
| `GET` | `/tenant-config` | Get tenant auth config | Both |
| `GET` | `/tenant-config/v1` | Get tenant auth config (validated) | Both |
| `POST` | `/login` | Portal login | Both |
| `POST` | `/login/extension` | Extension login with group check | Both |
| `POST` | `/login/sso` | Azure SSO pre-check | AZURE |
| `GET` | `/events-groups` | List all groups | Both |
| `POST` | `/events-groups` | Create group | APIKEY |
| `GET` | `/events-groups/{groupId}` | Get group details | Both |
| `PUT` | `/events-groups/{groupId}` | Update group name/description | Both |
| `DELETE` | `/events-groups/{groupId}` | Soft delete group | Both |
| `PUT` | `/events-groups/groups/{groupId}/authorize` | Authorize group | Both |
| `PUT` | `/events-groups/groups/{groupId}/unauthorize` | Unauthorize group | Both |
| `POST` | `/events-groups/{groupId}/device-users` | Assign device user(s) | Both |
| `DELETE` | `/events-groups/{groupId}/device-users/{deviceUserId}` | Remove device user | Both |
| `GET` | `/events-groups/{groupId}/device-users` | List group members | Both |
| `GET` | `/events-groups/{groupId}/policies` | Get group policies | Both |
| `POST` | `/events-groups/sync` | Trigger Azure group sync | AZURE |
| `POST` | `/events-groups/sync-members` | Sync Azure group members | AZURE |
| `GET` | `/events-groups/stats/memberships` | Group membership stats | Both |
| `GET` | `/events-groups/history` | Group change history | Both |

### sfn-events-api

| Method | Endpoint | Description | Tenant |
|--------|----------|-------------|--------|
| `POST` | `/api/events/public/extension/generate-token` | Exchange API key for JWT | APIKEY |
| `POST` | `/api/events/public/extension/validate` | Validate API key (no token) | APIKEY |
| `POST` | `/api/events/public/extension/check-expiry` | Check API key expiry | APIKEY |
| `POST` | `/api/events/public/extension/usage-stats` | API key usage stats | APIKEY |
| `POST` | `/api/events/public/extension/refresh-token` | Refresh JWT via API key | APIKEY |
| `POST` | `/api/events/extension-api-keys` | Create new API key | APIKEY |
| `GET` | `/api/events/extension-api-keys` | List API keys | APIKEY |
| `GET` | `/api/events/extension-api-keys/id` | Get API key details | APIKEY |
| `PATCH` | `/api/events/extension-api-keys/{keyId}` | Update API key | APIKEY |
| `POST` | `/api/events/extension-api-keys/{keyId}/revoke` | Revoke API key | APIKEY |
| `POST` | `/api/events/extension-api-keys/{keyId}/reactivate` | Reactivate API key | APIKEY |
| `DELETE` | `/api/events/extension-api-keys/{keyId}` | Delete API key (hard) | APIKEY |
| `POST` | `/api/events/extension-api-keys/{keyId}/rotate` | Rotate API key | APIKEY |
| `POST` | `/api/events/extension-api-keys/{keyId}/extend-expiry` | Extend expiry | APIKEY |
| `GET` | `/api/events/extension-api-keys/{keyId}/rotation-history` | Key rotation history | APIKEY |
| `GET` | `/api/events/extension-api-keys/stats` | API key statistics | APIKEY |
| `GET` | `/api/events/extension-api-keys/settings` | Get key settings | APIKEY |
| `PATCH` | `/api/events/extension-api-keys/settings` | Update key settings | APIKEY |
| `POST` | `/api/public/extension/register-device` | Anonymous device registration | Both (MSI) |
| `POST` | `/api/public/extension/sync` | Anonymous extension sync | Both (MSI) |
| `POST` | `/api/events/extensions/sync` | Authenticated extension sync | Both |

### Admin Feature Flag Endpoints (sfn-iam-api)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/admin/api-flags/apis` | Create API feature flag |
| `PUT` | `/admin/api-flags/tenants` | Enable/disable API for tenant |
| `GET` | `/admin/api-flags/mappings` | List all flag mappings |

---

*End of document*
