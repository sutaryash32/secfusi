package com.secufusion.iam.controller;

import com.secufusion.iam.dto.UsersDto;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.service.UserService;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    // AUTH CUSTOM RESPONSE
    // ============================================================

    /**
     * Return a custom authentication response extracted from the Jwt principal.
     * Logs the user id and token length at debug level.
     */
    @Operation(summary = "Custom authentication response", description = "Return user information extracted from the JWT principal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Custom auth response returned"),
            @ApiResponse(responseCode = "400", description = "No JWT provided")
    })
    @GetMapping("/custom-response")
    public ResponseEntity<Map<String, Object>> getCustomResponse(@AuthenticationPrincipal Jwt jwt) {

        log.info("API: Custom auth response requested");
        if (jwt == null) {
            log.warn("No JWT principal available in request");
            return ResponseEntity.badRequest().body(Map.of("message", "No JWT provided"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("userId", jwt.getSubject());
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("accessToken", jwt.getTokenValue());
        response.put("message", "Login successful");

        log.debug("Auth response prepared for userId={}, tokenLength={}", jwt.getSubject(),
                jwt.getTokenValue() != null ? jwt.getTokenValue().length() : 0);

        log.info("API: Custom auth response returning for user {}", jwt.getSubject());
        return ResponseEntity.ok(response);
    }


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
    public ResponseEntity<UsersDto> createUser(
            @Parameter(description = "Tenant identifier", required = true) @PathVariable String tenantId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Create user under tenant {}", tenantId);
        log.debug("Request payload for createUser under tenant {}: {}", tenantId, usersDto);
        UsersDto created = userService.createUser(tenantId, usersDto);
        log.info("User created with id={} under tenant={}", created != null ? created.getPkUserId() : null, tenantId);
        return ResponseEntity.ok(created);
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
    public ResponseEntity<UsersDto> createUserByTenant(
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
        return ResponseEntity.ok(created);
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
    public ResponseEntity<UsersDto> getUser(@Parameter(description = "Global user identifier", required = true) @PathVariable String userId) {

        log.info("API: Fetch user {}", userId);
        UsersDto user = userService.getUser(userId);
        log.debug("Fetched user for id={} : {}", userId, user);
        return ResponseEntity.ok(user);
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
    public ResponseEntity<UsersDto> getUserByParents(HttpServletRequest request, @Parameter(description = "User identifier", required = true) @RequestParam String userId) {

        log.info("API: Fetch user {} under tenant from request", userId);
        Tenant tenantFromEmail = jwtUtl.getTenantFromRequest(request);

        if (tenantFromEmail == null) {
            log.error("Unable to determine tenant from request for getUserByParents userId={}", userId);
            return ResponseEntity.badRequest().build();
        }

        log.debug("Tenant resolved for getUserByParents: {}", tenantFromEmail.getTenantID());
        UsersDto user = userService.getUserByParents(request, userId);
        log.info("User fetched for id={} under tenant={}", userId, tenantFromEmail.getTenantID());
        return ResponseEntity.ok(user);
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
    public ResponseEntity<List<UsersDto>> getAllUsers(HttpServletRequest request) {

        log.info("API: Fetch all users");
        List<UsersDto> users = userService.getAllUsers(request);
        log.debug("Number of users fetched: {}", users != null ? users.size() : 0);
        return ResponseEntity.ok(users);
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
    public ResponseEntity<List<UsersDto>> getUsersByTenant(@Parameter(description = "Tenant identifier", required = true) @PathVariable String tenantId) {

        log.info("API: Fetch users for tenant {}", tenantId);
        List<UsersDto> users = userService.getUsersByTenantId(tenantId);
        log.debug("Users fetched for tenant {}: {}", tenantId, users != null ? users.size() : 0);
        return ResponseEntity.ok(users);
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
    public ResponseEntity<UsersDto> updateUser(
            @Parameter(description = "Global user identifier", required = true) @PathVariable String userId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Update user {}", userId);
        log.debug("Update payload for user {}: {}", userId, usersDto);
        UsersDto updated = userService.updateUser(userId, usersDto);
        log.info("User updated id={}", userId);
        return ResponseEntity.ok(updated);
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
    public ResponseEntity<UsersDto> updateUserByParents(
            HttpServletRequest request,
            @Parameter(description = "User identifier", required = true) @RequestParam String userId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Update user {} using tenant from request", userId);
        UsersDto updated = userService.updateUsersByParent(request, userId, usersDto);
        log.info("User updated id={} by parent", userId);
        return ResponseEntity.ok(updated);
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
    public ResponseEntity<Map<String, Object>> deleteUser(@Parameter(description = "Global user identifier", required = true) @PathVariable String userId) {

        log.info("API: Delete user {}", userId);

        boolean success = userService.deleteUser(userId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", success);
        resp.put("userId", userId);

        log.info("Delete completed for userId={}, deleted={}", userId, success);
        return ResponseEntity.ok(resp);
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
    public ResponseEntity<Boolean> uniqueValidations(
            @Parameter(description = "Username to check", required = false) @RequestParam(required = false) String userName,
            @Parameter(description = "Phone number to check", required = false) @RequestParam(required = false) String phoneNumber,
            @Parameter(description = "Email to check", required = false) @RequestParam(required = false) String email) {
        log.info("API: Unique validations requested");
        if (userName != null) {
            log.debug("Checking username uniqueness for {}", userName);
            boolean result = userService.checkUserName(userName);
            if (result) {
                log.info("Username validation returned result for {}", userName);
                return ResponseEntity.ok(result);
            }
        }
        if (phoneNumber != null) {
            log.debug("Checking phone number uniqueness for {}", phoneNumber);
            boolean result = userService.checkMobileNumber(phoneNumber);
            if (result) {
                log.info("Phone number validation returned result for {}", phoneNumber);
                return ResponseEntity.ok(result);
            }
        }
        if (email != null) {
            log.debug("Checking email uniqueness for {}", email);
            boolean result = userService.checkEmail(email);
            if (result) {
                log.info("Email validation returned result for {}", email);
                return ResponseEntity.ok(result);
            }
        }
        log.warn("Unique validation request did not supply a result for any parameter");
        return ResponseEntity.badRequest().body(false);
    }
}