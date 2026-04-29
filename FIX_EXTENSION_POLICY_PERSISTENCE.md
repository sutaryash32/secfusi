# Extension Policy Persistence Fix

## Fix Report: Extension Policy Persistence Issue

### 1. Problem Overview

The `Browser Extension Management` section within the Extension Policy was failing to persist its configuration. While the policy itself was saved, specific sub-fields always returned to their default values after creation or update.

Affected fields:

- `extensionPolicyType` (saved as `action` in DB)
- `enforcementAction`
- `warningMessage`

### 2. Root Cause Analysis

The investigation revealed four primary issues:

1. Naming mismatch:
   The frontend sent `extensionPolicyType`, but the backend entity expected `action`. Without Jackson mapping, the value was ignored.
2. Enum persistence:
   The `action` enum was missing `@Enumerated(EnumType.STRING)`, causing it to be saved as an integer ordinal, which led to inconsistent reading.
3. Missing DB mapping:
   Columns like `enforcement_action` and `warning_message` were not explicitly mapped in the JPA entity, leading to default Hibernate behavior that sometimes failed to bind the values.
4. Incomplete logic:
   The service layer was not initializing `ManagedExtension` objects for default policies and was missing cloning logic for these specific fields during versioned updates.
This means two things:

1. Default policy flow:
   the parent `ExtensionPolicy` row could be created, but the child `ManagedExtension` block was not always initialized correctly.
   So the child section could be null, empty, or appear with default values later.

2. Versioned `PUT` flow:
   the service does not update the same row in place.
   It creates a new parent policy row and a new child `ManagedExtension` row.
   If the incoming child values are not copied into that new child row, then the new row is created with defaults instead of the request values.

In short:
- default flow: child block was effectively ignored or not initialized properly
- update flow: new DB row was created, but child values were not fully copied into the new child row

That is why:
- parent policy save looked successful
- but nested Browser Extension Management values did not persist correctly
- parent row = `extension_policy`
- child row = `managed_extensions`

Buggy behavior was like:

- save parent correctly
- create child incorrectly
- or recreate child with defaults instead of request values



### 3. Technical Changes

#### A. Entity Layer (`ManagedExtension.java`)

- Added `@Enumerated(EnumType.STRING)` to ensure the policy type is saved as a readable string.
- Added `@JsonProperty` and `@JsonAlias` for `extensionPolicyType` to bridge the gap between frontend and backend naming.
- Added explicit `@Column` mappings and default values.

Here, Jackson is responsible for reading the incoming API field names and mapping them to the correct backend fields even when the frontend and backend names are slightly different.

Difference between the two:
Jackson is the library Spring Boot uses to convert JSON into Java objects and Java objects back into JSON.

- `@JsonProperty("managedExtension")`
  - defines the main JSON name
  - affects request/response mapping
- `@JsonAlias("managedExtensions")`
  - accepts extra input names
  - mainly helps when old clients send older field names

Why it mattered here:

- frontend/backend had naming drift
- one side used `managedExtension`
- another used `managedExtensions`
- one nested field was exposed as `extensionPolicyType`, but DB/entity field was `action`

#### B. DTO Layer (`ManagedExtensionDto.java` and `ManagedExtensionResponseDto.java`)

- Added `@JsonProperty("extensionPolicyType")` to ensure the API request and response bodies match the frontend's expectations exactly.

#### C. Service Layer (`ExtensionPolicyService.java`)

- Initialization:
  Updated `createDefaultPolicyIfNotExists` to create a default `ManagedExtension` object so policies are not created with null sections.
- Cloning:
  Updated `createTenantDefaultPolicy` to properly clone extension settings from the global default to the tenant default.
- Updates:
  Ensured that versioned updates correctly carry over or override the extension management fields.

### 4. Verification Summary

| Feature | Before Fix | After Fix |
|---|---|---|
| Field Mapping | Ignored (name mismatch) | Success (Jackson aliases) |
| Persistence | Saved as integer/null | Success (string enums) |
| Default Policies | Empty/missing section | Success (auto-initialized) |
| API Response | Returned `action` | Success (returns `extensionPolicyType`) |

## What We Did So Far

### Problem Observed

- `POST /api/tenants/extension-policy` created a policy record successfully.
- `PUT /api/tenants/extension-policy/{id}` also updated the policy row successfully.
- But the `Browser Extension Management` block was not being preserved correctly.
- On a later `GET`, these fields fell back to defaults instead of returning the saved values:
  - `managedExtension.extensionPolicyType`
  - `managedExtension.enforcementAction`
  - `managedExtension.warningMessage`

### What Existed Before

- The extension policy entity exposed the nested block as `managedExtensions` on the backend side.
- Browser policy used the singular name `managedExtension`.
- The extension repository queries also fetched `managedExtensions`.
- The extension update flow read from `getManagedExtensions()` and wrote back with `setManagedExtensions(...)`.
- Because the API contract and backend field naming drifted, the managed-extension payload did not line up cleanly across create, update, and fetch flows.
- Default values from `ManagedExtension` were therefore the values most often seen again on a later fetch:
  - `extensionPolicyType = ALLOW_ALL`
  - `enforcementAction = WARN_USER`
  - default warning message

  ### How the Old Behavior Affected These Test Payloads

Using the following request bodies against the pre-fix implementation would most likely not throw an immediate server error:

- `POST /api/tenants/extension-policy`
- `PUT /api/tenants/extension-policy/{id}`

However, they would fail functionally because the nested managed-extension payload did not bind and persist consistently.

For the create payload:

```json
{
  "name": "Custom Extension Policy",
  "description": "Create request for extension persistence verification",
  "landingPageUrl": "https://example.com/extension-policy/create",
  "managedExtension": {
    "extensionPolicyType": "BLOCK_ALL",
    "enforcementAction": "BLOCK_BROWSER",
    "warningMessage": "Custom create-time warning message.",
    "extensions": []
  }
}
```

Likely old behavior:

- request returns `200 OK` or `201 Created`
- policy row is created
- later `GET` may return default managed-extension values instead of the submitted values

For the update payload:

```json
{
  "name": "Default Extension Policy",
  "description": "Updated to block all browser extensions",
  "landingPageUrl": "https://example.com/extension-policy/block-all",
  "managedExtension": {
    "extensionPolicyType": "BLOCK_ALL",
    "enforcementAction": "BLOCK_BROWSER",
    "warningMessage": "All browser extensions are blocked by policy.",
    "extensions": []
  }
}
```

Likely old behavior:

- request returns `200 OK`
- versioned update still creates a new policy row
- the new row may carry default managed-extension values rather than the submitted payload values

In short, before the fix these test cases were expected to run, but fail persistence verification rather than fail with a hard runtime error.


### What Was Fixed

- `ExtensionPolicy` was aligned to the browser-style API contract:
  - backend field is now `managedExtension`
  - `@JsonProperty("managedExtension")` was added
  - `@JsonAlias("managedExtensions")` was added for backward compatibility
- `ExtensionPolicyService` update flow was aligned to use:
  - `getManagedExtension()`
  - `setManagedExtension(...)`
- `ExtensionPolicyRepository` fetch queries were aligned to:
  - `LEFT JOIN FETCH e.managedExtension`
- Default policy creation now initializes a `ManagedExtension` block explicitly so the default policy is complete and predictable.

## Functionality Implemented In Code

- The backend contract was aligned to use `managedExtension`, while still accepting `managedExtensions` for backward compatibility.
- `extensionPolicyType` now maps explicitly to backend field `action`, and managed-extension persistence fields are mapped cleanly in the entity.
- Repository fetch queries were aligned to load `managedExtension` consistently.
- The versioned `PUT` flow now clones `ManagedExtension` values correctly into the new policy version.
- Global default and tenant-default policy creation now ensure a complete `ManagedExtension` block exists.
- Default `enforcementAction` and `warningMessage` values were moved from hardcoded service logic into config.

### Files Touched So Far

- `sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/entity/ExtensionPolicy.java`
- `sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/service/ExtensionPolicyService.java`
- `sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/repository/ExtensionPolicyRepository.java`
- `sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/dto/ExtensionPolicyRequestDto.java`
- `FIX_EXTENSION_POLICY_PERSISTENCE.md`

### Current Expected Contract

Use this nested JSON block in requests:

```json
{
  "managedExtension": {
    "extensionPolicyType": "BLOCK_ALL",
    "enforcementAction": "BLOCK_BROWSER",
    "warningMessage": "Custom message here",
    "extensions": []
  }
}
```

## Test Target

- `pkExtensionPolicyId`: `844dd67d-e3cf-43a8-ac65-3ea440422d9b`
- Base endpoint: `PUT /api/tenants/extension-policy/{id}`
- Replace `{{base_url}}` and `{{auth_token}}` with your environment values.

## Postman Test Checklist

Run these in order:

1. `POST /api/tenants/extension-policy`
   Confirm a new policy is created with a non-null `pkExtensionPolicyId`.
2. `GET /api/tenants/extension-policy/{id}`
   Confirm the saved `managedExtension` block is returned.
3. `PUT /api/tenants/extension-policy/844dd67d-e3cf-43a8-ac65-3ea440422d9b`
   Use one of the update payloads below.
4. `GET /api/tenants/extension-policy/844dd67d-e3cf-43a8-ac65-3ea440422d9b`
   Confirm the updated values are still present.

## Example Postman Create Request

Use this to create a fresh extension policy with explicit Browser Extension Management values:

```http
POST {{base_url}}/api/tenants/extension-policy
Authorization: Bearer {{auth_token}}
Content-Type: application/json
```

```json
{
  "name": "Custom Extension Policy",
  "description": "Create request for extension persistence verification",
  "landingPageUrl": "https://example.com/extension-policy/create",
  "managedExtension": {
    "extensionPolicyType": "BLOCK_ALL",
    "enforcementAction": "BLOCK_BROWSER",
    "warningMessage": "Custom create-time warning message.",
    "extensions": []
  }
}
```

Expected:

- Response status: `201 Created` or `200 OK`
- Response contains `managedExtension.extensionPolicyType = BLOCK_ALL`
- Response contains `managedExtension.enforcementAction = BLOCK_BROWSER`
- Response contains `managedExtension.warningMessage = Custom create-time warning message.`

## Example Postman Read Request

```http
GET {{base_url}}/api/tenants/extension-policy/844dd67d-e3cf-43a8-ac65-3ea440422d9b
Authorization: Bearer {{auth_token}}
```

Expected:

- Response status: `200 OK`
- Response body includes `managedExtension`
- Response body shows the last persisted values, not the defaults, unless defaults were actually saved

## Important: Versioned Update Behavior

Extension Policy updates are versioned. A `PUT` does not update the existing row in place.

What happens on update:

1. The old policy row is marked inactive.
2. A new policy row is created.
3. The new row gets a new `pkExtensionPolicyId`.
4. The `policyKey` stays the same across versions.

Example:

- Old version:
  - `pkExtensionPolicyId = 844dd67d-e3cf-43a8-ac65-3ea440422d9b`
  - `version = 0.1`
  - `isActive = false`
- New version returned by `PUT`:
  - `pkExtensionPolicyId = cf22619a-0b58-4e95-9d6b-020d0a4eb665`
  - `version = 0.2`
  - `isActive = true`

## How To Know the New Policy ID After PUT

Read it from the `PUT` response body.

Example response snippet:

```json
{
  "pkExtensionPolicyId": "cf22619a-0b58-4e95-9d6b-020d0a4eb665",
  "policyKey": "620224c1-90d5-4055-8140-7d8bdaaa4873",
  "version": "0.2",
  "isActive": true
}
```

Use that new ID for the next `GET`:

```http
GET {{base_url}}/api/tenants/extension-policy/cf22619a-0b58-4e95-9d6b-020d0a4eb665
Authorization: Bearer {{auth_token}}
```

Do not fetch the old ID after a successful update if you want the latest version.

## Recommended Tracking Strategy

- Use `pkExtensionPolicyId` when you want one exact saved version.
- Use `policyKey` when you want to identify the same logical policy across versions.
- After every successful `PUT`, store the returned `pkExtensionPolicyId` and use that for the next fetch.

## Database Save Locations

The Extension Policy and its Browser Extension Management data are not saved into a single flat table. They are persisted across related tables.

### 1. Main Policy Row

Table:

- `extension_policy`

Important columns:

- `pk_extension_policy_id`
- `fk_tenant_id`
- `name`
- `description`
- `policy_key`
- `version`
- `is_active`
- `is_tenant_default`
- `landing_page_url`
- `fk_landingpage_id`
- `fk_managed_extensions_id`
- `created_at`
- `updated_at`

This is the main parent row for the policy.

### 2. Browser Extension Management Block

Table:

- `managed_extensions`

Important columns:

- `pk_managed_extension_id`
- `action`
- `enforcement_action`
- `warning_message`
- `created_at`

This table stores the nested `managedExtension` object.

Field mapping:

- API `managedExtension.extensionPolicyType` -> DB `managed_extensions.action`
- API `managedExtension.enforcementAction` -> DB `managed_extensions.enforcement_action`
- API `managedExtension.warningMessage` -> DB `managed_extensions.warning_message`





## DB Interpretation

For a request like:

```json
{
  "managedExtension": {
    "extensionPolicyType": "ALLOW_LIST",
    "enforcementAction": "WARN_USER",
    "warningMessage": "Only approved extensions are allowed for this tenant.",
    "extensions": [
      {
        "extensionId": "ghbmnnjooekpmoecnnnilnnbdlolhkhi",
        "extensionName": "Google Docs Offline",
        "publisher": "Google"
      }
    ]
  }
}
```

the expected DB write locations are:

- `extension_policy.fk_managed_extensions_id` -> points to one row in `managed_extensions`
- `managed_extensions.action = ALLOW_LIST`
- `managed_extensions.enforcement_action = WARN_USER`
- `managed_extensions.warning_message = Only approved extensions are allowed for this tenant.`
- one or more rows in `extension_detail` linked to that managed extension row



### DB Mapping

#### Table: `extension_policy`

| Column | Value Saved |
|---|---|
| `pk_extension_policy_id` | `844dd67d-e3cf-43a8-ac65-3ea440422d9b` |
| `fk_tenant_id` | `713bbd8a-b5f4-4543-983a-6bfd587af923` |
| `name` | `Strict Extension Policy` |
| `description` | `NULL` |
| `policy_key` | `620224c1-90d5-4055-8140-7d8bdaaa4873` |
| `version` | `0.1` |
| `is_active` | `true` |
| `fk_managed_extensions_id` | `a8836e3c-412e-4c4b-acbc-e3de82a62e94` |
| `landing_page_url` | `NULL` |
| `fk_landingpage_id` | `NULL` |
| `created_at` | `2026-04-15T20:25:53.6525039` |
| `updated_at` | `2026-04-15T20:25:53.6525039` |

#### Table: `managed_extensions`

| Column | Value Saved |
|---|---|
| `pk_managed_extension_id` | `a8836e3c-412e-4c4b-acbc-e3de82a62e94` |
| `action` | `BLOCK_ALL` |
| `enforcement_action` | `BLOCK_BROWSER` |
| `warning_message` | `Extensions are strictly prohibited on this corporate device.` |
| `created_at` | `2026-04-15T20:25:53.6525039` |

#### Join Relationship

| Parent Table | Parent Column | Child Table | Child Column | Meaning |
|---|---|---|---|---|
| `extension_policy` | `fk_managed_extensions_id` | `managed_extensions` | `pk_managed_extension_id` | This policy points to its Browser Extension Management settings |

### Plain Mapping Summary

- `name` -> `extension_policy.name`
- `managedExtension.extensionPolicyType` -> `managed_extensions.action`
- `managedExtension.enforcementAction` -> `managed_extensions.enforcement_action`
- `managedExtension.warningMessage` -> `managed_extensions.warning_message`
- `extension_policy.fk_managed_extensions_id` -> `managed_extensions.pk_managed_extension_id`

## PUT Test Case 1: Update to `BLOCK_ALL`

Purpose: verify `managedExtension.extensionPolicyType`, `managedExtension.enforcementAction`, and `managedExtension.warningMessage` persist correctly.

```http
PUT {{base_url}}/api/tenants/extension-policy/844dd67d-e3cf-43a8-ac65-3ea440422d9b
Authorization: Bearer {{auth_token}}
Content-Type: application/json
```

```json
{
  "name": "Default Extension Policy",
  "description": "Updated to block all browser extensions",
  "landingPageUrl": "https://example.com/extension-policy/block-all",
  "managedExtension": {
    "extensionPolicyType": "BLOCK_ALL",
    "enforcementAction": "BLOCK_BROWSER",
    "warningMessage": "All browser extensions are blocked by policy.",
    "extensions": []
  }
}
```

Expected:

- Response status: `200 OK`
- `managedExtension.extensionPolicyType` = `BLOCK_ALL`
- `managedExtension.enforcementAction` = `BLOCK_BROWSER`
- `managedExtension.warningMessage` = `All browser extensions are blocked by policy.`

## PUT Test Case 2: Update to `ALLOW_LIST`

Purpose: verify allow-list mode persists along with curated extensions and warning behavior.

```http
PUT {{base_url}}/api/tenants/extension-policy/844dd67d-e3cf-43a8-ac65-3ea440422d9b
Authorization: Bearer {{auth_token}}
Content-Type: application/json
```

```json
{
  "name": "Default Extension Policy",
  "description": "Allow only approved extensions",
  "landingPageUrl": "https://example.com/extension-policy/allow-list",
  "managedExtension": {
    "extensionPolicyType": "ALLOW_LIST",
    "enforcementAction": "WARN_USER",
    "warningMessage": "Only approved extensions are allowed for this tenant.",
    "extensions": [
      {
        "extensionId": "ghbmnnjooekpmoecnnnilnnbdlolhkhi",
        "extensionName": "Google Docs Offline",
        "publisher": "Google"
      },
      {
        "extensionId": "aapbdbdomjkkjkaonfhkkikfgjllcleb",
        "extensionName": "Google Translate",
        "publisher": "Google"
      }
    ]
  }
}
```

Expected:

- Response status: `200 OK`
- `managedExtension.extensionPolicyType` = `ALLOW_LIST`
- `managedExtension.enforcementAction` = `WARN_USER`
- `managedExtension.warningMessage` = `Only approved extensions are allowed for this tenant.`
- `managedExtension.extensions` contains 2 records

## PUT Test Case 3: Update to `BLOCK_LIST`

Purpose: verify block-list mode persists with blocked extensions and custom warning text.

```http
PUT {{base_url}}/api/tenants/extension-policy/844dd67d-e3cf-43a8-ac65-3ea440422d9b
Authorization: Bearer {{auth_token}}
Content-Type: application/json
```

```json
{
  "name": "Default Extension Policy",
  "description": "Block only prohibited extensions",
  "landingPageUrl": "https://example.com/extension-policy/block-list",
  "managedExtension": {
    "extensionPolicyType": "BLOCK_LIST",
    "enforcementAction": "BLOCK_BROWSER",
    "warningMessage": "The listed extensions are prohibited and have been blocked.",
    "extensions": [
      {
        "extensionId": "cjpalhdlnbpafiamejdnhcphjbkeiagm",
        "extensionName": "uBlock Origin",
        "publisher": "Raymond Hill"
      },
      {
        "extensionId": "gighmmpiobklfepjocnamgkkbiglidom",
        "extensionName": "AdBlock",
        "publisher": "getadblock.com"
      }
    ]
  }
}
```

Expected:

- Response status: `200 OK`
- `managedExtension.extensionPolicyType` = `BLOCK_LIST`
- `managedExtension.enforcementAction` = `BLOCK_BROWSER`
- `managedExtension.warningMessage` = `The listed extensions are prohibited and have been blocked.`
- `managedExtension.extensions` contains the blocked extension list

## Verification GET

After each PUT, confirm persistence with:

```http
GET {{base_url}}/api/tenants/extension-policy/844dd67d-e3cf-43a8-ac65-3ea440422d9b
Authorization: Bearer {{auth_token}}
```

Verify the returned `managedExtension` block matches the most recent PUT payload.
