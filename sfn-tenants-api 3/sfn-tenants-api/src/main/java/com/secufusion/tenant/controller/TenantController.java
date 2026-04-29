package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.exception.GlobalException;
import com.secufusion.tenant.service.TenantService;
import com.secufusion.tenant.util.KeycloakAdminUtil;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import com.secufusion.tenant.dto.LoggedInUserDetailsBean;
import com.secufusion.tenant.dto.SetTempPasswordRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST controller responsible for tenant lifecycle operations:
 * - Create a new tenant
 * - Retrieve tenant by id (when caller is parent)
 * - Retrieve tenant hierarchy visible to the caller
 * - Update an existing tenant
 * - Delete a tenant
 * - Check tenant field availability/existence (single parameter per call)
 * - Create an extension OAuth/OpenID client for a tenant
 *
 * This controller delegates business logic to {@link TenantService} and focuses on:
 * - request validation handling
 * - mapping responses and HTTP status codes
 * - structured logging for operations (entry, success, failure)
 *
 * Cross-origin requests are allowed from any origin for simplicity in APIs used by
 * UI or other services. Adjust `@CrossOrigin` configuration for stricter security
 * in production environments.
 */
@RestController
@RequestMapping("/api/tenants")
@Slf4j
@Tag(name = "Tenants", description = "APIs for tenant management and related operations")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private KeycloakAdminUtil keycloakAdminUtil;

    /**
     * Create a new tenant from the provided payload.
     *
     * Behavior:
     * - Validates the request body.
     * - Delegates creation to {@link TenantService#createTenant}.
     * - Returns 200 OK with the created tenant payload on success.
     * - Returns 400 Bad Request when validation fails (handled by framework).
     * - Returns 500 Internal Server Error on unexpected server-side errors.
     *
     * @param request HTTP servlet request for auth/context (hidden from docs)
     * @param req     validated create tenant request body
     * @return ResponseEntity containing created {@link TenantResponse} or error status
     */
    /**
     * Create a new tenant asynchronously.
     * <p>
     * Validates input and persists the tenant skeleton synchronously,
     * then kicks off Keycloak provisioning in the background.
     * Returns 202 Accepted immediately with the tenant ID and status.
     * <p>
     * The frontend should poll {@code GET /api/tenants/{id}/provisioning-status}
     * to track provisioning progress until status becomes ACTIVE or FAILED.
     */
    @PostMapping
    @Operation(
            summary = "Create tenant (async)",
            description = "Validates and persists tenant skeleton, then provisions Keycloak resources in background. "
                    + "Returns 202 Accepted immediately. Poll GET /api/tenants/{id}/provisioning-status for progress.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Tenant creation accepted, provisioning in progress",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TenantResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation or client input error"),
                    @ApiResponse(responseCode = "409", description = "Tenant already exists or creation in progress"),
                    @ApiResponse(responseCode = "500", description = "Unexpected server error")
            }
    )
    public ResponseEntity<TenantResponse> createTenant(
            @Parameter(hidden = true) HttpServletRequest request,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Tenant creation payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateTenantRequest.class))
            )
            @Valid @RequestBody CreateTenantRequest req) {

        log.info("createTenant: tenantName={}", req.getTenantName());
        TenantResponse response = tenantService.createTenant(request, req);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Poll tenant provisioning status.
     * <p>
     * Returns the current provisioning state of a tenant.
     * Status flow: CREATING → CREATED_LOCAL → REALM_CREATED → CLIENT_CREATED → USER_CREATED → ACTIVE
     * On failure: status becomes FAILED.
     *
     * @param id tenant UUID
     * @return TenantResponse with current status
     */
    @GetMapping("/{id}/provisioning-status")
    @Operation(
            summary = "Get tenant provisioning status",
            description = "Poll this endpoint to track async tenant creation progress. "
                    + "Status transitions: CREATING → CREATED_LOCAL → REALM_CREATED → CLIENT_CREATED → USER_CREATED → ACTIVE. "
                    + "On failure: FAILED.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Current provisioning status",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TenantResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<TenantResponse> getProvisioningStatus(
            @Parameter(description = "Tenant ID", required = true) @PathVariable String id) {
        return ResponseEntity.ok(tenantService.getProvisioningStatus(id));
    }

    /**
     * Manually retry provisioning for a FAILED or ABANDONED tenant.
     * Resets the retry counter and triggers provisioning again.
     */
    @PostMapping("/{id}/retry-provisioning")
    @Operation(
            summary = "Retry tenant provisioning (manual)",
            description = "Admin-triggered retry for a tenant stuck in FAILED or ABANDONED status. "
                    + "Resets the retry counter and re-triggers async provisioning. "
                    + "Poll GET /api/tenants/{id}/provisioning-status to track progress.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Retry accepted, provisioning restarted"),
                    @ApiResponse(responseCode = "400", description = "Tenant is already ACTIVE or provisioning in progress"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<ResponseDto<String>> retryProvisioning(
            @Parameter(description = "Tenant ID", required = true) @PathVariable String id) {

        log.info("retryProvisioning: manual retry requested for tenantId={}", id);
        tenantService.manualRetryProvisioning(id);
        return ResponseEntity.accepted()
                .body(new ResponseDto<>("Provisioning retry accepted. Poll /api/tenants/" + id + "/provisioning-status for progress.", "202"));
    }

    /**
     * Repair ACTIVE tenants that have missing finalization artifacts
     * (auth_provider_config, default policies, tenant code, default groups).
     * All repair steps are idempotent — safe to call repeatedly.
     */
    @PostMapping("/repair-active-tenants")
    @Operation(
            summary = "Repair ACTIVE tenants with missing configs",
            description = "Scans all ACTIVE tenants and re-runs finalization steps for any missing artifacts: "
                    + "auth_provider_config, tenant code, default policies, default events groups. "
                    + "All steps are idempotent. Returns the number of tenants that needed repair.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Repair completed"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<ResponseDto<String>> repairActiveTenants() {
        log.info("repairActiveTenants: admin triggered tenant repair");
        int repaired = tenantService.repairActiveTenants();
        return ResponseEntity.ok(
                new ResponseDto<>(repaired + " tenant(s) repaired.", String.valueOf(HttpStatus.OK.value()))
        );
    }

    /**
     * Repair a single ACTIVE tenant by ID.
     * Creates only the finalization artifacts that are missing.
     */
    @PostMapping("/{id}/repair")
    @Operation(
            summary = "Repair a single ACTIVE tenant",
            description = "Checks and creates any missing finalization artifacts (auth_provider_config, "
                    + "tenant code, login URL, default policies, events groups, subscription) for the given tenant. "
                    + "Only creates what is missing — skips everything that already exists.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Repair completed"),
                    @ApiResponse(responseCode = "400", description = "Tenant is not ACTIVE"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<ResponseDto<String>> repairTenant(
            @Parameter(description = "Tenant ID", required = true) @PathVariable String id) {
        log.info("repairTenant: repair requested for tenantId={}", id);
        boolean repaired = tenantService.repairTenantById(id);
        String message = repaired
                ? "Tenant repaired — missing artifacts created."
                : "Tenant is healthy — nothing missing.";
        return ResponseEntity.ok(
                new ResponseDto<>(message, String.valueOf(HttpStatus.OK.value()))
        );
    }

    /**
     * Normalize all Keycloak realm names to lowercase.
     * Renames mixed-case realms in Keycloak and updates DB to match.
     * Idempotent — skips realms that are already lowercase.
     * WARNING: Active sessions in renamed realms will be invalidated.
     */
    @PostMapping("/normalize-realm-names")
    @Operation(
            summary = "Normalize Keycloak realm names to lowercase",
            description = "Scans all tenants where realm_name differs from its lowercase form. "
                    + "Renames the Keycloak realm and updates the DB record. "
                    + "Also updates auth_provider_config URLs and login URLs to reflect the new realm name. "
                    + "Idempotent — safe to call repeatedly. WARNING: active sessions will be invalidated for renamed realms.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Normalization completed"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<ResponseDto<String>> normalizeRealmNames() {
        log.info("normalizeRealmNames: admin triggered realm name normalization");
        String result = tenantService.normalizeRealmNamesToLowercase();
        return ResponseEntity.ok(
                new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value()))
        );
    }

    /**
     * Resend the welcome email and password-reset email for an ACTIVE tenant's admin.
     */
    @PostMapping("/{id}/resend-welcome-email")
    @Operation(
            summary = "Resend welcome email",
            description = "Resends the welcome email and required-action email (password reset, email verify) "
                    + "to the admin user of an ACTIVE tenant.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Email sent successfully"),
                    @ApiResponse(responseCode = "400", description = "Tenant is not ACTIVE or has no admin user"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<ResponseDto<String>> resendWelcomeEmail(
            @Parameter(description = "Tenant ID", required = true) @PathVariable String id) {

        log.info("resendWelcomeEmail: requested for tenantId={}", id);
        tenantService.resendWelcomeEmail(id);
        return ResponseEntity.ok(new ResponseDto<>("Welcome email sent successfully.", "200"));
    }

    /**
     * Resend only the reset-password (required-action) email for an ACTIVE tenant's admin.
     * Use this when the admin received the welcome email but never set their password.
     */
    @PostMapping("/{id}/resend-reset-password")
    @Operation(
            summary = "Resend reset-password email",
            description = "Triggers Keycloak to resend only the UPDATE_PASSWORD required-action email "
                    + "to the admin user of an ACTIVE tenant. Use when the admin never set their password.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reset password email sent successfully"),
                    @ApiResponse(responseCode = "400", description = "Tenant is not ACTIVE or Keycloak ID missing"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<ResponseDto<String>> resendResetPasswordEmail(
            @Parameter(description = "Tenant ID", required = true) @PathVariable String id) {

        log.info("resendResetPasswordEmail: requested for tenantId={}", id);
        tenantService.resendResetPasswordEmail(id);
        return ResponseEntity.ok(new ResponseDto<>("Reset password email sent successfully.", "200"));
    }

    /**
     * Set a temporary password for the default admin user of a sub-tenant.
     * <p>
     * Use this when the Enterprise tenant has no email provider (e.g. no Microsoft 365
     * subscription) and cannot receive the Keycloak welcome/setup email. The MSSP admin
     * sets a temporary password and delivers it to the Enterprise admin out-of-band.
     * Keycloak forces a password change on first login.
     * <p>
     * Authorization: the JWT caller must be the direct parent (managing MSSP) of the
     * target tenant.
     */
    @PostMapping("/{id}/admin-user/set-temp-password")
    @Operation(
            summary = "Set temporary password for sub-tenant admin user",
            description = "Allows an MSSP admin to set a temporary password for the admin user of a "
                    + "managed Enterprise sub-tenant. Intended for tenants without an email provider "
                    + "(e.g. no Microsoft 365 subscription). The password is marked temporary — "
                    + "the admin must change it on first login.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Temporary password set successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid password or tenant not fully provisioned"),
                    @ApiResponse(responseCode = "403", description = "Caller is not the parent of the target tenant"),
                    @ApiResponse(responseCode = "404", description = "Tenant or admin user not found")
            }
    )
    public ResponseEntity<ResponseDto<String>> setAdminTemporaryPassword(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "Target sub-tenant ID", required = true) @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Temporary password payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = SetTempPasswordRequest.class))
            )
            @Valid @RequestBody SetTempPasswordRequest body) {

        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String callerTenantId = loggedInUser.getTenantId();

        log.info("setAdminTemporaryPassword: callerTenantId={} targetTenantId={}", callerTenantId, id);
        tenantService.setAdminTemporaryPassword(callerTenantId, id, body.getTemporaryPassword());
        return ResponseEntity.ok(new ResponseDto<>(
                "Temporary password set. Admin must change password on first login.", "200"));
    }

    /**
     * Sync roles for an existing selfManaged tenant.
     * Assigns ENTERPRISE ADMIN role to the admin group if missing.
     * Use this for tenants created before the multi-role provisioning fix.
     */
    @PostMapping("/{id}/sync-self-managed-roles")
    @Operation(
            summary = "Sync roles for selfManaged tenant",
            description = "Assigns the ENTERPRISE ADMIN role to the admin group of an existing "
                    + "selfManaged MSSP/Master MSSP tenant. Use this to fix tenants created before "
                    + "the multi-role provisioning was in place.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Roles synced successfully"),
                    @ApiResponse(responseCode = "400", description = "Tenant is not selfManaged or has no admin group"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<ResponseDto<String>> syncSelfManagedRoles(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "Target tenant ID", required = true) @PathVariable String id) {

        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String callerTenantId = loggedInUser.getTenantId();

        log.info("syncSelfManagedRoles: callerTenantId={} targetTenantId={}", callerTenantId, id);
        tenantService.syncSelfManagedRoles(callerTenantId, id);
        return ResponseEntity.ok(new ResponseDto<>(
                "Roles synced successfully. ENTERPRISE ADMIN role is now assigned to the admin group.", "200"));
    }

    /**
     * Retrieve a tenant by id if the caller has parent privileges.
     *
     * Notes:
     * - This endpoint expects a query parameter `id`.
     * - If the tenant does not exist or is not visible to the caller, 404 is returned.
     *
     * @param request HTTP servlet request for auth/context (hidden from docs)
     * @param id      tenant id to fetch
     * @return ResponseEntity with {@link TenantResponse} or 404 when not found
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get tenant by id",
            description = "Retrieve a tenant by its id. Caller must be a parent or the tenant itself.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tenant found",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Tenant.class))),
                    @ApiResponse(responseCode = "404", description = "Tenant not found"),
                    @ApiResponse(responseCode = "403", description = "Access denied")
            }
    )
    public ResponseEntity<Tenant> getTenant(
            @Parameter(hidden = true) HttpServletRequest request,
            @PathVariable String id
    ) {

        log.debug("getTenant: id={}", id);

        return ResponseEntity.ok(
                tenantService.getTenantIfParent(request, id)
        );
    }


    /**
     * Return the full tenant hierarchy visible to the calling principal.
     *
     * Behavior:
     * - Delegates to {@link TenantService#getTenantHierarchy}.
     * - Always returns 200 with a list (empty list if nothing visible).
     *
     * @param request HTTP servlet request for auth/context (hidden from docs)
     * @return list of {@link TenantResponse} representing visible hierarchy
     */
    @GetMapping
    @Operation(
            summary = "Get tenant hierarchy",
            description = "Return the tenant hierarchy visible to the current caller. Returns an empty list when no tenants are visible.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Hierarchy returned",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = TenantResponse.class)))),
                    @ApiResponse(responseCode = "500", description = "Unexpected server error")
            }
    )
    public ResponseEntity<List<TenantResponse>> getAll(@Parameter(hidden = true) HttpServletRequest request) {
        log.debug("getAll - entry: fetching tenant hierarchy for caller");
        return ResponseEntity.ok(
                tenantService.getTenantHierarchy(request)
        );
    }

    /**
     * Update fields of an existing tenant identified by path variable `id`.
     *
     * Behavior:
     * - Validates request body.
     * - Delegates update operation to {@link TenantService#updateTenantWithSubscription}.
     * - If packageId is provided, automatically upgrades the subscription.
     * - Returns 200 with updated tenant on success, 404 if not found.
     *
     * @param request HTTP servlet request for auth/context (hidden from docs)
     * @param id      id of tenant to update
     * @param req     validated update payload with optional packageId for subscription upgrade
     * @return updated {@link TenantResponse} or 404 when tenant not found
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "Update tenant",
            description = "Updates tenant metadata and optionally upgrades subscription. " +
                    "If packageId is provided, the tenant's subscription will be upgraded to the new package. " +
                    "Keycloak sync is optional and explicit.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tenant updated successfully",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TenantResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request or subscription upgrade failed"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found"),
                    @ApiResponse(responseCode = "403", description = "Access denied")
            }
    )
    public ResponseEntity<TenantResponse> updateTenant(
            HttpServletRequest request,
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Tenant update payload with optional subscription upgrade",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateTenantRequest.class))
            )
            @RequestBody UpdateTenantRequest req,

            @Parameter(
                    description = "If true, sync changes with Keycloak (disable realm, suspend users, etc.)",
                    example = "false"
            )
            @RequestParam(
                    name = "syncWithKeycloak",
                    required = false,
                    defaultValue = "false"
            )
            boolean syncWithKeycloak
    ) {
        log.info("updateTenant: tenantId={}, packageId={}", id, req.getPackageId());
        return ResponseEntity.ok(
                tenantService.updateTenantWithSubscription(request, id, req, syncWithKeycloak)
        );
    }


    /**
     * Delete a tenant by id.
     *
     * Behavior:
     * - Delegates to {@link TenantService#}.
     * - Returns 204 No Content on successful deletion.
     * - Returns 404 when the tenant does not exist.
     *
     * @param id id of tenant to delete
     * @return 204 No Content on success, 404 if not found, 500 on errors
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Hard delete tenant",
            description = "Permanently deletes a tenant and all associated data including Keycloak realm. " +
                    "This operation is irreversible and requires explicit confirmation.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Tenant deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Delete confirmation missing"),
                    @ApiResponse(responseCode = "403", description = "Access denied"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found"),
                    @ApiResponse(responseCode = "500", description = "Deletion failed")
            }
    )
    public ResponseEntity<Void> deleteTenant(
            HttpServletRequest request,

            @Parameter(
                    name = "id",
                    description = "Tenant ID to hard delete",
                    required = true
            )
            @PathVariable String id,
            @RequestBody DeleteTenantRequest deleteRequest
    ) {

        log.warn("⚠ HARD DELETE API called for tenantId={}", id);

        tenantService.hardDeleteTenant(request, id, deleteRequest);

        return ResponseEntity.noContent().build();
    }


    /**
     * Check availability or existence of a tenant field.
     *
     * Rules:
     * - Only one parameter should be provided per request. If multiple are provided, the first matching param in
     *   the order (tenantName, domainName, phoneNumber, tenantEmail) will be checked.
     * - Returns boolean true/false indicating availability/existence semantics defined by service methods.
     *
     * @param tenantName  optional tenant name to check availability
     * @param domainName  optional domain name to check existence
     * @param phoneNumber optional phone number to check
     * @param tenantEmail optional tenant email to check
     * @return 200 with boolean result when a parameter is provided, 400 when none provided
     */
    @GetMapping("/check")
    @Operation(
            summary = "Check tenant fields",
            description = "Check availability/existence for a single tenant field. Provide only one of: tenantName, domainName, phoneNumber, tenantEmail.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Check result returned",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Boolean.class))),
                    @ApiResponse(responseCode = "400", description = "No parameters provided"),
                    @ApiResponse(responseCode = "500", description = "Unexpected server error")
            }
    )
    public ResponseEntity<ResponseDto<Boolean>> checkTenant(
            @RequestParam(required = false) String tenantId,
            @Parameter(name = "tenantName", description = "Tenant name to check", required = false) @RequestParam(required = false) String tenantName,
            @Parameter(name = "domainName", description = "Domain name to check", required = false) @RequestParam(required = false) String domainName,
            @Parameter(name = "phoneNumber", description = "Phone number to check", required = false) @RequestParam(required = false) String phoneNumber,
            @Parameter(name = "tenantEmail", description = "Tenant email to check", required = false) @RequestParam(required = false) String tenantEmail) {

        log.debug("checkTenant - entry: tenantName='{}', domainName='{}', phoneNumber='{}', tenantEmail='{}'",
                tenantName, domainName, phoneNumber, tenantEmail);

        if (tenantName != null) {
            boolean exists = tenantService.checkTenantNameAvailability(tenantName, tenantId);
            return ResponseEntity.ok(new ResponseDto<>(exists, "200", exists ?
                    "Tenant Name Already Exists" : "Tenant Name Available"));
        }

        if (domainName != null) {
            boolean exists = tenantService.checkExistsByDomain(domainName, tenantId);
            return ResponseEntity.ok(new ResponseDto<>(exists, "200", exists ?
                    "Domain Name Already Exists" : "Domain Name Available"));
        }

        if (phoneNumber != null) {
            boolean exists = tenantService.checkPhoneNumber(phoneNumber, tenantId);
            return ResponseEntity.ok(new ResponseDto<>(exists, "200", exists ?
                    "Phone Number Already Exists" : "Phone Number Available"));
        }

        if (tenantEmail != null) {

            String validationMessage = tenantService.checkEmail(tenantEmail, tenantId);
            boolean exists = validationMessage != null;

            log.info("EMAIL validation : {}",
                    exists ? validationMessage : "Tenant Email Available");

            if (exists) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ResponseDto<>(true, "409", validationMessage));
            }

            return ResponseEntity.ok(
                    new ResponseDto<>(false, "200", "Tenant Email Available")
            );

        }


        throw new GlobalException("No parameters provided");
    }


    /**
     * Create an extension OAuth/OpenID client for the specified tenant.
     *
     * Behavior:
     * - Expects tenantId and redirectUrl as request parameters.
     * - Delegates to {@link TenantService#createExtensionClient}.
     * - Returns 200 OK with a success message when client creation succeeds.
     * - Returns 400 Bad Request for invalid input and 500 for other errors.
     *
     * @param request     HTTP servlet request for auth/context (hidden from docs)
     * @param tenantId    Tenant identifier for which the client will be created (required)
     * @param redirectUrl Redirect URL for the created client (required)
     * @return 200 OK with message on success, 400 Bad Request for client input errors, 500 on server errors
     */
    @PostMapping("/clients")
    @Operation(
            summary = "Create extension client for tenant",
            description = "Create an OAuth/OpenID extension client for a tenant. Requires tenantId and redirectUrl parameters.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Client created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request or parameters"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<ResponseDto<String>> createClientForTenant(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(name = "tenantId", description = "Tenant id to create the client for", required = true) @RequestParam String tenantId,
            @Parameter(name = "redirectUrl", description = "Redirect URL for the new client", required = true) @RequestParam String redirectUrl) {

        log.info("createClientForTenant - entry: tenantId='{}', redirectUrl='{}'", tenantId, redirectUrl);
        String message = tenantService.createExtensionClient(tenantId, redirectUrl, request);
        return ResponseEntity.ok(new ResponseDto<>(message, "200"));
    }

    @Operation(summary = "Get tenant config (validated)",
            description = "Returns tenant authentication configuration after validating the Referer header and request host. " +
                    "Supports lookup by host, companyName, or tenantCode (for MSI deployments).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant configuration found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthDetailsDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - invalid referer or host"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Parameter(name = "Referer", in = ParameterIn.HEADER, description = "Referer header containing origin URL", required = true)
    @GetMapping("/tenant-config/v1")
    public ResponseEntity<ResponseDto<AuthDetailsDto>> getTenantConfig(
            HttpServletRequest request,
            @Parameter(description = "Host/domain", required = false) @RequestParam(required = false) String host,
            @Parameter(description = "Company name (e.g., 'companyName', 'companyName.com', or '@companyName.com')", required = false) @RequestParam(required = false) String companyName,
            @Parameter(description = "Unique tenant code for MSI deployment identification (e.g., 'ACME-2024')", required = false) @RequestParam(required = false) String tenantCode) {

        // Extract referer
        String refererHeader = request.getHeader("Referer");
        if (refererHeader == null || refererHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // Domain from referer
            URI refererUri = new URI(refererHeader);
            String refererDomain = refererUri.getHost();

            // Domain from request URL
            URI requestUri = new URI(request.getRequestURL().toString());
            String requestDomain = requestUri.getHost();

            // Normalize host param
            String expectedDomain = host.toLowerCase().trim();

            log.info("Validation check => requestDomain={}, refererDomain={}, hostParam={}, tenantCode={}",
                    requestDomain, refererDomain, expectedDomain, tenantCode);

            // STRICT MATCHING RULES
            if (!expectedDomain.equalsIgnoreCase(refererDomain) ||
                    !expectedDomain.equalsIgnoreCase(requestDomain) ||
                    !refererDomain.equalsIgnoreCase(requestDomain)) {

                log.warn("Domain validation failed");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Passed all checks → return config
            return ResponseEntity.ok(
                    new ResponseDto<>(
                            tenantService.getTenantConfig(host, companyName, tenantCode),
                            String.valueOf(HttpStatus.OK.value())
                    )
            );

        } catch (Exception e) {
            log.error("Error validating referer", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Operation(summary = "Get tenant config (no validation)",
            description = "Returns tenant authentication configuration without referer/host validation. " +
                    "Can lookup by host/domain, companyName (extracted from tenant email), or tenantCode (for MSI deployments). " +
                    "Lookup priority: tenantCode > host > companyName")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant configuration found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthDetailsDto.class))),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/tenant-config")
    public ResponseEntity<ResponseDto<AuthDetailsDto>> getTenantConfig(
            @Parameter(description = "Host/domain", required = false) @RequestParam(required = false) String host,
            @Parameter(description = "Company name (e.g., 'companyName', 'companyName.com', or '@companyName.com')", required = false) @RequestParam(required = false) String companyName,
            @Parameter(description = "Unique tenant code for MSI deployment identification (e.g., 'ACME-2024')", required = false) @RequestParam(required = false) String tenantCode) {
        return ResponseEntity.ok(
                new ResponseDto<>(
                        tenantService.getTenantConfig(host, companyName, tenantCode),
                        String.valueOf(HttpStatus.OK.value())
                )
        );
    }

    @Operation(summary = "Diagnose Azure SSO configuration",
            description = "Performs comprehensive diagnostic check of Azure SSO configuration:\n" +
                    "- Master-hub IdP authorization URL\n" +
                    "- Master-hub IdP mappers (azure_tenant_id, roles, groups)\n" +
                    "- Tenant client mappers\n" +
                    "- Broker client mappers\n" +
                    "- Master Azure IdP mappers\n" +
                    "- Organization existence and IdP linking\n\n" +
                    "Returns detailed report with issues, warnings, and recommendations. " +
                    "Use this before repair-sso to understand what needs fixing.")
    @ApiResponse(responseCode = "200", description = "Diagnostic completed successfully")
    @ApiResponse(responseCode = "404", description = "Tenant not found")
    @ApiResponse(responseCode = "500", description = "Diagnostic failed")
    @GetMapping("/{realmName}/diagnose-sso")
    public ResponseEntity<ResponseDto<SsoDiagnosticDto>> diagnoseSso(
            @Parameter(description = "Keycloak realm name for the tenant", required = true)
            @PathVariable String realmName,
            @Parameter(description = "Azure IdP alias in master realm (default: microsoft)", required = false)
            @RequestParam(defaultValue = "microsoft") String azureIdpAlias) {

        log.info("🔍 Starting SSO diagnosis: realmName={}, azureIdpAlias={}", realmName, azureIdpAlias);

        try {
            // Get tenant details from database
            Tenant tenant = tenantService.getTenantByKeycloakRealmName(realmName);
            if (tenant == null) {
                log.error("❌ Tenant not found for realm: {}", realmName);
                return ResponseEntity.status(404)
                        .body(new ResponseDto<>(null, "404", "Tenant not found for realm: " + realmName));
            }

            String tenantName = tenant.getTenantName();
            String domain = tenant.getTenantName() != null && !tenant.getTenantName().isBlank()
                    ? tenant.getTenantName()
                    : tenantName.toLowerCase().replaceAll("\\s+", "") + ".secufusion.com";

            log.info("📋 Tenant details: name={}, domain={}, realmName={}", tenantName, domain, realmName);

            // Execute SSO diagnostic
            SsoDiagnosticDto diagnostic = keycloakAdminUtil.diagnoseSsoConfiguration(
                    realmName, tenantName, domain, azureIdpAlias);

            log.info("✅ Diagnostic complete: {} issues, {} warnings",
                    diagnostic.getIssues().size(), diagnostic.getWarnings().size());

            return ResponseEntity.ok(new ResponseDto<>(diagnostic, "200"));

        } catch (Exception e) {
            log.error("❌ SSO diagnosis failed for realm: {}", realmName, e);
            return ResponseEntity.status(500)
                    .body(new ResponseDto<>(null, "500", "SSO diagnosis failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Repair Azure SSO for a tenant (Complete)",
            description = "Comprehensive SSO repair that fixes all Azure SSO configurations:\n" +
                    "1. Fixes master-hub IdP authorizationUrl in tenant realm (adds kc_idp_hint=microsoft)\n" +
                    "2. Configures master-hub IdP mappers in tenant realm (import azure_tenant_id, roles, groups)\n" +
                    "3. Configures tenant realm client mappers (write attributes into app JWT)\n" +
                    "4. Configures broker client mappers in master realm (include attributes in broker token)\n" +
                    "5. Repairs master realm Azure IdP mappers (delete stale, recreate correct syncMode)\n" +
                    "6. Creates organization in master realm (if not exists)\n" +
                    "7. Links Azure IdP to organization (if not linked)\n\n" +
                    "Call this on any existing Azure tenant that shows the Keycloak login screen or has SSO issues.")
    @ApiResponse(responseCode = "200", description = "SSO repaired successfully with organization setup")
    @ApiResponse(responseCode = "404", description = "Tenant not found")
    @ApiResponse(responseCode = "500", description = "SSO repair failed")
    @PostMapping("/{realmName}/repair-sso")
    public ResponseEntity<ResponseDto<String>> repairAzureSso(
            @Parameter(description = "Keycloak realm name for the tenant", required = true)
            @PathVariable String realmName,
            @Parameter(description = "Azure IdP alias in master realm (default: microsoft)", required = false)
            @RequestParam(defaultValue = "microsoft") String azureIdpAlias) {

        log.info("🔧 Starting complete SSO repair: realmName={}, azureIdpAlias={}", realmName, azureIdpAlias);

        try {
            // Get tenant details from database
            Tenant tenant = tenantService.getTenantByKeycloakRealmName(realmName);
            if (tenant == null) {
                log.error("❌ Tenant not found for realm: {}", realmName);
                return ResponseEntity.status(404)
                        .body(new ResponseDto<>("Tenant not found for realm: " + realmName, "404"));
            }

            String tenantName = tenant.getTenantName();
            String domain = tenant.getTenantName() != null && !tenant.getTenantName().isBlank()
                    ? tenant.getTenantName()
                    : tenantName.toLowerCase().replaceAll("\\s+", "") + ".secufusion.com";

            log.info("📋 Tenant details: name={}, domain={}, realmName={}", tenantName, domain, realmName);

            // Execute comprehensive SSO repair (all 7 steps)
            keycloakAdminUtil.repairAzureSsoComplete(realmName, tenantName, domain, azureIdpAlias);

            String successMessage = String.format(
                    "✅ Complete SSO repair successful for realm '%s':\n" +
                    "- Fixed master-hub IdP authorization URL\n" +
                    "- Configured IdP and client mappers\n" +
                    "- Repaired master Azure IdP mappers\n" +
                    "- Created organization '%s' in master realm\n" +
                    "- Linked Azure IdP '%s' to organization",
                    realmName, tenantName, azureIdpAlias
            );

            log.info("✅ {}", successMessage);
            return ResponseEntity.ok(new ResponseDto<>(successMessage, "200"));

        } catch (Exception e) {
            log.error("❌ SSO repair failed for realm: {}", realmName, e);
            return ResponseEntity.status(500)
                    .body(new ResponseDto<>("SSO repair failed: " + e.getMessage(), "500"));
        }
    }
}