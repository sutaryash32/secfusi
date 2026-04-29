# Walkthrough - Phone Number Validation Fix

I have modified the IAM service to make phone numbers fully optional and non-unique during admin creation. This ensures that admins can be created without a phone number or with a phone number that is already in use by another user.

## Changes Made

### 1. Service Layer Validation
**File**: `src/main/java/com/secufusion/iam/service/UserService.java`
- Removed the block in `validateUserFields` that enforced phone number uniqueness by querying the database and throwing `PHONE_EXISTS`.
- Added a debug log to indicate that phone uniqueness validation is skipped.

### 2. DTO and Entity Verification
- Verified `UsersDto.java` and `User.java` to ensure no mandatory validation annotations (`@NotNull`, `@NotBlank`) or unique constraints (`unique = true`) exist for phone fields. They were already clean.

---

## Postman Test Cases

You can use the following requests to verify the fix.

### 1. Create Admin with NO Phone Field
**Endpoint**: `POST {{iam_url}}/users/{{tenant_id}}`
**Body**:
```json
{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe.nophone@example.com",
    "groups": []
}
```
**Expected**: `201 Created`

### 2. Create Admin with NULL Phone
**Endpoint**: `POST {{iam_url}}/users/{{tenant_id}}`
**Body**:
```json
{
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane.doe.nullphone@example.com",
    "phoneNumber": null,
    "groups": []
}
```
**Expected**: `201 Created`

### 3. Create Admin with EMPTY Phone String
**Endpoint**: `POST {{iam_url}}/users/{{tenant_id}}`
**Body**:
```json
{
    "firstName": "Bob",
    "lastName": "Smith",
    "email": "bob.smith.emptyphone@example.com",
    "phoneNumber": "",
    "groups": []
}
```
**Expected**: `201 Created`

### 4. Create Admin with DUPLICATE Phone Number
**Step 1**: Create User A with phone `1234567890`.
**Step 2**: Create User B with the same phone.
**Endpoint**: `POST {{iam_url}}/users/{{tenant_id}}`
**Body**:
```json
{
    "firstName": "Duplicate",
    "lastName": "User",
    "email": "duplicate.phone@example.com",
    "phoneNumber": "1234567890",
    "groups": []
}
```
**Expected**: `201 Created` (Previously this would return `409 Conflict` with `PHONE_EXISTS`)

### 5. Check Phone Availability (Utility Endpoint)
**Endpoint**: `GET {{iam_url}}/users/check?phoneNumber=1234567890`
**Expected**: `200 OK` with `true` (if exists) or `false` (if available). This endpoint still works as intended to provide info, but does not block creation.
