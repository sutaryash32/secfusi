# Device Registration API

**Endpoint:** `POST /api/events/devices/register`
**Auth:** Bearer JWT token (Authorization header)

---

## Request Body

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `deviceName` | String | No | Human-readable device name (e.g., "Venkat's Laptop") |
| `userAgent` | String | **Yes** | Browser user agent string |
| `extensionVersion` | String | No | Browser extension version |
| `ipAddress` | String | No | Device IP address |
| `osInfo` | String | No | Operating system info (e.g., "Windows 11 Pro") |
| `deviceFingerprint` | String | No | Unique fingerprint from extension (used for device deduplication) |
| `userEmail` | String | Conditional | Real user email. **Required for APIKEY and MSI/Intune tenants** (JWT has no email claim). For Azure AD tenants, email is extracted from JWT automatically. |
| `userDisplayName` | String | No | User display name. Falls back to `userEmail` if not provided. |

---

## How User Identity is Resolved

The backend resolves the user's email and display name using this priority chain:

```
1. JWT 'email' claim        --> if present, use it (Azure AD flow)
2. request.userEmail         --> if present, use it (APIKEY / MSI+browser profile)
3. device-{fingerprint}@{tenant} --> auto-generated (MSI fallback, no user identity)
4. device-{deviceId}@{tenant}    --> absolute fallback (no fingerprint either)
```

**Extension developers:** To ensure correct user identity in MSI/Intune deployments, use the browser identity API:

```javascript
// Chrome/Edge extension - detect signed-in browser user
chrome.identity.getProfileUserInfo({ accountStatus: 'ANY' }, (userInfo) => {
  // userInfo.email = "venkat@contoso.com" (if signed into browser)
  registerDevice({
    userEmail: userInfo.email || undefined,
    userDisplayName: userInfo.email || undefined,
    // ... other fields
  });
});
```

**Manifest permission required:**
```json
{
  "permissions": ["identity.email"]
}
```

---

## Scenario 1: Azure AD Tenant

**When:** User logs in via Azure AD SSO. JWT contains `email`, `name`, `azure_tenant_id`, and `groups` claims.

### Request
```json
{
  "deviceName": "Venkat's Work Laptop",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0",
  "extensionVersion": "2.1.0",
  "ipAddress": "10.0.5.42",
  "osInfo": "Windows 11 Pro",
  "deviceFingerprint": "fp_az_8a3b1c9d2e4f"
}
```

> `userEmail` / `userDisplayName` **not needed** -- extracted from JWT.

### Response
```json
{
  "data": {
    "deviceId": "dev-a1b2c3d4-5678",
    "deviceName": "Chrome - Windows 11 Pro - Desktop - Venkateswara Reddy",
    "tenantId": "tenant-contoso-001",
    "userName": "venkat@contoso.com",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0",
    "deviceType": "Desktop",
    "browserType": "Chrome",
    "extensionVersion": "2.1.0",
    "ipAddress": "10.0.5.42",
    "location": null,
    "status": "ACTIVE",
    "firstSeenAt": "2026-04-01T10:30:00",
    "lastSeenAt": "2026-04-01T10:30:00",
    "osInfo": "Windows 11 Pro",
    "deviceFingerprint": "fp_az_8a3b1c9d2e4f",
    "deviceToken": null,
    "isAnonymous": null,
    "linkedAt": null,
    "linkedUserId": null,
    "deviceUserId": "du-9f8e7d6c-5b4a",
    "deviceUserEmail": "venkat@contoso.com",
    "deviceUserStatus": "ACTIVE",
    "linkedToPortalUser": true
  },
  "message": "200"
}
```

**What happens:**
- Email resolved from JWT `email` claim
- Display name from JWT `name` claim
- Azure groups matched and mapped automatically
- Policies resolved per group assignments

---

## Scenario 2: API Key Tenant

**When:** Tenant uses API key authentication. JWT is a Keycloak client_credentials grant -- `preferred_username` is a service account name (e.g., `service-account-acme-extension-client`), NOT the real user.

### Request
```json
{
  "deviceName": "Reception Kiosk",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/124.0.0.0",
  "extensionVersion": "2.1.0",
  "ipAddress": "192.168.1.100",
  "osInfo": "Windows 10 Enterprise",
  "deviceFingerprint": "fp_api_7x2k9m4n",
  "userEmail": "receptionist@acmecorp.com",
  "userDisplayName": "Front Desk - Acme Corp"
}
```

> `userEmail` **required** -- JWT has no email claim.
> `userDisplayName` recommended for better dashboard readability.

### Response
```json
{
  "data": {
    "deviceId": "dev-e5f6g7h8-1234",
    "deviceName": "Edge - Windows 10 Enterprise - Desktop - Front Desk",
    "tenantId": "tenant-acme-002",
    "userName": "service-account-acme-extension-client",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/124.0.0.0",
    "deviceType": "Desktop",
    "browserType": "Edge",
    "extensionVersion": "2.1.0",
    "ipAddress": "192.168.1.100",
    "location": null,
    "status": "ACTIVE",
    "firstSeenAt": "2026-04-01T11:00:00",
    "lastSeenAt": "2026-04-01T11:00:00",
    "osInfo": "Windows 10 Enterprise",
    "deviceFingerprint": "fp_api_7x2k9m4n",
    "deviceToken": null,
    "isAnonymous": null,
    "linkedAt": null,
    "linkedUserId": null,
    "deviceUserId": "du-3a2b1c0d-9e8f",
    "deviceUserEmail": "receptionist@acmecorp.com",
    "deviceUserStatus": "ACTIVE",
    "linkedToPortalUser": false
  },
  "message": "200"
}
```

**What happens:**
- JWT `email` = null, falls back to `request.userEmail`
- No Azure groups -- device user created without group mappings

---

## Scenario 3: MSI/Intune -- User Signed Into Browser

**When:** Extension is pushed silently via Intune/MSI. JWT is a machine service token. The extension detects the browser profile user via `chrome.identity.getProfileUserInfo()`.

### Request
```json
{
  "deviceName": "DESKTOP-MSI-4A7B",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/124.0.0.0",
  "extensionVersion": "2.1.0",
  "ipAddress": "10.10.20.55",
  "osInfo": "Windows 11 Enterprise",
  "deviceFingerprint": "fp_msi_m3n4o5p6q7",
  "userEmail": "venkat@megacorp.com",
  "userDisplayName": "Venkateswara Reddy"
}
```

> Extension populated `userEmail` + `userDisplayName` from browser profile identity.

### Response
```json
{
  "data": {
    "deviceId": "dev-i9j0k1l2-5678",
    "deviceName": "Edge - Windows 11 Enterprise - Desktop - Venkateswara Reddy",
    "tenantId": "tenant-megacorp-003",
    "userName": "service-account-intune-client",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/124.0.0.0",
    "deviceType": "Desktop",
    "browserType": "Edge",
    "extensionVersion": "2.1.0",
    "ipAddress": "10.10.20.55",
    "location": null,
    "status": "ACTIVE",
    "firstSeenAt": "2026-04-01T11:30:00",
    "lastSeenAt": "2026-04-01T11:30:00",
    "osInfo": "Windows 11 Enterprise",
    "deviceFingerprint": "fp_msi_m3n4o5p6q7",
    "deviceToken": null,
    "isAnonymous": null,
    "linkedAt": null,
    "linkedUserId": null,
    "deviceUserId": "du-7f6e5d4c-3b2a",
    "deviceUserEmail": "venkat@megacorp.com",
    "deviceUserStatus": "ACTIVE",
    "linkedToPortalUser": false
  },
  "message": "200"
}
```

**What happens:**
- JWT `email` = null, falls back to `request.userEmail` = `"venkat@megacorp.com"` (from browser profile)
- Each user gets their own DeviceUser record

---

## Scenario 4: MSI/Intune -- User NOT Signed Into Browser (Fallback)

**When:** Extension is pushed via Intune, but user is not signed into the browser. Extension cannot detect user identity.

### Request
```json
{
  "deviceName": "DESKTOP-MSI-9X2Z",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/124.0.0.0",
  "extensionVersion": "2.1.0",
  "ipAddress": "10.10.20.77",
  "osInfo": "Windows 11 Enterprise",
  "deviceFingerprint": "fp_msi_r8s9t0u1v2"
}
```

> No `userEmail` -- extension couldn't detect browser profile.

### Response
```json
{
  "data": {
    "deviceId": "dev-x3y4z5a6-9012",
    "deviceName": "Edge - Windows 11 Enterprise - Desktop",
    "tenantId": "tenant-megacorp-003",
    "userName": "service-account-intune-client",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/124.0.0.0",
    "deviceType": "Desktop",
    "browserType": "Edge",
    "extensionVersion": "2.1.0",
    "ipAddress": "10.10.20.77",
    "location": null,
    "status": "ACTIVE",
    "firstSeenAt": "2026-04-01T12:00:00",
    "lastSeenAt": "2026-04-01T12:00:00",
    "osInfo": "Windows 11 Enterprise",
    "deviceFingerprint": "fp_msi_r8s9t0u1v2",
    "deviceToken": null,
    "isAnonymous": null,
    "linkedAt": null,
    "linkedUserId": null,
    "deviceUserId": "du-c4d5e6f7-1a2b",
    "deviceUserEmail": "device-fp_msi_r8s9t0u1v2@tenant-megacorp-003",
    "deviceUserStatus": "ACTIVE",
    "linkedToPortalUser": false
  },
  "message": "200"
}
```

**What happens:**
- JWT `email` = null, `request.userEmail` = null
- Falls back to device fingerprint-based identity: `device-{fingerprint}@{tenantId}`
- Each device gets its own unique DeviceUser (no shared service account)
- When user later signs into browser, next registration call will update with real email

---

## Response Fields Reference

| Field | Type | Description |
|-------|------|-------------|
| `deviceId` | String | Unique device identifier |
| `deviceName` | String | Sanitized device name (format: `Browser - OS - Type - User`) |
| `tenantId` | String | Tenant identifier |
| `userName` | String | JWT `preferred_username` (may be service account for APIKEY/MSI) |
| `deviceType` | String | Parsed from user agent: `Desktop`, `Mobile`, `Tablet`, `Unknown` |
| `browserType` | String | Parsed from user agent: `Chrome`, `Edge`, `Firefox`, `Safari`, etc. |
| `status` | String | `ACTIVE`, `INACTIVE`, or `BLOCKED` |
| `firstSeenAt` | DateTime | First registration timestamp |
| `lastSeenAt` | DateTime | Last activity timestamp |
| `deviceFingerprint` | String | Unique fingerprint (used for device deduplication across sessions) |
| `deviceUserId` | String | Associated DeviceUser ID (auto-created on registration) |
| `deviceUserEmail` | String | Resolved user email (see resolution chain above) |
| `deviceUserStatus` | String | DeviceUser status (`ACTIVE`) |
| `linkedToPortalUser` | Boolean | Whether DeviceUser is linked to a portal user account |

---

## Summary Table

| Tenant Type | JWT has email? | Extension sends userEmail? | Email Resolved As |
|-------------|---------------|---------------------------|-------------------|
| Azure AD | Yes | Not needed | JWT `email` claim |
| API Key | No | Yes (required) | `request.userEmail` |
| MSI/Intune (browser signed in) | No | Yes (from `chrome.identity`) | `request.userEmail` |
| MSI/Intune (browser NOT signed in) | No | No | `device-{fingerprint}@{tenantId}` |

---

## Important Notes for Extension Developers

1. **Always send `userEmail` when available** -- even for Azure AD tenants, it serves as a fallback.
2. **Use `chrome.identity.getProfileUserInfo()`** to detect browser profile email for MSI/Intune deployments.
3. **Add `"identity.email"` permission** to the extension manifest.
4. **Always send `deviceFingerprint`** -- it's used for device deduplication and as a fallback identity for MSI/Intune.
5. **`userName` in response** is always the JWT `preferred_username`, which may be a service account name for APIKEY/MSI tenants. Use `deviceUserEmail` for the real user identity.
