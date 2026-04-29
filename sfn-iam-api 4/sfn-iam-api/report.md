# Phone Number Validation Fix Report

## Overview
The IAM service was updated so portal admin creation no longer fails when phone number is missing, empty, null, or duplicated. Phone number validation is now informational only during creation.

## What Was There
Before the fix, the service enforced a uniqueness check on phone number during admin creation. If a matching record already existed, the flow threw `PHONE_EXISTS` and rejected the request. This made phone number behave like a mandatory unique field even though the business requirement is for it to remain optional.

## What Was Resolved
### Service Layer Validation
File: [src/main/java/com/secufusion/iam/service/UserService.java](src/main/java/com/secufusion/iam/service/UserService.java)
- Removed the database-backed uniqueness check from `validateUserFields` that searched for an existing phone number and threw `PHONE_EXISTS`.
- Replaced it with a debug log so the service still records when a phone number is supplied, without blocking creation.

### DTO and Entity Review
- Verified `UsersDto.java` and `User.java` to ensure the phone field does not have mandatory validation annotations such as `@NotNull`, `@NotBlank`, or `@NotEmpty`.
- Verified that no `unique = true` constraint is applied to the phone field at the DTO or entity level.

### Repository and Utility Behavior
- `/users/check` was enhanced for `phoneNumber` lookups to return a minimal list of matching users instead of a boolean flag.
- Added a dedicated projection DTO and JPQL projection query so the endpoint does not load or return groups/roles/scopes.

### Behavior Change: Email Uniqueness on Create
- Creation now fails when a user with the same email already exists in the tenant. The `createUserInternal` flow was updated to throw `EMAIL_EXISTS` instead of reusing an existing record. See: [src/main/java/com/secufusion/iam/service/UserService.java](src/main/java/com/secufusion/iam/service/UserService.java).

## Postman Test Cases

### 1. Create Admin with No Phone Field
Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`

Body:
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe.nophone@example.com",
  "groups": []
}
```
Expected result: `201 Created`

### 2. Create Admin with Null Phone
Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`

Body:
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane.doe.nullphone@example.com",
  "phoneNumber": null,
  "groups": []
}
```
Expected result: `201 Created`

### 3. Create Admin with Empty Phone String
Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`

Body:
```json
{
  "firstName": "Bob",
  "lastName": "Smith",
  "email": "bob.smith.emptyphone@example.com",
  "phoneNumber": "",
  "groups": []
}
```
Expected result: `201 Created`

### 4. Create Admin with Duplicate Phone Number
Step 1: Create User A with phone `1234567890`.
Step 2: Create User B with the same phone.

Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`

Body:
```json
{
  "firstName": "Duplicate",
  "lastName": "User",
  "email": "duplicate.phone@example.com",
  "phoneNumber": "1234567890",
  "groups": []
}
```
Expected result: `201 Created`

Previously this would return `409 Conflict` with `PHONE_EXISTS`.

### 5. Check Users by Phone Number
Endpoint: `GET {{iam_url}}/users/check?phoneNumber=1234567890`

Expected result: `200 OK` with a minimal response:

```json
{
  "count": 2,
  "message": "2 user(s) found with phone number 1234567890",
  "users": [
    {
      "pkUserId": "...",
      "userName": "...",
      "email": "...",
      "phoneNumber": "1234567890",
      "firstName": "...",
      "lastName": "...",
      "status": "ACTIVE",
      "createdAt": "2026-04-29T20:58:40.1042"
    }
  ]
}
```

## Validation Scenarios
The following scenarios should now succeed without validation errors:
- Portal admin created with no phone field in the request.
- Portal admin created with `phoneNumber: null`.
- Portal admin created with `phoneNumber: ""`.
- Portal admin created with a phone number already used by another user.
- Portal admin created with a valid unique phone number.

## Added Functionality

- `GET /users/check?phoneNumber={number}` now returns only users with exact matching phone number and only minimal fields.
- No `groups`, `roles`, or `scopes` are returned for this phone lookup response.

### Files Touched and What Changed

### Files Created

1. `report.md`
- Created to document issue analysis, fixes, API behavior changes, and Postman examples.

2. `src/main/java/com/secufusion/iam/dto/UserPhoneCheckDto.java`
- Created as a minimal projection DTO for `/users/check?phoneNumber=...`.

3. `src/test/java/com/secufusion/iam/controller/UserControllerPhoneCheckTest.java`
- Created as a focused controller unit test file for phone lookup behavior (kept without execution in this session).

1. `src/main/java/com/secufusion/iam/dto/UserPhoneCheckDto.java`
- New file (did not exist earlier).
- Added minimal projection DTO with fields:
  `pkUserId`, `userName`, `email`, `phoneNumber`, `firstName`, `lastName`, `status`, `createdAt`.

2. `src/main/java/com/secufusion/iam/repository/UserRepository.java`
- Existed earlier.
- Added JPQL constructor projection methods for exact phone lookup:
  - `findPhoneCheckByPhoneNo(...)`
  - `findPhoneCheckByPhoneNoAndTenantId(...)`
- Query intentionally avoids fetch joins and returns only minimal DTO fields.

3. `src/main/java/com/secufusion/iam/service/UserService.java`
- Existed earlier.
- Added/updated phone lookup service to return `List<UserPhoneCheckDto>` using repository projection methods.
- Added tenant-scoped variant for request-tenant filtering.

4. `src/main/java/com/secufusion/iam/controller/UserController.java`
- Existed earlier.
- Updated `GET /users/check` phone branch to return clean top-level structure:
  - `count`
  - `users`
  - `message`

### What Existed Earlier vs New

- Earlier behavior:
  - Phone check returned boolean-style response (availability/existence semantics).
  - Response went through generic `ResponseDto` wrapper.
  - Full user object expansion could leak unnecessary nested data depending on mapping path.

- New behavior:
  - Phone check returns minimal user list for exact phone matches.
  - Uses projection DTO to prevent nested expansion.
  - Returns clean payload focused on phone-match use case.

### Wrapper Field Problem and Fix

- Problem observed:
  - Generic `ResponseDto` field names are `results`, `errorMessage`, `errorCode`.
  - Even for successful `200` responses, wrapper fields looked misleading (for example `errorMessage: "200"`, `errorCode: "...success message..."`).

- Fix applied for phone-number branch:
  - For `phoneNumber` requests in `/users/check`, controller now returns a direct `Map` response (`ResponseEntity.ok(resp)`) instead of wrapping in `ResponseDto`.
  - This removes confusing `errorMessage`/`errorCode` fields for this specific success response.

- Note:
  - Username/email branches under `/users/check` still use the existing `ResponseDto` pattern to avoid broad API contract changes outside this scope.

- `POST /users/{tenantId}` behavior examples (requests used during testing):

  1) Create user without phone

  Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`
  Body:
  ```json
  {
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe.nophone@example.com",
    "groups": []
  }
  ```

  2) Create user with null phone

  Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`
  Body:
  ```json
  {
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane.doe.nullphone@example.com",
    "phoneNumber": null,
    "groups": []
  }
  ```

  3) Create user with empty phone

  Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`
  Body:
  ```json
  {
    "firstName": "Bob",
    "lastName": "Smith",
    "email": "bob.smith.emptyphone@example.com",
    "phoneNumber": "",
    "groups": []
  }
  ```

  4) Create two users with same phone (duplicate allowed)

  Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`
  Body (example):
  ```json
  {
    "firstName": "Duplicate",
    "lastName": "User",
    "email": "duplicate.phone@example.com",
    "phoneNumber": "1234567890",
    "groups": []
  }
  ```

  5) Create with an email that already exists (now blocked)

  Endpoint: `POST {{iam_url}}/users/{{tenant_id}}`
  Body (example):
  ```json
  {
    "firstName": "Existing",
    "lastName": "User",
    "email": "john.doe.nophone@example.com",
    "groups": []
  }
  ```
  Expected result: `409`/`EMAIL_EXISTS` (service throws `EMAIL_EXISTS` and creation is blocked).

## Example Responses

- Successful create (201/accepted body format):

```json
{
  "data": { "pkUserId": "<uuid>", "email": "john.doe.nophone@example.com", "fkTenantId": "<tenantId>" },
  "status": "201",
  "message": "User created successfully"
}
```

- Duplicate email error (example):

```json
{
  "error": "EMAIL_EXISTS",
  "code": 3101,
  "message": "Email already exists"
}
```
