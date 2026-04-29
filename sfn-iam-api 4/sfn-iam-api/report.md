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
- Kept the `/users/check` endpoint and `checkMobileNumber` logic unchanged, because they only report availability and do not block creation.
- Kept phone lookup methods in the repository unchanged so existing read-side behavior still works.

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

### 5. Check Phone Availability
Endpoint: `GET {{iam_url}}/users/check?phoneNumber=1234567890`

Expected result: `200 OK` with `true` if the phone exists or `false` if it is available.

## Validation Scenarios
The following scenarios should now succeed without validation errors:
- Portal admin created with no phone field in the request.
- Portal admin created with `phoneNumber: null`.
- Portal admin created with `phoneNumber: ""`.
- Portal admin created with a phone number already used by another user.
- Portal admin created with a valid unique phone number.
