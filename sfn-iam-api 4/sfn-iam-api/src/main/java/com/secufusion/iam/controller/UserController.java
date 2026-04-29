package com.secufusion.iam.controller;

import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.dto.UserPhoneCheckDto;
import com.secufusion.iam.dto.UsersDto;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.service.UserService;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Operations for user management")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtl jwtUtl;

    // ============================================================
    // CREATE USER UNDER TENANT
    // ============================================================

    /**
     * Create a new user under the given tenant id.
     */
    @Operation(summary = "Create user under tenant", description = "Create a new user under the specified tenant ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @PostMapping("/{tenantId}")
    public ResponseEntity<ResponseDto<UsersDto>> createUser(
            @Parameter(description = "Tenant identifier", required = true) @PathVariable String tenantId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Create user under tenant {}", tenantId);
        log.debug("Request payload for createUser under tenant {}: {}", tenantId, usersDto);
        UsersDto created = userService.createUser(tenantId, usersDto);
        log.info("User created with id={} under tenant={}", created != null ? created.getPkUserId() : null, tenantId);
        return ResponseEntity.ok(new ResponseDto<>(created,String.valueOf(HttpStatus.CREATED.value()),"User created successfully"));
    }

    /**
     * Create a user using tenant info extracted from the JWT in the request.
     */
    @Operation(summary = "Create user by tenant from JWT", description = "Create a new user using tenant determined from the request JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Unable to determine tenant from request")
    })
    @PostMapping
    public ResponseEntity<ResponseDto<UsersDto>> createUserByTenant(
            HttpServletRequest request,
            @RequestBody UsersDto usersDto) {

        log.info("API: Create user by tenant from request");
        Tenant tenantFromEmail = jwtUtl.getTenantFromRequest(request);

        if (tenantFromEmail == null) {
            log.error("Unable to determine tenant from request while creating user");
            return ResponseEntity.badRequest().build();
        }

        log.debug("Tenant resolved for createUserByTenant: {}", tenantFromEmail.getTenantID());
        UsersDto created = userService.createUser(request, usersDto);
        log.info("User created with id={} under tenant={}", created != null ? created.getPkUserId() : null,
                tenantFromEmail.getTenantID());
        return ResponseEntity.ok(new ResponseDto<>(created,String.valueOf(HttpStatus.CREATED.value()),"User created successfully"));
    }


    // ============================================================
    // GET USER BY ID
    // ============================================================

    /**
     * Fetch a user by the global user id.
     */
    @Operation(summary = "Get user by ID", description = "Fetch a user by the global user identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User fetched"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<ResponseDto<UsersDto>> getUser(@Parameter(description = "Global user identifier", required = true) @PathVariable String userId) {

        log.info("API: Fetch user {}", userId);
        UsersDto user = userService.getUser(userId);
        log.debug("Fetched user for id={} : {}", userId, user);
        return ResponseEntity.ok(new ResponseDto<>(user,String.valueOf(HttpStatus.OK.value()),"User fetched successfully"));
    }

    /**
     * Fetch a user by id while validating tenant from the request JWT.
     */
    @Operation(summary = "Get user by ID under tenant", description = "Fetch a user by ID and validate tenant from request JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User fetched"),
            @ApiResponse(responseCode = "400", description = "Unable to determine tenant from request")
    })
    @GetMapping("/userId")
    public ResponseEntity<ResponseDto<UsersDto>> getUserByParents(HttpServletRequest request, @Parameter(description = "User identifier", required = true) @RequestParam String userId) {

        log.info("API: Fetch user {} under tenant from request", userId);
        Tenant tenantFromEmail = jwtUtl.getTenantFromRequest(request);

        if (tenantFromEmail == null) {
            log.error("Unable to determine tenant from request for getUserByParents userId={}", userId);
            return ResponseEntity.badRequest().build();
        }

        log.debug("Tenant resolved for getUserByParents: {}", tenantFromEmail.getTenantID());
        UsersDto user = userService.getUserByParents(request, userId);
        log.info("User fetched for id={} under tenant={}", userId, tenantFromEmail.getTenantID());
        return ResponseEntity.ok(new ResponseDto<>(user,String.valueOf(HttpStatus.OK.value()),"User fetched successfully"));
    }


    // ============================================================
    // GET ALL USERS
    // ============================================================

    /**
     * Return all users (global).
     */
    @Operation(summary = "Get all users", description = "Return all users in the system")
    @ApiResponse(responseCode = "200", description = "List of users returned")
    @GetMapping
    public ResponseEntity<ResponseDto<List<UsersDto>>> getAllUsers(HttpServletRequest request) {

        log.info("API: Fetch all users");
        List<UsersDto> users = userService.getAllUsers(request);
        log.debug("Number of users fetched: {}", users != null ? users.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(users,String.valueOf(HttpStatus.OK.value()),"Users fetched successfully"));
    }


    // ============================================================
    // GET USERS BY TENANT ID
    // ============================================================

    /**
     * Fetch users belonging to a given tenant.
     */
    @Operation(summary = "Get users by tenant", description = "Fetch users belonging to the specified tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users fetched"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ResponseDto<List<UsersDto>>> getUsersByTenant(@Parameter(description = "Tenant identifier", required = true) @PathVariable String tenantId) {

        log.info("API: Fetch users for tenant {}", tenantId);
        List<UsersDto> users = userService.getUsersByTenantId(tenantId);
        log.debug("Users fetched for tenant {}: {}", tenantId, users != null ? users.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(users,String.valueOf(HttpStatus.OK.value()),"Users fetched successfully"));
    }


    // ============================================================
    // UPDATE USER
    // ============================================================

    /**
     * Update a user by global user id.
     */
    @Operation(summary = "Update user by ID", description = "Update a user identified by the global user ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @PutMapping("/{userId}")
    public ResponseEntity<ResponseDto<UsersDto>> updateUser(
            @Parameter(description = "Global user identifier", required = true) @PathVariable String userId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Update user {}", userId);
        log.debug("Update payload for user {}: {}", userId, usersDto);
        UsersDto updated = userService.updateUser(userId, usersDto);
        log.info("User updated id={}", userId);
        return ResponseEntity.ok(new ResponseDto<>(updated,String.valueOf(HttpStatus.OK.value()),updated != null ? "User updated successfully" : "User update failed"));
    }

    /**
     * Update a user while validating tenant from the request JWT.
     */
    @Operation(summary = "Update user by tenant", description = "Update a user while validating tenant from the request JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @PutMapping("/update")
    public ResponseEntity<ResponseDto<UsersDto>> updateUserByParents(
            HttpServletRequest request,
            @Parameter(description = "User identifier", required = true) @RequestParam String userId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Update user {} using tenant from request", userId);
        UsersDto updated = userService.updateUsersByParent(request, userId, usersDto);
        log.info("User updated id={} by parent", userId);
        return ResponseEntity.ok(new ResponseDto<>(updated,String.valueOf(HttpStatus.OK.value()),updated != null ? "User updated successfully" : "User update failed"));
    }

    // ============================================================
    // DELETE USER
    // ============================================================

    /**
     * Delete a user by global id. Returns boolean status in response map.
     */
    @Operation(summary = "Delete user by ID", description = "Delete a user identified by the global user ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted status returned"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<ResponseDto<Map<String, Object>>> deleteUser(@Parameter(description = "Global user identifier", required = true) @PathVariable String userId) {

        log.info("API: Delete user {}", userId);

        boolean success = userService.deleteUser(userId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", success);
        resp.put("userId", userId);

        log.info("Delete completed for userId={}, deleted={}", userId, success);
        return ResponseEntity.ok(new ResponseDto<>(resp,String.valueOf(HttpStatus.ACCEPTED.value()),success ? "User deleted successfully" : "User deletion failed"));
    }

    /**
     * Validate uniqueness of username, phone, or email. Returns the first non-null validation message.
     */
    @Operation(summary = "Validate unique fields", description = "Validate uniqueness of username, phone, or email and return the first validation message")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validation result returned"),
            @ApiResponse(responseCode = "400", description = "No valid parameter supplied or bad request")
    })
    @GetMapping("/check")
        public ResponseEntity<?> uniqueValidations(HttpServletRequest request,
            @Parameter(description = "Username to check", required = false) @RequestParam(required = false) String userName,
            @Parameter(description = "Phone number to check", required = false) @RequestParam(required = false) String phoneNumber,
            @Parameter(description = "Email to check", required = false) @RequestParam(required = false) String email) {
        log.info("API: Unique validations requested");
        if (userName != null) {
            log.debug("Checking username uniqueness for {}", userName);
            boolean result = userService.checkUserName(userName);
            log.info("Username validation returned result for {}", userName);
            return ResponseEntity.ok(new ResponseDto<>(result,String.valueOf(HttpStatus.OK.value()),result ? "Username Already Exists" : "Username Available"));
        }
        if (phoneNumber != null) {
            log.debug("Retrieving users for phone number {}", phoneNumber);
            Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
            String tenantId = null;
            if (tenantFromRequest != null) tenantId = tenantFromRequest.getTenantID();

            List<UserPhoneCheckDto> users = userService.getUsersByPhoneNumber(phoneNumber, tenantId);

            Map<String, Object> resp = new HashMap<>();
            resp.put("count", users.size());
            resp.put("users", users);
            String message = users.isEmpty() ? "No users found for this phone number" : users.size() + " user(s) found with phone number " + phoneNumber;
            resp.put("message", message);

            return ResponseEntity.ok(resp);
        }
        if (email != null) {
            log.debug("Checking email uniqueness for {}", email);
            boolean result = userService.checkEmail(email);
            log.info("Email validation returned result for {}", email);
            return ResponseEntity.ok(new ResponseDto<>(result,String.valueOf(HttpStatus.OK.value()),result ? "Email Already Exists" : "Email Available"));
        }
        log.warn("No valid parameter supplied for uniqueness validation");
        return ResponseEntity.badRequest().body(new ResponseDto<>(false,String.valueOf(HttpStatus.BAD_REQUEST.value()),"No valid parameter supplied"));
    }

    /**
     * Resend verification email for a user belonging to the specified tenant.
     *
     * This endpoint triggers the resend flow in the user service. The request
     * may be authenticated; the servlet request is available for extracting
     * auth/tenant context if needed by the service.
     *
     * @param request   HttpServletRequest carrying authentication details
     * @param tenantId  Tenant identifier for which to resend the verification
     * @param userId    User identifier to resend the verification for
     * @return          ResponseEntity containing a confirmation message and identifiers
     */
    @Operation(summary = "Resend verification email", description = "Trigger resend of a user's verification email for the given tenant and user ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification email re-sent successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Tenant or user not found")
    })
    @PostMapping("/tenants/{tenantId}/users/{userId}/resend-verification")
    public ResponseEntity<ResponseDto<Map<String, Object>>> resendVerification(
            HttpServletRequest request,
            @Parameter(description = "Tenant identifier", required = true) @PathVariable String tenantId,
            @Parameter(description = "User identifier", required = true) @PathVariable String userId) {

        log.info("API called: resend verification for tenantId={}, userId={}", tenantId, userId);

        userService.resendVerificationEmail(tenantId, userId);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Verification email has been re-sent successfully");
        body.put("tenantId", tenantId);
        body.put("userId", userId);

        return ResponseEntity.ok(new ResponseDto<>(body,String.valueOf(HttpStatus.ACCEPTED.value()),"Verification email re-sent successfully"));
    }

}