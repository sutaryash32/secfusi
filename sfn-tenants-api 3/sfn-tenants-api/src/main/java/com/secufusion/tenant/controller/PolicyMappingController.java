package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.PolicyAssignment;
import com.secufusion.tenant.service.PolicyMappingService;
import com.secufusion.tenant.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/policy/mapping")
@RequiredArgsConstructor
@Tag(name = "Policy Assignment API", description = "Manage mapping of Browser/Network policies to Azure Groups/Roles")
public class PolicyMappingController {

    private final PolicyMappingService mappingService;
    private final JwtUtl jwtUtl;

    @Operation(summary = "Bulk Assign Policy", description = "Replaces ALL existing assignments for a specific policy with a new list of Azure resources.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Policy mapped successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid policy type provided (must be 'browser' or 'network' or 'extension')"),
            @ApiResponse(responseCode = "500", description = "Database error during mapping")
    })
    @PostMapping("/{policyType}")
    public ResponseEntity<List<PolicyAssignment>> assignPolicy(
            HttpServletRequest request,
            @Parameter(description = "Type of policy: 'browser' or 'network' or 'extension'", required = true, example = "browser")
            @PathVariable String policyType,
            @RequestBody PolicyMappingRequest mappingRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        List<PolicyAssignment> result = null;

        if ("browser".equalsIgnoreCase(policyType)) {
            result = mappingService.mapPolicyToAzureResources(tenantId, mappingRequest);
        } else if ("network".equalsIgnoreCase(policyType)) {
            result = mappingService.mapNetworkPolicyToAzureResources(tenantId, mappingRequest);
        } else if ("extension".equalsIgnoreCase(policyType)) {
            result = mappingService.mapExtensionPolicyToAzureResources(tenantId, mappingRequest);
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Add Single Assignment", description = "Appends a SINGLE Azure resource assignment to a policy without deleting existing ones.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assignment added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate assignment"),
            @ApiResponse(responseCode = "404", description = "Policy not found")
    })
    @PostMapping("/{policyType}/{policyId}")
    public ResponseEntity<PolicyAssignment> addSingleAssignment(
            HttpServletRequest request,
            @Parameter(description = "Type of policy: 'browser' or 'network' or 'extension'", required = true, example = "browser")
            @PathVariable String policyType,
            @Parameter(description = "UUID of the policy", required = true)
            @PathVariable String policyId,
            @RequestBody AzureAssignmentDto dto) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        PolicyAssignment created = mappingService.addSingleAssignment(tenantId, policyType, policyId, dto);

        if (created == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(created);
    }

    @Operation(summary = "Update Assignment", description = "Update the target Azure resource (Role/Group) for an existing assignment ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assignment updated successfully"),
            @ApiResponse(responseCode = "404", description = "Assignment ID not found for this tenant")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PolicyAssignment> updateAssignment(
            HttpServletRequest request,
            @Parameter(description = "UUID of the assignment row", required = true)
            @PathVariable String id,
            @RequestBody AzureAssignmentDto dto) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        PolicyAssignment updated = mappingService.updateSingleAssignment(tenantId, id, dto);

        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get All Mappings", description = "Retrieve all policy assignments for the authenticated tenant.")
    @ApiResponse(responseCode = "200", description = "List of assignments retrieved")
    @GetMapping
    public ResponseEntity<List<PolicyAssignmentResponseDto>> getAllMappings(HttpServletRequest request) {
        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        return ResponseEntity.ok(mappingService.getAllMappingsForTenant(tenantId));
    }

    @Operation(summary = "Get Assignment by ID", description = "Retrieve a specific assignment by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assignment found"),
            @ApiResponse(responseCode = "404", description = "Assignment not found or access denied")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PolicyAssignmentResponseDto> getMappingById(
            HttpServletRequest request,
            @Parameter(description = "UUID of the assignment", required = true)
            @PathVariable String id) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        return ResponseEntity.ok(mappingService.getMappingById(id, tenantId));
    }

    @Operation(summary = "Delete Assignment", description = "Remove a single policy assignment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Assignment deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Assignment not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(
            HttpServletRequest request,
            @Parameter(description = "UUID of the assignment to delete", required = true)
            @PathVariable String id) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        boolean deleted = mappingService.deleteAssignment(tenantId, id);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    // =================================================================================
    // GROUP POLICY MAPPING (MULTI-POLICY, MULTI-GROUP via EventsGroup)
    // =================================================================================

    @Operation(summary = "Assign Policies to Groups",
            description = "Assign one or more policies (Browser/Network/Extension) to multiple EventsGroups (APIKEY_GROUP or AZURE_GROUP). " +
                    "Replaces ALL existing assignments for each policy in the request.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Policies assigned to groups successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (bad assignmentType, missing groups, invalid policyType)"),
            @ApiResponse(responseCode = "404", description = "Policy or group not found")
    })
    @PostMapping("/groups")
    public ResponseEntity<GroupPolicyMappingResponse> assignPoliciesToGroups(
            HttpServletRequest request,
            @RequestBody GroupPolicyMappingRequest mappingRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        GroupPolicyMappingResponse response = mappingService.assignPoliciesToGroups(tenantId, mappingRequest);

        return ResponseEntity.ok(response);
    }
}