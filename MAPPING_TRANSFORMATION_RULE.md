# Response Mapping Transformation Rule

## Overview
This document outlines a specific transformation rule applied at the API response/mapper layer for the `EventsGroupDeviceUserMapping` entity.

## Transformation Rule
When converting the `EventsGroupDeviceUserMapping` entity to its respective Data Transfer Objects (DTOs), the following logic is applied to the `assignedBy` field:

- **Condition**: If `assignedBy` equals `"SYSTEM_API_KEY"`
- **Action**: Replace value with `"SYSTEM_TENANT_KEY"`
- **Scope**: Outgoing API responses only.

## Intent
The goal is to present `"SYSTEM_TENANT_KEY"` to API consumers while maintaining the original `"SYSTEM_API_KEY"` value in the database. This is a display-layer change only.

## Affected Files
- **Service Layer**: `sfn-iam-api 4/sfn-iam-api/src/main/java/com/secufusion/iam/service/DeviceUserGroupMappingService.java` (in `getDeviceUserWithGroups` method)
- **Controller Layer**: `sfn-iam-api 4/sfn-iam-api/src/main/java/com/secufusion/iam/controller/EventsGroupController.java` (in `convertMappingToDto` method)

## Database Integrity Constraints
To ensure absolute data integrity, the following constraints are enforced:
1. **NO modification to Entity**: The `EventsGroupDeviceUserMapping` entity's `assignedBy` field is never modified.
2. **NO Persistence of Transformed Value**: `"SYSTEM_TENANT_KEY"` is never saved to the database.
3. **Database Value Retention**: The database always retains `"SYSTEM_API_KEY"` as the source of truth for system-generated mappings.

---

## Postman Step-by-Step Testing Guide

Follow these steps to verify the transformation and avoid common pitfalls.

### Step 1: Obtain Authentication Token
1. Log in with your **Enterprise** or **MSSP** credentials.
2. Save the `accessToken` as `{{auth_token}}` in your Postman environment.

### Step 2: Identify Valid IDs (Crucial Step)
You cannot use a Portal Admin ID (from the `/users` endpoint) for group mapping. You **must** use a `deviceUserId`.

1. **Find an existing mapping**:
   * **GET** `http://localhost:8084/api/iam/events-groups`
   * Find a group where `deviceUsersCount > 0` (usually the "Default API Key Group").
   * Copy that `pkEventsGroupId`.
2. **Retrieve the Device User ID**:
   * **GET** `http://localhost:8084/api/iam/events-groups/{{groupId}}/device-users`
   * Copy the `deviceUserId` from the response (e.g., `97507649-e21f-43a5-80d8-a7e9fbadcb4a`).

### Step 3: Verify the Transformation (GET)
Check the response from Step 2.2. If the user was auto-assigned by the system:
* **Expected Result**: `"assignedBy": "SYSTEM_TENANT_KEY"`
* **Actual DB Value**: `SYSTEM_API_KEY` (Verified via display layer masking).

### Step 4: Test Manual Assignment (POST)
1. **URL**: `http://localhost:8084/api/iam/events-groups/{{groupId}}/device-users`
2. **Body**:
   ```json
   {
     "deviceUserId": "97507649-e21f-43a5-80d8-a7e9fbadcb4a"
   }
   ```
3. **Verification**: In this case, `assignedBy` will show your **email address** because you performed the action manually.

---

## Troubleshooting & Resolutions

### 1. Error: "RESOURCE CONFLICT"
**Message**: `Cannot delete or update resource due to existing references`
- **Problem**: You are likely trying to use a `pkUserId` from the standard Users table.
- **Resolution**: Group mappings require a `deviceUserId`. Use the ID found in the `/device-users` list or triggered via an event.

### 2. Error: "RESOURCE NOT FOUND"
- **Problem**: The `groupId` in the URL does not belong to the tenant in your JWT.
- **Resolution**: Use `GET /api/iam/events-groups` to ensure you are using a valid group for your current session.

---

## Reference Test Cases (JSON)

### GET Verification (System Masking)
**Endpoint**: `GET /events-groups/{{defaultGroupId}}/device-users`
```json
[
    {
        "mappingId": "ac2a0730-3dad-46fd-965b-ad8cb9152155",
        "deviceUserId": "97507649-e21f-43a5-80d8-a7e9fbadcb4a",
        "deviceUserEmail": "kelsey58d312@to.cloudvxz.com",
        "groupName": "Default API Key Group",
        "assignedBy": "SYSTEM_TENANT_KEY"
    }
]
```

### POST Assignment (Manual Action)
**Endpoint**: `POST /events-groups/{{groupId}}/device-users`
```json
{
  "message": "Successfully assigned 1 user(s) to group",
  "mappings": [
    {
      "deviceUserId": "97507649-e21f-43a5-80d8-a7e9fbadcb4a",
      "assignedBy": "admin-email@example.com"
    }
  ]
}
```
### Assignment Source Logic (`assignedBy` Values)
| Value | Context | Origin |
|-------|---------|--------|
| `SYSTEM_TENANT_KEY` | **Transformed** | Originally `SYSTEM_API_KEY` in DB. Assigned via API Key flow. |
| `SYSTEM_AUTO` | **System** | Automated assignment via Azure Sync, SSO Login, or Default Group logic. |
| `admin@example.com` | **Manual** | A specific administrator performed a manual POST mapping. |

---

## Azure Integration & Authorization

### 1. The `null` pkEventsGroupId Behavior
When calling `GET /events-groups` for an Azure tenant, you may see `pkEventsGroupId: null`.
- **Why**: The group exists in **Azure AD (Microsoft Graph)** but has not been **Authorized** in Secufusion.
- **Status**: It is a "Virtual Group" until authorized. It cannot hold policy assignments or manual user mappings in this state.
- **Resolution**: Use the `/authorize` endpoint to persist the group to the local database and generate a permanent UUID.

### 2. Authorization Flow
1. **Fetch**: System queries MS Graph for all tenant groups.
2. **Display**: Shows `authorized: false` and `pkEventsGroupId: null`.
3. **Action**: Admin authorizes the group.
4. **Persist**: Group is saved to the `events_groups` table; `pkEventsGroupId` is generated.

---
