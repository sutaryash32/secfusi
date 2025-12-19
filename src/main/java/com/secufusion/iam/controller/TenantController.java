//package com.secufusion.iam.controller;
//
//import com.secufusion.iam.dto.CreateIdentityProviderRequest;
//import com.secufusion.iam.dto.CreateTenantRequest;
//import com.secufusion.iam.dto.TenantResponse;
//import com.secufusion.iam.entity.SsoConfiguration;
//import com.secufusion.iam.service.TenantService;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.validation.Valid;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.responses.ApiResponse;
//import io.swagger.v3.oas.annotations.media.Content;
//import io.swagger.v3.oas.annotations.media.Schema;
//import io.swagger.v3.oas.annotations.media.ArraySchema;
//
//import java.util.List;
//
///**
// * REST controller for tenant management.
// * Provides endpoints to create, read, update, delete and check tenants.
// */
//@CrossOrigin(origins = "*", allowedHeaders = "*")
//@RestController
//@RequestMapping("/tenants")
//@Slf4j
//@Tag(name = "Tenants", description = "APIs for tenant management")
//public class TenantController {
//
//    @Autowired
//    private TenantService tenantService;
//
//    /**
//     * Create a new tenant.
//     *
//     * @param request HTTP servlet request (for auth/context)
//     * @param req     validated create tenant request body
//     * @return the created TenantResponse
//     */
//    @PostMapping
//    @Operation(
//            summary = "Create tenant",
//            description = "Create a new tenant.",
//            responses = {
//                    @ApiResponse(responseCode = "200", description = "Tenant created",
//                            content = @Content(mediaType = "application/json",
//                                    schema = @Schema(implementation = TenantResponse.class))),
//                    @ApiResponse(responseCode = "400", description = "Invalid request")
//            }
//    )
//    public ResponseEntity<TenantResponse> createTenant(
//            @Parameter(hidden = true) HttpServletRequest request,
//            @io.swagger.v3.oas.annotations.parameters.RequestBody(
//                    description = "Tenant creation payload",
//                    required = true,
//                    content = @Content(schema = @Schema(implementation = CreateTenantRequest.class))
//            )
//            @Valid @RequestBody CreateTenantRequest req) {
//        log.info("createTenant - start: tenantName={}", req.getTenantName());
//        TenantResponse resp = tenantService.createTenant(request, req);
//        log.info("createTenant - completed: tenantId={}", resp != null ? resp.getTenantID() : "null");
//        return ResponseEntity.ok(resp);
//    }
//
//    /**
//     * Get a tenant by id if the caller is parent.
//     *
//     * @param request HTTP servlet request (for auth/context)
//     * @param id      tenant id to fetch
//     * @return the TenantResponse
//     */
//    @GetMapping("/id")
//    @Operation(
//            summary = "Get tenant by id",
//            description = "Get a tenant by id if the caller is parent.",
//            responses = {
//                    @ApiResponse(responseCode = "200", description = "Tenant found",
//                            content = @Content(mediaType = "application/json",
//                                    schema = @Schema(implementation = TenantResponse.class))),
//                    @ApiResponse(responseCode = "404", description = "Tenant not found")
//            }
//    )
//    public ResponseEntity<TenantResponse> getTenant(
//            @Parameter(hidden = true) HttpServletRequest request,
//            @Parameter(name = "id", description = "Tenant id to fetch", required = true) @RequestParam String id) {
//        log.debug("getTenant - start: id={}", id);
//        TenantResponse response = tenantService.getTenantIfParent(request, id);
//        log.debug("getTenant - completed: id={}, found={}", id, response != null);
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * Get full tenant hierarchy visible to caller.
//     *
//     * @param request HTTP servlet request (for auth/context)
//     * @return list of tenant responses representing the hierarchy
//     */
//    @GetMapping
//    @Operation(
//            summary = "Get tenant hierarchy",
//            description = "Get full tenant hierarchy visible to caller.",
//            responses = {
//                    @ApiResponse(responseCode = "200", description = "Hierarchy returned",
//                            content = @Content(mediaType = "application/json",
//                                    array = @ArraySchema(schema = @Schema(implementation = TenantResponse.class))))
//            }
//    )
//    public ResponseEntity<List<TenantResponse>> getAll(@Parameter(hidden = true) HttpServletRequest request) {
//        log.debug("getAll - fetching tenant hierarchy");
//        List<TenantResponse> list = tenantService.getTenantHierarchy(request);
//        log.debug("getAll - result count={}", list != null ? list.size() : 0);
//        return ResponseEntity.ok(list);
//    }
//
//    /**
//     * Update an existing tenant.
//     *
//     * @param request HTTP servlet request (for auth/context)
//     * @param id      id of tenant to update
//     * @param req     validated update payload (reuses CreateTenantRequest)
//     * @return updated TenantResponse
//     */
//    @PutMapping("/{id}")
//    @Operation(
//            summary = "Update tenant",
//            description = "Update an existing tenant.",
//            responses = {
//                    @ApiResponse(responseCode = "200", description = "Tenant updated",
//                            content = @Content(mediaType = "application/json",
//                                    schema = @Schema(implementation = TenantResponse.class))),
//                    @ApiResponse(responseCode = "404", description = "Tenant not found")
//            }
//    )
//    public ResponseEntity<TenantResponse> updateTenant(
//            @Parameter(hidden = true) HttpServletRequest request,
//            @Parameter(name = "id", description = "Tenant id to update", required = true) @PathVariable String id,
//            @io.swagger.v3.oas.annotations.parameters.RequestBody(
//                    description = "Tenant update payload",
//                    required = true,
//                    content = @Content(schema = @Schema(implementation = CreateTenantRequest.class))
//            )
//            @Valid @RequestBody CreateTenantRequest req) {
//        log.info("updateTenant - start: id={}, tenantName={}", id, req.getTenantName());
//        TenantResponse updated = tenantService.updateTenant(request, id, req);
//        log.info("updateTenant - completed: id={}, updated={}", id, updated != null);
//        return ResponseEntity.ok(updated);
//    }
//
//    /**
//     * Delete a tenant by id.
//     *
//     * @param id id of tenant to delete
//     * @return 204 No Content on success
//     */
//    @DeleteMapping("/{id}")
//    @Operation(
//            summary = "Delete tenant",
//            description = "Delete a tenant by id.",
//            responses = {
//                    @ApiResponse(responseCode = "204", description = "Tenant deleted"),
//                    @ApiResponse(responseCode = "404", description = "Tenant not found")
//            }
//    )
//    public ResponseEntity<Void> deleteTenant(
//            @Parameter(name = "id", description = "Tenant id to delete", required = true) @PathVariable String id) {
//        log.info("deleteTenant - start: id={}", id);
//        tenantService.deleteTenant(id);
//        log.info("deleteTenant - completed: id={}", id);
//        return ResponseEntity.noContent().build();
//    }
//
//    /**
//     * Check availability / existence for tenant fields.
//     * Supports one parameter at a time: tenantName, domainName, phoneNumber, tenantEmail.
//     *
//     * @param tenantName  optional tenant name to check availability
//     * @param domainName  optional domain name to check existence
//     * @param phoneNumber optional phone number to check
//     * @param tenantEmail optional tenant email to check
//     * @return string result describing availability/existence or bad request if none provided
//     */
//    @GetMapping("/check")
//    @Operation(
//            summary = "Check tenant fields",
//            description = "Check availability / existence for tenant fields. Provide one parameter at a time.",
//            responses = {
//                    @ApiResponse(responseCode = "200", description = "Check result returned",
//                            content = @Content(mediaType = "text/plain")),
//                    @ApiResponse(responseCode = "400", description = "No parameters provided")
//            }
//    )
//    public ResponseEntity<Boolean> checkTenant(
//            @Parameter(name = "tenantName", description = "Tenant name to check", required = false) @RequestParam(required = false) String tenantName,
//            @Parameter(name = "domainName", description = "Domain name to check", required = false) @RequestParam(required = false) String domainName,
//            @Parameter(name = "phoneNumber", description = "Phone number to check", required = false) @RequestParam(required = false) String phoneNumber,
//            @Parameter(name = "tenantEmail", description = "Tenant email to check", required = false) @RequestParam(required = false) String tenantEmail) {
//
//        log.debug("checkTenant - called with tenantName={}, domainName={}, phoneNumber={}, tenantEmail={}",
//                tenantName, domainName, phoneNumber, tenantEmail);
//
//        if (tenantName != null) {
//            boolean result = tenantService.checkTenantNameAvailability(tenantName);
//            log.info("checkTenant - tenantName check result={}", result);
//            return ResponseEntity.ok(result);
//        }
//
//        if (domainName != null) {
//            boolean result = tenantService.checkExistsByDomain(domainName);
//            log.info("checkTenant - domainName check result={}", result);
//            return ResponseEntity.ok(result);
//        }
//
//        if (phoneNumber != null) {
//            boolean result = tenantService.checkPhoneNumber(phoneNumber);
//            log.info("checkTenant - phoneNumber check result={}", result);
//            return ResponseEntity.ok(result);
//        }
//
//        if (tenantEmail != null) {
//            boolean result = tenantService.checkEmail(tenantEmail);
//            log.info("checkTenant - tenantEmail check result={}", result);
//            return ResponseEntity.ok(result);
//        }
//
//        log.warn("checkTenant - no parameters provided");
//        return ResponseEntity.badRequest().body(false);
//    }
//
//    /**
//         * Add an identity provider to a tenant.
//         *
//         * @param request         HTTP servlet request (for auth/context)
//         * @param providerRequest payload describing the identity provider
//         * @return result message or error details
//         */
//        @PostMapping("/sso")
//        @Operation(
//                summary = "Add identity provider to tenant",
//                description = "Adds an identity provider to the specified tenant.",
//                responses = {
//                        @ApiResponse(responseCode = "200", description = "Provider added",
//                                content = @Content(mediaType = "text/plain",
//                                        schema = @Schema(implementation = String.class))),
//                        @ApiResponse(responseCode = "400", description = "Invalid request"),
//                        @ApiResponse(responseCode = "500", description = "Internal server error")
//                }
//        )
//        public ResponseEntity<String> addProviderToTenant(
//                @Parameter(hidden = true) HttpServletRequest request,
//                @io.swagger.v3.oas.annotations.parameters.RequestBody(
//                        description = "Identity provider payload",
//                        required = true,
//                        content = @Content(schema = @Schema(implementation = CreateIdentityProviderRequest.class))
//                )
//                @Valid @RequestBody CreateIdentityProviderRequest providerRequest) {
//
//            // Log entry and basic request payload presence
//            log.info("addProviderToTenant - start");
//            log.debug("addProviderToTenant - payload={}", providerRequest != null ? providerRequest.toString() : "null");
//
//            // Defensive null check - @Valid/@RequestBody usually enforces presence, but guard here for clarity
//            if (providerRequest == null) {
//                log.warn("addProviderToTenant - bad request: providerRequest is null");
//                return ResponseEntity.badRequest().body("providerRequest is required");
//            }
//
//            try {
//                // Delegate to service and log result
//                String result = tenantService.addProviderToTenant(request, providerRequest);
//                log.info("addProviderToTenant - completed successfully");
//                log.debug("addProviderToTenant - result={}", result);
//                return ResponseEntity.ok(result);
//            } catch (Exception e) {
//                // Log full error for troubleshooting and return generic message to client
//                log.error("addProviderToTenant - failed: error={}, payload={}", e.getMessage(), providerRequest, e);
//                return ResponseEntity.status(500).body("Failed to add provider");
//            }
//        }
//
//        /**
//         * Activate a SSO configuration for a tenant.
//         * Returns the activated configuration on success.
//         */
//        @PostMapping("/sso/activate")
//        @Operation(summary = "Activate SSO configuration", description = "Activate a SSO configuration for a tenant.")
//        public ResponseEntity<SsoConfiguration> activateSso(
//                @Parameter(hidden = true) HttpServletRequest request,
//                @Parameter(name = "alias", description = "SSO config alias", required = true) @RequestParam String alias) {
//            log.info("activateSso - start: alias={}", alias);
//            try {
//                SsoConfiguration cfg = tenantService.activateSsoConfiguration(request, alias);
//                log.info("activateSso - completed: alias={}", alias);
//                return ResponseEntity.ok(cfg);
//            } catch (Exception e) {
//                log.error("activateSso - failed: alias={}, error={}", alias, e.getMessage(), e);
//                return ResponseEntity.status(500).build();
//            }
//        }
//
//        /**
//         * Deactivate a SSO configuration for a tenant.
//         * Returns the deactivated configuration on success.
//         */
//        @PostMapping("/sso/deactivate")
//        @Operation(summary = "Deactivate SSO configuration", description = "Deactivate a SSO configuration for a tenant.")
//        public ResponseEntity<SsoConfiguration> deactivateSso(
//                @Parameter(hidden = true) HttpServletRequest request,
//                @Parameter(name = "alias", description = "SSO config alias", required = true) @RequestParam String alias) {
//            log.info("deactivateSso - start: alias={}", alias);
//            try {
//                SsoConfiguration cfg = tenantService.deactivateSsoConfiguration(request, alias);
//                log.info("deactivateSso - completed: alias={}", alias);
//                return ResponseEntity.ok(cfg);
//            } catch (Exception e) {
//                log.error("deactivateSso - failed: alias={}, error={}", alias, e.getMessage(), e);
//                return ResponseEntity.status(500).build();
//            }
//        }
//
//        /**
//         * Update an existing SSO configuration by id.
//         * Logs start, end and any failures.
//         */
//        @PutMapping("/sso/{id}")
//        @Operation(summary = "Update SSO configuration", description = "Update an existing SSO configuration by id.")
//        public ResponseEntity<SsoConfiguration> updateSso(
//                @Parameter(hidden = true) HttpServletRequest request,
//                @Parameter(name = "id", description = "SSO configuration id", required = true) @PathVariable String id,
//                @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SSO configuration payload", required = true)
//                @Valid @RequestBody CreateIdentityProviderRequest payload) {
//            log.info("updateSso - start: id={}", id);
//            log.debug("updateSso - payload={}", payload != null ? payload.toString() : "null");
//            try {
//                SsoConfiguration updated = tenantService.updateSsoConfiguration(id, request, payload);
//                log.info("updateSso - completed: id={}, updated={}", id, updated != null);
//                return ResponseEntity.ok(updated);
//            } catch (Exception e) {
//                log.error("updateSso - failed: id={}, error={}", id, e.getMessage(), e);
//                return ResponseEntity.status(500).build();
//            }
//        }
//
//        /**
//         * Return the active SSO configuration for a tenant, if present.
//         */
//        @GetMapping("/sso/active")
//        @Operation(summary = "Get active SSO configuration", description = "Return the active SSO configuration for a tenant.")
//        public ResponseEntity<SsoConfiguration> getActiveSso(
//                @Parameter(hidden = true) HttpServletRequest request,
//                @Parameter(name = "tenantId", description = "Tenant id", required = true) @RequestParam String tenantId) {
//            log.debug("getActiveSso - start: tenantId={}", tenantId);
//            try {
//                return tenantService.getActiveSsoConfiguration(tenantId)
//                        .map(cfg -> {
//                            log.debug("getActiveSso - found active configuration for tenantId={}", tenantId);
//                            return ResponseEntity.ok(cfg);
//                        })
//                        .orElseGet(() -> {
//                            log.debug("getActiveSso - no active configuration found for tenantId={}", tenantId);
//                            return ResponseEntity.noContent().build();
//                        });
//            } catch (Exception e) {
//                log.error("getActiveSso - failed: tenantId={}, error={}", tenantId, e.getMessage(), e);
//                return ResponseEntity.status(500).build();
//            }
//        }
//
//    /**
//     * Create an extension OAuth/OpenID client for a tenant.
//     *
//     * @param request     HTTP servlet request (for auth/context)
//     * @param redirectUrl Redirect URL for the created client (required)
//     * @param tenantId    Tenant identifier for which the client will be created (required)
//     * @return 200 OK with message on success, 400 Bad Request for client input errors, 500 on server errors
//     */
//    @PostMapping("/clients")
//    @Operation(
//            summary = "Create extension client for tenant",
//            description = "Creates an extension OAuth/OpenID client for the specified tenant.",
//            responses = {
//                    @ApiResponse(responseCode = "200", description = "Client created"),
//                    @ApiResponse(responseCode = "400", description = "Invalid request"),
//                    @ApiResponse(responseCode = "500", description = "Internal server error")
//            }
//    )
//    public ResponseEntity<String> createClientForTenant(
//            @Parameter(hidden = true) HttpServletRequest request,
//            @Parameter(name = "tenantId", description = "Tenant id to create the client for", required = true) @RequestParam String tenantId,
//            @Parameter(name = "redirectUrl", description = "Redirect URL for the new client", required = true) @RequestParam String redirectUrl) {
//
//        log.info("createClientForTenant - start: tenantId={}, redirectUrl={}", tenantId, redirectUrl);
//        try {
//            String message = tenantService.createExtensionClient(tenantId, redirectUrl, request);
//            log.info("createClientForTenant - completed: tenantId={}, message={}", tenantId, message);
//            return ResponseEntity.ok(message);
//        } catch (IllegalArgumentException iae) {
//            log.warn("createClientForTenant - bad request: tenantId={}, error={}", tenantId, iae.getMessage(), iae);
//            return ResponseEntity.badRequest().body(iae.getMessage());
//        } catch (Exception e) {
//            log.error("createClientForTenant - failed: tenantId={}, error={}", tenantId, e.getMessage(), e);
//            return ResponseEntity.status(500).body("Failed to create client");
//        }
//    }
//
//}