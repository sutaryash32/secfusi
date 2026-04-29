package com.secufusion.tenant.controller;

import com.secufusion.tenant.entity.ExtensionPolicy;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.service.ExtensionPolicyService;
import com.secufusion.tenant.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Tag(name = "Extension Policy Controller", description = "APIs for managing Extension Policies")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RestController
@RequestMapping("/api/tenants/extension-policy")
public class ExtensionPolicyController {

    private final ExtensionPolicyService extensionPolicyService;
    private final JwtUtl jwtUtl;

    public ExtensionPolicyController(
            ExtensionPolicyService extensionPolicyService,
            JwtUtl jwtUtl
    ) {
        this.extensionPolicyService = extensionPolicyService;
        this.jwtUtl = jwtUtl;
    }

    /* ==========================================================
                              CREATE
       ========================================================== */

    @Operation(summary = "Create Extension Policy",
            description = "Creates a new extension policy for the tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Policy created successfully",
                    content = @Content(schema = @Schema(implementation = ExtensionPolicy.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    public ResponseEntity<ExtensionPolicy> createPolicy(
            HttpServletRequest request,
            @RequestBody ExtensionPolicy dto
    ) {
        log.debug("API request: Create ExtensionPolicy");

        ExtensionPolicy created =
                extensionPolicyService.createPolicy(dto, request);

        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /* ==========================================================
                              GET BY ID
       ========================================================== */

    @Operation(summary = "Get Extension Policy By ID",
            description = "Fetch a specific extension policy using its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Policy retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ExtensionPolicy.class))),
            @ApiResponse(responseCode = "404", description = "Policy not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ExtensionPolicy> getPolicy(
            @Parameter(description = "Policy ID", required = true)
            @PathVariable String id,
            HttpServletRequest request) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        if (tenant == null || tenant.getTenantID() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(extensionPolicyService.getPolicyById(id, tenant.getTenantID()));
    }
    /* ==========================================================
                              GET ALL
       ========================================================== */

    @Operation(summary = "Get All Extension Policies",
            description = "Fetch all extension policies for the current tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Policies retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<List<ExtensionPolicy>> getAllPolicies(HttpServletRequest request) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);

        if (tenant == null || tenant.getTenantID() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(
                extensionPolicyService.getAllPolicies(tenant.getTenantID())
        );
    }

    /* ==========================================================
                              UPDATE
       ========================================================== */

    @Operation(summary = "Update Extension Policy",
            description = "Update an existing extension policy by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Policy updated successfully"),
            @ApiResponse(responseCode = "404", description = "Policy not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ExtensionPolicy> updatePolicy(
            @Parameter(description = "Policy ID", required = true)
            @PathVariable String id,
            @RequestBody ExtensionPolicy dto,
            HttpServletRequest request
    ) {
        ExtensionPolicy updated =
                extensionPolicyService.updatePolicy(id, dto, request);

        return ResponseEntity.ok(updated);
    }

    /* ==========================================================
                              DELETE
       ========================================================== */

    @Operation(summary = "Delete Extension Policy",
            description = "Delete an extension policy by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Policy deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Policy not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(
            @Parameter(description = "Policy ID", required = true)
            @PathVariable String id) {
        extensionPolicyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }


    //implement the same like browserpolicycontroller of method getAllPolicies where we are giving the policy based on group mapped policy

    /* ==========================================================
                        GROUP MAPPED POLICY
       ========================================================== */
    @Operation(summary = "Get Group Mapped Policy",
            description = "Fetch the effective extension policy mapped to the current user based on group mapping")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Policy resolved successfully",
                    content = @Content(schema = @Schema(implementation = ExtensionPolicy.class))),
            @ApiResponse(responseCode = "404", description = "No policy mapped for user")
    })
    @GetMapping("/mapping")
    public ResponseEntity<ExtensionPolicy> getGroupMappedPolicies(HttpServletRequest request) {
        return ResponseEntity.ok( extensionPolicyService.resolvePolicyForCurrentUser(request));
    }
}
