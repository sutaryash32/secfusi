# Secufusion — QA Test Case Report

> **Project:** Secufusion Browser Security Platform  
> **Version:** 1.0  
> **Date:** April 9, 2026  
> **Prepared By:** Automated Audit  
> **Total Test Cases:** 105  
> **Base URL:** `http://localhost:8083`

---

## Test Case Legend

| Priority | Meaning |
|---|---|
| 🔴 P0 | Critical — Must pass for release |
| 🟠 P1 | High — Core functionality |
| 🟡 P2 | Medium — Important but not blocking |
| 🟢 P3 | Low — Nice to have |

| Status | Meaning |
|---|---|
| ⬜ | Not Executed |
| ✅ | Pass |
| ❌ | Fail |
| ⏭️ | Skipped |

---

## Module 1: Authentication & Login

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Request Body / Params | Expected Result | Status |
|---|---|---|---|---|---|---|---|---|
| TC-001 | 🔴 P0 | Verify tenant config is returned for valid host | None (public endpoint) | 1. Send GET request with valid tenant host | `GET /api/iam/tenant-config?host=example.motivitylabs.net` | Query: `host=example.motivitylabs.net` | **200 OK** — Response contains `tenantId`, `clientId`, `ssoType` (AZURE/KEYCLOAK/APIKEY), `authUrl` | ⬜ |
| TC-002 | 🟠 P1 | Verify tenant config fails for invalid host | None | 1. Send GET with non-existent host | `GET /api/iam/tenant-config?host=fake.invalid.com` | Query: `host=fake.invalid.com` | **404** or error response with descriptive message | ⬜ |
| TC-003 | 🟠 P1 | Verify tenant config requires host param | None | 1. Send GET without host param | `GET /api/iam/tenant-config` | None | **400 Bad Request** | ⬜ |
| TC-004 | 🔴 P0 | Verify login with valid Azure JWT | Valid Azure JWT token | 1. Get token from Azure AD 2. Send POST with token | `POST /api/iam/login?token=<jwt>` | Body: `{"deviceFingerprint":"abc","deviceName":"Chrome","deviceOS":"Win11","browser":"Chrome","ipAddress":"1.2.3.4"}` | **200 OK** — Returns `userId`, `email`, `tenantId`, `roles[]`, `token` (session JWT) | ⬜ |
| TC-005 | 🔴 P0 | Verify login rejects invalid JWT | None | 1. Send POST with garbage token | `POST /api/iam/login?token=invalid.jwt.here` | None | **401 Unauthorized** — No token returned | ⬜ |
| TC-006 | 🟠 P1 | Verify login rejects empty token | None | 1. Send POST with empty token param | `POST /api/iam/login?token=` | None | **400** or **401** | ⬜ |
| TC-007 | 🟠 P1 | Verify SSO login works | Valid Azure JWT | 1. Send POST with Azure JWT | `POST /api/iam/login/sso?token=<jwt>` | None | **200 OK** — Returns tenant and user info with session token | ⬜ |
| TC-008 | 🟠 P1 | Verify extension login works | Valid Azure JWT + device user email | 1. Send POST with token and email | `POST /api/iam/login/extension?token=<jwt>&deviceUserEmail=user@co.com` | None | **200 OK** — Returns extension auth details | ⬜ |
| TC-009 | 🟡 P2 | Verify tenant-config/v1 validates Referer header | Valid Referer header matching tenant | 1. Send GET with Referer header set | `GET /api/iam/tenant-config/v1?host=example.motivitylabs.net` | Header: `Referer: https://example.motivitylabs.net` | **200 OK** — Same as TC-001 | ⬜ |

---

## Module 2: User Management

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Request Body / Params | Expected Result | Status |
|---|---|---|---|---|---|---|---|---|
| TC-010 | 🔴 P0 | Get all users for tenant | Logged in with valid JWT | 1. Send GET with Bearer token | `GET /api/iam/users` | Header: `Authorization: Bearer <token>` | **200 OK** — Array of user objects with `email`, `firstName`, `lastName`, `role` | ⬜ |
| TC-011 | 🔴 P0 | Get all users — no auth header | None | 1. Send GET without Authorization header | `GET /api/iam/users` | No auth header | **401 Unauthorized** — No user data leaked | ⬜ |
| TC-012 | 🟠 P1 | Get users by specific tenant ID | Logged in + tenant ID available | 1. Send GET with tenant ID in path | `GET /api/iam/users/tenant/{tenantId}` | Path: `tenantId` | **200 OK** — Only users belonging to that tenant | ⬜ |
| TC-013 | 🟠 P1 | Get single user by ID | Logged in + valid user ID | 1. Send GET with user ID | `GET /api/iam/users/{userId}` | Path: `userId` | **200 OK** — Single user with `email`, `firstName`, `lastName`, `phoneNumber`, `role` | ⬜ |
| TC-014 | 🟠 P1 | Get user with invalid ID | Logged in | 1. Send GET with non-existent UUID | `GET /api/iam/users/00000000-0000-0000-0000-000000000000` | None | **404 Not Found** | ⬜ |
| TC-015 | 🔴 P0 | Create new user | Logged in + tenant ID | 1. Send POST with user details | `POST /api/iam/users/{tenantId}` | `{"userName":"john.doe","firstName":"John","lastName":"Doe","email":"john@test.com","phoneNumber":"+1234567890","role":"USER"}` | **200/201** — Created user returned with generated ID | ⬜ |
| TC-016 | 🟠 P1 | Create user — duplicate email | Logged in + existing email | 1. Send POST with already-used email | `POST /api/iam/users/{tenantId}` | Same email as existing user | **400/409 Conflict** — "already exists" message | ⬜ |
| TC-017 | 🟠 P1 | Create user — missing required fields | Logged in | 1. Send POST with empty body | `POST /api/iam/users/{tenantId}` | `{"userName":""}` | **400 Bad Request** — Validation error | ⬜ |
| TC-018 | 🟠 P1 | Update existing user | Logged in + valid user ID | 1. Send PUT with updated fields | `PUT /api/iam/users/{userId}` | `{"firstName":"Updated","lastName":"Name"}` | **200 OK** — Updated user returned | ⬜ |
| TC-019 | 🟠 P1 | Delete user | Logged in + user ID to delete | 1. Send DELETE 2. Try GET same user | `DELETE /api/iam/users/{userId}` | None | **200/204** — User removed. Subsequent GET returns 404 | ⬜ |
| TC-020 | 🟡 P2 | Check username/email/phone uniqueness | Logged in | 1. Send GET with values to check | `GET /api/iam/users/check?userName=x&phoneNumber=y&email=z` | Query params | **200 OK** — Indicates availability of each field | ⬜ |
| TC-021 | 🟡 P2 | Resend verification email | Logged in + user ID | 1. Send POST to resend | `POST /api/iam/users/tenants/{tenantId}/users/{userId}/resend-verification` | None | **200 OK** — Email sent confirmation | ⬜ |

---

## Module 3: Role & Scope Management

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Request Body / Params | Expected Result | Status |
|---|---|---|---|---|---|---|---|---|
| TC-030 | 🟠 P1 | Get all roles | Logged in | 1. Send GET | `GET /api/iam/roles` | None | **200 OK** — Array of roles with `roleName`, `description`, `scopes[]` | ⬜ |
| TC-031 | 🟠 P1 | Create new role | Logged in | 1. Send POST with role details | `POST /api/iam/roles` | `{"roleName":"Analyst","description":"Security analyst","scopes":[]}` | **200/201** — Role created | ⬜ |
| TC-032 | 🟡 P2 | Get role by ID | Logged in + role ID | 1. Send GET with ID | `GET /api/iam/roles/{roleId}` | None | **200 OK** — Single role with full scope list | ⬜ |
| TC-033 | 🟡 P2 | Update role | Logged in + role ID | 1. Send PUT with new name | `PUT /api/iam/roles/{roleId}` | `{"roleName":"Updated Analyst","description":"Updated"}` | **200 OK** — Updated role returned | ⬜ |
| TC-034 | 🟡 P2 | Activate role | Logged in + role ID | 1. Send POST to activate | `POST /api/iam/roles/activate?roleId={id}` | Query: `roleId` | **200 OK** — Role activated | ⬜ |
| TC-040 | 🟠 P1 | Get all scopes | Logged in | 1. Send GET | `GET /api/iam/scopes` | None | **200 OK** — Array of scopes with `scopeName`, `menu`, `subMenu` | ⬜ |
| TC-041 | 🟡 P2 | Get scopes filtered by menu | Logged in | 1. Send GET with menu path | `GET /api/iam/scopes/menu/SECURITY` | None | **200 OK** — All scopes have `menu=SECURITY` | ⬜ |
| TC-042 | 🟡 P2 | Create new scope | Logged in | 1. Send POST | `POST /api/iam/scopes` | `{"scopeName":"view_events","displayName":"View Events","menu":"SECURITY","subMenu":"EVENTS"}` | **200/201** — Scope created | ⬜ |

---

## Module 4: Reference Data (Dropdowns)

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-060 | 🟠 P1 | Get all regions | Logged in | 1. Send GET | `GET /api/iam/regions` | **200 OK** — Non-empty array. Response < 500ms | ⬜ |
| TC-061 | 🟡 P2 | Get countries by region | Logged in + region ID | 1. Send GET with regionId | `GET /api/iam/countries?regionId={id}` | **200 OK** — Countries array | ⬜ |
| TC-062 | 🟡 P2 | Get states by country | Logged in + country ID | 1. Send GET | `GET /api/iam/states?countryId={id}` | **200 OK** — States array | ⬜ |
| TC-063 | 🟡 P2 | Get cities by state | Logged in + state ID | 1. Send GET | `GET /api/iam/cities?stateId={id}` | **200 OK** — Cities array | ⬜ |
| TC-064 | 🟠 P1 | Get all industries | Logged in | 1. Send GET | `GET /api/iam/industries` | **200 OK** — Non-empty array | ⬜ |
| TC-065 | 🟡 P2 | Get billing types | Logged in | 1. Send GET | `GET /api/iam/tenants/billing` | **200 OK** | ⬜ |
| TC-066 | 🟡 P2 | Get tenant types | Logged in | 1. Send GET | `GET /api/iam/tenants/types` | **200 OK** | ⬜ |
| TC-067 | 🟡 P2 | Get package types | Logged in | 1. Send GET | `GET /api/iam/package-types` | **200 OK** | ⬜ |
| TC-068 | 🟡 P2 | Get billing cycles | Logged in | 1. Send GET | `GET /api/iam/billing-cycles` | **200 OK** — Non-empty array | ⬜ |

---

## Module 5: Features, Packages & Entitlements

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Request Body | Expected Result | Status |
|---|---|---|---|---|---|---|---|---|
| TC-070 | 🟠 P1 | Get all features | Logged in | 1. Send GET | `GET /api/iam/features` | — | **200 OK** — Array with `featureCode`, `featureName`, `isActive` | ⬜ |
| TC-071 | 🟠 P1 | Get all packages | Logged in | 1. Send GET | `GET /api/iam/packages` | — | **200 OK** — Non-empty array (Basic, Pro, Enterprise, etc.) | ⬜ |
| TC-072 | 🟠 P1 | Get feature matrix (all packages) | Logged in | 1. Send GET | `GET /api/iam/package-feature-mappings/matrices` | — | **200 OK** — Matrix of packages × features | ⬜ |
| TC-073 | 🟡 P2 | Get feature matrix for single package | Logged in + package ID | 1. Send GET | `GET /api/iam/package-feature-mappings/matrix/{packageId}` | — | **200 OK** — Feature list with access levels | ⬜ |
| TC-074 | 🟡 P2 | Check feature access for package | Logged in | 1. Send GET with package + feature code | `GET /api/iam/package-feature-mappings/check-access?packageId={id}&featureCode=BROWSER_EVENTS` | — | **200 OK** — Boolean or access details | ⬜ |
| TC-075 | 🟡 P2 | Get access levels list | Logged in | 1. Send GET | `GET /api/iam/access-levels` | — | **200 OK** — Non-empty array | ⬜ |
| TC-076 | 🟡 P2 | Get retention periods list | Logged in | 1. Send GET | `GET /api/iam/retention-periods` | — | **200 OK** | ⬜ |

---

## Module 6: Subscription Management

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-080 | 🔴 P0 | Get active subscription for tenant | Logged in + tenant ID | 1. Send GET | `GET /api/iam/subscriptions/tenant/{tenantId}` | **200 OK** — Active subscription with `packageName`, `status`, `startDate`, `endDate` | ⬜ |
| TC-081 | 🟠 P1 | Get subscription history | Logged in + tenant ID | 1. Send GET | `GET /api/iam/subscriptions/tenant/{tenantId}/history` | **200 OK** — Array of past subscriptions | ⬜ |
| TC-082 | 🟠 P1 | Get all package pricing | Logged in | 1. Send GET | `GET /api/iam/subscriptions/pricing/packages` | **200 OK** — Packages with pricing tiers | ⬜ |
| TC-083 | 🟡 P2 | Get addon pricing | Logged in | 1. Send GET | `GET /api/iam/subscriptions/pricing/addons` | **200 OK** — Addon features with prices | ⬜ |
| TC-084 | 🟡 P2 | Get tenant billing summary | Logged in + tenant ID | 1. Send GET | `GET /api/iam/tenant-addon-features/tenant/{tenantId}/billing-summary` | **200 OK** — Billing breakdown | ⬜ |
| TC-085 | 🟡 P2 | Get tenant feature access | Logged in + tenant ID | 1. Send GET | `GET /api/iam/tenant-features/{tenantId}` | **200 OK** — All features with access flags | ⬜ |

---

## Module 7: Events Groups & Policy

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Request Body | Expected Result | Status |
|---|---|---|---|---|---|---|---|---|
| TC-090 | 🟠 P1 | Get all events groups | Logged in | 1. Send GET | `GET /api/iam/events-groups?authorizedOnly=false&includePolicies=true` | — | **200 OK** — Array of groups with `name`, `groupType`, `authorized`, policies | ⬜ |
| TC-091 | 🟠 P1 | Create APIKEY group | Logged in | 1. Send POST | `POST /api/iam/events-groups` | `{"name":"Eng Team","description":"Engineering"}` | **200/201** — Group created with ID | ⬜ |
| TC-092 | 🟡 P2 | Authorize a group | Logged in + group ID | 1. Send PUT | `PUT /api/iam/events-groups/groups/{groupId}/authorize` | — | **200 OK** — `authorized=true` | ⬜ |
| TC-093 | 🟡 P2 | Assign device users to group | Logged in + group ID + user IDs | 1. Send POST with user IDs | `POST /api/iam/events-groups/{groupId}/device-users` | `{"deviceUserIds":["id1","id2"]}` | **200 OK** — Users assigned | ⬜ |
| TC-094 | 🟡 P2 | Get group membership stats | Logged in | 1. Send GET | `GET /api/iam/events-groups/stats/memberships` | — | **200 OK** — Stats per group | ⬜ |
| TC-095 | 🟡 P2 | Get policies for device user | Logged in + device user ID | 1. Send GET | `GET /api/iam/events-groups/device-users/{id}/policies` | — | **200 OK** — Browser, network, extension policies | ⬜ |
| TC-096 | 🟡 P2 | Get group history (audit trail) | Logged in | 1. Send GET | `GET /api/iam/events-groups/history` | — | **200 OK** — History events array | ⬜ |

---

## Module 8: SSO Configuration

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-100 | 🟠 P1 | List all SSO configs | Logged in | 1. Send GET | `GET /api/iam/sso-configurations` | **200 OK** — Array of SSO configs | ⬜ |
| TC-101 | 🟡 P2 | Create SSO config | Logged in | 1. Send POST with SSO details | `POST /api/iam/sso-configurations` | **200/201** — Config created | ⬜ |
| TC-102 | 🟡 P2 | Activate SSO config | Logged in + config ID | 1. Send PUT | `PUT /api/iam/sso-configurations/{id}/activate` | **200 OK** — Config activated | ⬜ |
| TC-103 | 🟡 P2 | Search Azure AD groups | Logged in + Azure configured | 1. Send GET with search term | `GET /api/iam/sso-configurations/groups?search=engineering` | **200 OK** — Matching Azure groups | ⬜ |

---

## Module 9: Login Audit & Device Tracking

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-110 | 🟠 P1 | Get paginated audit events | Logged in | 1. Send GET with pagination | `GET /api/iam/audit/login/events?page=0&size=10` | **200 OK** — Paginated audit events with `eventType`, `userId`, `timestamp` | ⬜ |
| TC-111 | 🟡 P2 | Get audit events by user | Logged in + user ID | 1. Send GET | `GET /api/iam/audit/login/events/user/{userId}` | **200 OK** — Events for that user only | ⬜ |
| TC-112 | 🟡 P2 | Get audit events by time range | Logged in | 1. Send GET with start/end | `GET /api/iam/audit/login/events/timerange?start=2024-01-01T00:00:00&end=2027-12-31T23:59:59` | **200 OK** — Events within range | ⬜ |
| TC-113 | 🟠 P1 | Get all tenant devices | Logged in | 1. Send GET | `GET /api/iam/audit/login/devices` | **200 OK** — Array of devices with `fingerprint`, `deviceName`, `status` | ⬜ |
| TC-114 | 🟡 P2 | Search devices | Logged in | 1. Send GET with search term | `GET /api/iam/audit/login/devices/search?q=Chrome` | **200 OK** — Matching devices | ⬜ |
| TC-115 | 🟡 P2 | Get device stats | Logged in | 1. Send GET with date range | `GET /api/iam/audit/login/devices/stats?start=2024-01-01T00:00:00&end=2027-12-31T23:59:59` | **200 OK** — Stats summary | ⬜ |
| TC-116 | 🟡 P2 | Trust a device | Logged in + user ID + fingerprint | 1. Send POST | `POST /api/iam/audit/login/devices/user/{userId}/trust?fingerprint=abc123` | **200 OK** — Device trusted | ⬜ |
| TC-117 | 🟡 P2 | Block a device | Logged in + user ID + fingerprint | 1. Send POST | `POST /api/iam/audit/login/devices/user/{userId}/block?fingerprint=abc123` | **200 OK** — Device blocked | ⬜ |

---

## Module 10: Tenant Sessions (Keycloak)

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-120 | 🟠 P1 | Get all active sessions | Logged in | 1. Send GET | `GET /api/tenants/sessions` | **200 OK** — Active sessions list | ⬜ |
| TC-121 | 🟡 P2 | Get session count | Logged in | 1. Send GET | `GET /api/tenants/sessions/count` | **200 OK** — Numeric count | ⬜ |
| TC-122 | 🟠 P1 | Get my sessions | Logged in | 1. Send GET | `GET /api/tenants/sessions/me` | **200 OK** — Current user's sessions (≥1) | ⬜ |
| TC-123 | 🟡 P2 | Logout current session | Logged in | 1. Send DELETE | `DELETE /api/tenants/sessions/me/current` | **200/204** — Current session terminated | ⬜ |
| TC-124 | 🟡 P2 | Logout other sessions | Logged in with multiple sessions | 1. Send DELETE | `DELETE /api/tenants/sessions/me/others` | **200 OK** — Other sessions killed, current remains | ⬜ |
| TC-125 | 🟡 P2 | Kill specific user's sessions (admin) | Logged in as admin | 1. Send DELETE with target user ID | `DELETE /api/tenants/sessions/user/{userId}` | **200 OK** — Target user's sessions ended | ⬜ |

---

## Module 11: Policies (Browser / Network / Extension)

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-130 | 🟠 P1 | Get all extension policies | Logged in | 1. Send GET | `GET /api/tenants/extension-policy` | **200 OK** — Policies array | ⬜ |
| TC-131 | 🟡 P2 | Create extension policy | Logged in | 1. Send POST | `POST /api/tenants/extension-policy` | **200/201** — Policy created | ⬜ |
| TC-132 | 🟠 P1 | Get policy mappings | Logged in | 1. Send GET | `GET /api/tenants/policy/mapping` | **200 OK** — Group-to-policy mappings | ⬜ |
| TC-133 | 🟡 P2 | Map policy to groups | Logged in + policy ID + group IDs | 1. Send POST | `POST /api/tenants/policy/mapping/groups` | **200 OK** — Mapping created | ⬜ |
| TC-134 | 🟡 P2 | Get SMTP config | Logged in | 1. Send GET | `GET /api/tenants/smtp-config` | **200 OK** or **404** (if not configured) | ⬜ |
| TC-135 | 🟡 P2 | Test SMTP connection | Logged in + SMTP details | 1. Send POST with SMTP creds | `POST /api/tenants/smtp-config/test` | **200 OK** — Connection success/failure result | ⬜ |

---

## Module 12: Events — Core Browser Events

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-140 | 🔴 P0 | Get events (paginated) | Logged in | 1. Send GET with page params | `GET /api/events?page=0&size=20` | **200 OK** — Paginated events. Response < 5s | ⬜ |
| TC-141 | 🟠 P1 | Get event summary | Logged in | 1. Send GET | `GET /api/events/summary` | **200 OK** — Aggregated event data | ⬜ |
| TC-142 | 🟡 P2 | Get events by device | Logged in + device ID | 1. Send GET | `GET /api/events/device/{deviceId}` | **200 OK** — Events for that device | ⬜ |
| TC-143 | 🟡 P2 | Get events by user | Logged in + username | 1. Send GET | `GET /api/events/user/{userName}` | **200 OK** — Events for that user | ⬜ |
| TC-144 | 🟠 P1 | Search events | Logged in | 1. Send GET with search term | `GET /api/events/search?q=malware&page=0&size=10` | **200 OK** — Matching events | ⬜ |
| TC-145 | 🟡 P2 | Get event categories | Logged in | 1. Send GET | `GET /api/events/categories` | **200 OK** — Category list | ⬜ |
| TC-146 | 🟡 P2 | Get event count | Logged in | 1. Send GET | `GET /api/events/count` | **200 OK** — Total count number | ⬜ |

---

## Module 13: Security Events & Incidents

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-150 | 🔴 P0 | Get all security events | Logged in | 1. Send GET | `GET /api/events/security` | **200 OK** — Security events with `severity`, `threatType`, `riskLevel` | ⬜ |
| TC-151 | 🔴 P0 | Get critical security events | Logged in | 1. Send GET | `GET /api/events/security/critical` | **200 OK** — Only events with `severity=CRITICAL` | ⬜ |
| TC-152 | 🟠 P1 | Filter by severity | Logged in | 1. Send GET | `GET /api/events/security/severity/HIGH` | **200 OK** — Only HIGH severity events | ⬜ |
| TC-153 | 🟠 P1 | Filter by threat type | Logged in | 1. Send GET | `GET /api/events/security/threat-type/MALWARE` | **200 OK** — Only MALWARE events | ⬜ |
| TC-154 | 🟠 P1 | Get security dashboard | Logged in | 1. Send GET | `GET /api/events/security/dashboard` | **200 OK** — Dashboard data. Response < 5s | ⬜ |
| TC-155 | 🟡 P2 | Get security stats | Logged in | 1. Send GET | `GET /api/events/security/stats` | **200 OK** — Statistics summary | ⬜ |
| TC-156 | 🟡 P2 | Get security event count | Logged in | 1. Send GET | `GET /api/events/security/count` | **200 OK** — Numeric count | ⬜ |

---

## Module 14: Extension API Keys

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Request Body | Expected Result | Status |
|---|---|---|---|---|---|---|---|---|
| TC-160 | 🟠 P1 | Get all API keys | Logged in | 1. Send GET | `GET /api/events/extension-api-keys` | — | **200 OK** — Array of keys with `name`, `status`, `expiresAt` | ⬜ |
| TC-161 | 🟠 P1 | Create API key | Logged in | 1. Send POST | `POST /api/events/extension-api-keys` | `{"name":"Prod Key","description":"Production","expiresInDays":365}` | **200/201** — Key created, plaintext key returned **once** | ⬜ |
| TC-162 | 🟡 P2 | Revoke API key | Logged in + key ID | 1. Send POST | `POST /api/events/extension-api-keys/{keyId}/revoke` | — | **200 OK** — Key status = REVOKED | ⬜ |
| TC-163 | 🟡 P2 | Rotate API key | Logged in + key ID | 1. Send POST | `POST /api/events/extension-api-keys/{keyId}/rotate` | — | **200 OK** — New key returned, old one invalidated | ⬜ |
| TC-164 | 🟡 P2 | Get API key stats | Logged in | 1. Send GET | `GET /api/events/extension-api-keys/stats` | — | **200 OK** — Active/revoked/expired counts | ⬜ |
| TC-165 | 🟡 P2 | Get key rotation history | Logged in + key ID | 1. Send GET | `GET /api/events/extension-api-keys/{keyId}/rotation-history` | — | **200 OK** — History of rotations | ⬜ |

---

## Module 15: Escalation & User Activity

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-170 | 🟠 P1 | Get escalation rules | Logged in | 1. Send GET | `GET /api/events/escalation-rules` | **200 OK** — Rules array | ⬜ |
| TC-171 | 🟡 P2 | Create escalation rule | Logged in | 1. Send POST | `POST /api/events/escalation-rules` | **200/201** — Rule created | ⬜ |
| TC-172 | 🟠 P1 | Get user activity | Logged in | 1. Send GET | `GET /api/events/user-activity` | **200 OK** — Activity log | ⬜ |
| TC-173 | 🟡 P2 | Get activity stats | Logged in | 1. Send GET | `GET /api/events/user-activity/stats` | **200 OK** — Stats | ⬜ |
| TC-174 | 🟡 P2 | Search user activity | Logged in | 1. Send GET with q | `GET /api/events/user-activity/search?q=login` | **200 OK** — Matching entries | ⬜ |

---

## Module 16: Security & Penetration Tests

| ID | Priority | Test Case | Precondition | Steps | Method & URL | Expected Result | Status |
|---|---|---|---|---|---|---|---|
| TC-180 | 🔴 P0 | Access protected API without token | None | 1. Send GET to `/api/iam/users` without Authorization header | `GET /api/iam/users` | **401 Unauthorized** — No data leaked | ⬜ |
| TC-181 | 🔴 P0 | Access with malformed token | None | 1. Send GET with `Authorization: Bearer garbage` | `GET /api/iam/roles` | **401 Unauthorized** | ⬜ |
| TC-182 | 🔴 P0 | Access with expired token | Expired JWT | 1. Use a JWT that has expired | `GET /api/iam/users` | **401 Unauthorized** | ⬜ |
| TC-183 | 🟠 P1 | Cross-tenant data access | Logged in as Tenant A | 1. Try to access Tenant B's users | `GET /api/iam/users/tenant/{tenantB_id}` | **403 Forbidden** or empty array — no cross-tenant leak | ⬜ |
| TC-184 | 🟠 P1 | SQL injection in query params | Logged in | 1. Send `userName=admin'--` | `GET /api/iam/users/check?userName=admin'--` | No SQL error exposed in response body | ⬜ |
| TC-185 | 🟠 P1 | XSS in request body | Logged in | 1. Send `<script>` in roleName | `POST /api/iam/roles` with `{"roleName":"<script>alert(1)</script>"}` | `<script>` tag NOT reflected in response | ⬜ |
| TC-186 | 🟡 P2 | Large payload (DoS check) | Logged in | 1. Send POST with 10MB body | `POST /api/iam/users/{tenantId}` | **413 Payload Too Large** or graceful rejection | ⬜ |

---

## Module 17: Performance Benchmarks

| ID | Priority | Test Case | Precondition | Steps | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-190 | 🟡 P2 | Dropdown APIs < 500ms | Logged in | 1. Time GET requests for regions, industries, billing types | All return **< 500ms** | ⬜ |
| TC-191 | 🟡 P2 | List APIs < 3s | Logged in | 1. Time GET requests for users, roles, events | All return **< 3000ms** | ⬜ |
| TC-192 | 🟡 P2 | Dashboard APIs < 5s | Logged in | 1. Time GET for security/dashboard | Returns **< 5000ms** | ⬜ |
| TC-193 | 🟡 P2 | Pagination works correctly | Logged in | 1. GET events with `page=0&size=5` 2. GET with `page=1&size=5` | Results are different between pages, no overlap | ⬜ |

---

## Summary

| Module | Test Cases | P0 | P1 | P2 | P3 |
|---|---|---|---|---|---|
| Authentication | 9 | 3 | 4 | 2 | 0 |
| User Management | 12 | 3 | 5 | 4 | 0 |
| Roles & Scopes | 8 | 0 | 3 | 5 | 0 |
| Dropdowns | 9 | 0 | 2 | 7 | 0 |
| Features & Packages | 7 | 0 | 2 | 5 | 0 |
| Subscriptions | 6 | 1 | 2 | 3 | 0 |
| Events Groups | 7 | 0 | 2 | 5 | 0 |
| SSO Config | 4 | 0 | 1 | 3 | 0 |
| Login Audit | 8 | 0 | 2 | 6 | 0 |
| Sessions | 6 | 0 | 2 | 4 | 0 |
| Policies | 6 | 0 | 2 | 4 | 0 |
| Core Events | 7 | 1 | 2 | 4 | 0 |
| Security Events | 7 | 2 | 3 | 2 | 0 |
| API Keys | 6 | 0 | 2 | 4 | 0 |
| Escalation/Activity | 5 | 0 | 2 | 3 | 0 |
| Security Tests | 7 | 3 | 3 | 1 | 0 |
| Performance | 4 | 0 | 0 | 4 | 0 |
| **Total** | **118** | **13** | **39** | **64** | **2** |
