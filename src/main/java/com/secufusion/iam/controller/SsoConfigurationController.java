package com.secufusion.iam.controller;

import com.secufusion.iam.dto.AzureResourceDto;
import com.secufusion.iam.dto.CreateIdentityProviderRequest;
import com.secufusion.iam.dto.SsoConfigurationResponse;
import com.secufusion.iam.entity.SsoConfiguration;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.service.AzureGraphService;
import com.secufusion.iam.service.SsoConfigurationService;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import java.net.URI;
import java.util.List;

/**
 * REST controller that manages SSO configurations (identity providers) for a tenant.
 *
 * <p>Exposes CRUD operations and activation for SSO provider configurations.</p>
 *
 * Note: methods include logging to help trace requests and simple OpenAPI/Swagger
 * annotations for generated API documentation.
 */
@RestController
@RequestMapping("/sso-configurations")
@Tag(name = "SSO Configurations", description = "Manage identity provider configurations for tenants")
@RequiredArgsConstructor
@Slf4j
public class SsoConfigurationController {

    // Use constructor injection through Lombok's @RequiredArgsConstructor
    private final SsoConfigurationService ssoConfigurationService;
    private final AzureGraphService azureGraphService;
    private final JwtUtl jwtUtl;

    // CREATE
    /**
     * Create a new identity provider configuration for the current tenant.
     *
     * @param request servlet request (used to determine tenant/context)
     * @param dto     creation request payload
     * @return created SSO configuration
     */
    @PostMapping
    @Operation(summary = "Create SSO configuration", description = "Adds a new identity provider configuration to the tenant")
    public ResponseEntity<SsoConfigurationResponse> create(
            HttpServletRequest request,
            @RequestBody CreateIdentityProviderRequest dto) {

        // Log entry with minimal contextual info (avoid logging secrets)
        log.info("Received request to create SSO configuration for tenant ip={} payload={}", request.getRemoteAddr(), dto);

        SsoConfigurationResponse response = ssoConfigurationService.addProviderToTenant(request, dto);

        // Log success and return 201 Created with location header
        log.info("Created SSO configuration id={} for tenant ip={}", response != null ? response.getId() : "null", request.getRemoteAddr());
        URI location = (response != null && response.getId() != null)
                ? URI.create(String.format("/sso-configurations/%s", response.getId()))
                : URI.create("/sso-configurations");

        return ResponseEntity.created(location).body(response);
    }

    // READ - All
    /**
     * Retrieve all SSO configurations for the current tenant.
     *
     * @param request servlet request (used to determine tenant/context)
     * @return list of SSO configurations
     */
    @GetMapping
    @Operation(summary = "List SSO configurations", description = "Returns all identity provider configurations for the tenant")
    public ResponseEntity<List<SsoConfigurationResponse>> getAll(HttpServletRequest request) {
        log.info("Listing all SSO configurations for tenant ip={}", request.getRemoteAddr());
        List<SsoConfigurationResponse> result = ssoConfigurationService.getAll(request);
        log.debug("Found {} SSO configurations for tenant ip={}", result != null ? result.size() : 0, request.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    // READ - By ID
    /**
     * Retrieve a single SSO configuration by id.
     *
     * @param request servlet request (used to determine tenant/context)
     * @param id      configuration id
     * @return single SSO configuration
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get SSO configuration by id", description = "Returns the identity provider configuration for the given id")
    public ResponseEntity<SsoConfiguration> getById(
            HttpServletRequest request,
            @Parameter(description = "SSO configuration id") @PathVariable String id) {

        log.info("Fetching SSO configuration id={} for tenant ip={}", id, request.getRemoteAddr());
        SsoConfiguration response = ssoConfigurationService.getById(request, id);
        log.debug("Fetched SSO configuration id={} -> {}", id, response);
        return ResponseEntity.ok(response);
    }

    // UPDATE
    /**
     * Update an existing SSO configuration.
     *
     * @param request servlet request (used to determine tenant/context)
     * @param id      configuration id to update
     * @param dto     update payload
     * @return HTTP 200 on success
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update SSO configuration", description = "Updates an existing identity provider configuration")
    public ResponseEntity<Void> update(
            HttpServletRequest request,
            @Parameter(description = "SSO configuration id") @PathVariable String id,
            @RequestBody CreateIdentityProviderRequest dto) {

        log.info("Updating SSO configuration id={} for tenant ip={}, payload={}", id, request.getRemoteAddr(), dto);
        ssoConfigurationService.update(request, id, dto);
        log.info("Updated SSO configuration id={}", id);
        return ResponseEntity.ok().build();
    }

    // DELETE
    /**
     * Delete an SSO configuration.
     *
     * @param request servlet request (used to determine tenant/context)
     * @param id      configuration id to delete
     * @return HTTP 204 on success
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete SSO configuration", description = "Deletes the identity provider configuration with the given id")
    public ResponseEntity<Void> delete(
            HttpServletRequest request,
            @Parameter(description = "SSO configuration id") @PathVariable String id) {

        log.info("Deleting SSO configuration id={} for tenant ip={}", id, request.getRemoteAddr());
        ssoConfigurationService.delete(request, id);
        log.info("Deleted SSO configuration id={}", id);
        return ResponseEntity.noContent().build();
    }

    // ACTIVATE / DEACTIVATE
    /**
     * Activate an SSO configuration (mark as active).
     *
     * @param request servlet request (used to determine tenant/context)
     * @param id      configuration id to activate
     * @return HTTP 200 on success
     */
    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate SSO configuration", description = "Activates the identity provider configuration with the given id")
    public ResponseEntity<Void> activate(
            HttpServletRequest request,
            @Parameter(description = "SSO configuration id") @PathVariable String id) {

        log.info("Activating SSO configuration id={} for tenant ip={}", id, request.getRemoteAddr());
        ssoConfigurationService.activate(request, id);
        log.info("Activated SSO configuration id={}", id);
        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint to fetch "App Roles" (e.g., Manager, Admin) from Azure.
     * Use this if you want to map permissions based on assigned roles.
     * * URL: GET /api/v1/sso/azure/roles
     */
    @GetMapping("/roles")
    public ResponseEntity<List<AzureResourceDto>> getAvailableAppRoles(HttpServletRequest request) {
        // 1. Identify the Tenant from the JWT token
        Tenant tenant = jwtUtl.getTenantFromRequest(request);

        log.info("Request to fetch Azure App Roles for Tenant ID: {}", tenant.getTenantID());

        // 2. Call the service to get roles for this tenant's configured Azure App
        List<AzureResourceDto> roles = azureGraphService.getApplicationRoles(tenant);

        return ResponseEntity.ok(roles);
    }

    /**
     * Endpoint to search "Security Groups" in the Azure Tenant.
     * Use this if you want to map permissions based on Group IDs.
     * * URL: GET /api/v1/sso/azure/groups?search=HR
     */
    @GetMapping("/groups")
    public ResponseEntity<List<AzureResourceDto>> searchGroups(
            HttpServletRequest request,
            @RequestParam(required = false) String search) {

        // 1. Identify the Tenant from the JWT token
        Tenant tenant = jwtUtl.getTenantFromRequest(request);

        log.info("Request to search Azure Groups for Tenant ID: {} with term: '{}'",
                tenant.getTenantID(), search);

        // 2. Call the service to search groups in this tenant's Azure AD
        List<AzureResourceDto> groups = azureGraphService.searchTenantGroups(tenant, search);

        return ResponseEntity.ok(groups);
    }
}