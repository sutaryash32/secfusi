package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.dto.TenantResponse;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.util.List;
import java.util.Map;

/**
 * REST controller for tenant management.
 * Provides endpoints to create, read, update, delete and check tenants.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/tenants")
@Slf4j
@Tag(name = "Tenants", description = "APIs for tenant management")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    /**
     * Create a new tenant.
     *
     * @param request HTTP servlet request (for auth/context)
     * @param req     validated create tenant request body
     * @return the created TenantResponse
     */
    @PostMapping
    @Operation(
            summary = "Create tenant",
            description = "Create a new tenant.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tenant created",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TenantResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request")
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
        log.info("createTenant - start: tenantName={}", req.getTenantName());
        TenantResponse resp = tenantService.createTenant(request, req);
        log.info("createTenant - completed: tenantId={}", resp != null ? resp.getTenantID() : "null");
        return ResponseEntity.ok(resp);
    }

    /**
     * Get a tenant by id if the caller is parent.
     *
     * @param request HTTP servlet request (for auth/context)
     * @param id      tenant id to fetch
     * @return the TenantResponse
     */
    @GetMapping("/id")
    @Operation(
            summary = "Get tenant by id",
            description = "Get a tenant by id if the caller is parent.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tenant found",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TenantResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<TenantResponse> getTenant(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(name = "id", description = "Tenant id to fetch", required = true) @RequestParam String id) {
        log.debug("getTenant - start: id={}", id);
        TenantResponse response = tenantService.getTenantIfParent(request, id);
        log.debug("getTenant - completed: id={}, found={}", id, response != null);
        return ResponseEntity.ok(response);
    }

    /**
     * Get full tenant hierarchy visible to caller.
     *
     * @param request HTTP servlet request (for auth/context)
     * @return list of tenant responses representing the hierarchy
     */
    @GetMapping
    @Operation(
            summary = "Get tenant hierarchy",
            description = "Get full tenant hierarchy visible to caller.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Hierarchy returned",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = TenantResponse.class))))
            }
    )
    public ResponseEntity<List<TenantResponse>> getAll(@Parameter(hidden = true) HttpServletRequest request) {
        log.debug("getAll - fetching tenant hierarchy");
        List<TenantResponse> list = tenantService.getTenantHierarchy(request);
        log.debug("getAll - result count={}", list != null ? list.size() : 0);
        return ResponseEntity.ok(list);
    }

    /**
     * Update an existing tenant.
     *
     * @param request HTTP servlet request (for auth/context)
     * @param id      id of tenant to update
     * @param req     validated update payload (reuses CreateTenantRequest)
     * @return updated TenantResponse
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "Update tenant",
            description = "Update an existing tenant.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tenant updated",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TenantResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<TenantResponse> updateTenant(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(name = "id", description = "Tenant id to update", required = true) @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Tenant update payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateTenantRequest.class))
            )
            @Valid @RequestBody CreateTenantRequest req) {
        log.info("updateTenant - start: id={}, tenantName={}", id, req.getTenantName());
        TenantResponse updated = tenantService.updateTenant(request, id, req);
        log.info("updateTenant - completed: id={}, updated={}", id, updated != null);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a tenant by id.
     *
     * @param id id of tenant to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete tenant",
            description = "Delete a tenant by id.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Tenant deleted"),
                    @ApiResponse(responseCode = "404", description = "Tenant not found")
            }
    )
    public ResponseEntity<Void> deleteTenant(
            @Parameter(name = "id", description = "Tenant id to delete", required = true) @PathVariable String id) {
        log.info("deleteTenant - start: id={}", id);
        tenantService.deleteTenant(id);
        log.info("deleteTenant - completed: id={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Return tenant types available to the caller.
     *
     * @param request HTTP servlet request (for auth/context)
     * @return list of TenantType enums
     */
    @GetMapping("/types")
    @Operation(
            summary = "Get tenant types",
            description = "Return tenant types available to the caller.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tenant types returned",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = TenantType.class))))
            }
    )
    public ResponseEntity<List<TenantType>> getAllTenantTypes(@Parameter(hidden = true) HttpServletRequest request) {
        log.debug("getAllTenantTypes - start");
        List<TenantType> types = tenantService.getTenantTypesByTenantType(request);
        log.debug("getAllTenantTypes - completed: count={}", types != null ? types.size() : 0);
        return ResponseEntity.ok(types);
    }

    /**
     * Return billing types metadata for tenants.
     *
     * @return list of maps describing billing types
     */
    @GetMapping("/billing")
    @Operation(
            summary = "Get billing types",
            description = "Return billing types metadata for tenants.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Billing types returned",
                            content = @Content(mediaType = "application/json"))
            }
    )
    public ResponseEntity<List<Map<String, Object>>> getBillingTypes() {
        log.debug("getBillingTypes - fetching billing types");
        List<Map<String, Object>> billing = tenantService.getTenantBillingTypes();
        log.debug("getBillingTypes - completed: count={}", billing != null ? billing.size() : 0);
        return ResponseEntity.ok(billing);
    }

    /**
     * Check availability / existence for tenant fields.
     * Supports one parameter at a time: tenantName, domainName, phoneNumber, tenantEmail.
     *
     * @param tenantName  optional tenant name to check availability
     * @param domainName  optional domain name to check existence
     * @param phoneNumber optional phone number to check
     * @param tenantEmail optional tenant email to check
     * @return string result describing availability/existence or bad request if none provided
     */
    @GetMapping("/check")
    @Operation(
            summary = "Check tenant fields",
            description = "Check availability / existence for tenant fields. Provide one parameter at a time.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Check result returned",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "400", description = "No parameters provided")
            }
    )
    public ResponseEntity<String> checkTenant(
            @Parameter(name = "tenantName", description = "Tenant name to check", required = false) @RequestParam(required = false) String tenantName,
            @Parameter(name = "domainName", description = "Domain name to check", required = false) @RequestParam(required = false) String domainName,
            @Parameter(name = "phoneNumber", description = "Phone number to check", required = false) @RequestParam(required = false) String phoneNumber,
            @Parameter(name = "tenantEmail", description = "Tenant email to check", required = false) @RequestParam(required = false) String tenantEmail) {

        log.debug("checkTenant - called with tenantName={}, domainName={}, phoneNumber={}, tenantEmail={}",
                tenantName, domainName, phoneNumber, tenantEmail);

        if (tenantName != null) {
            String result = tenantService.checkTenantNameAvailability(tenantName);
            log.info("checkTenant - tenantName check result={}", result);
            return ResponseEntity.ok(result);
        }

        if (domainName != null) {
            String result = tenantService.checkExistsByDomain(domainName);
            log.info("checkTenant - domainName check result={}", result);
            return ResponseEntity.ok(result);
        }

        if (phoneNumber != null) {
            String result = tenantService.checkPhoneNumber(phoneNumber);
            log.info("checkTenant - phoneNumber check result={}", result);
            return ResponseEntity.ok(result);
        }

        if (tenantEmail != null) {
            String result = tenantService.checkEmail(tenantEmail);
            log.info("checkTenant - tenantEmail check result={}", result);
            return ResponseEntity.ok(result);
        }

        log.warn("checkTenant - no parameters provided");
        return ResponseEntity.badRequest().body("");
    }

}