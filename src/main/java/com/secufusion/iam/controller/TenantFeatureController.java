package com.secufusion.iam.controller;

import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.dto.TenantFeatureAccessResponse;
import com.secufusion.iam.dto.UpdateTenantPackageRequest;
import com.secufusion.iam.service.TenantFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/tenant-features")
@Tag(name = "Tenant Features", description = "Query and manage tenant feature access based on subscription package")
public class TenantFeatureController {

    @Autowired
    private TenantFeatureService tenantFeatureService;

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get all feature access for a tenant", description = "Retrieve complete feature access details based on tenant's subscription package")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature access retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<ResponseDto<TenantFeatureAccessResponse>> getTenantFeatures(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        TenantFeatureAccessResponse response = tenantFeatureService.getTenantFeatureAccess(tenantId);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/{tenantId}/check/{featureCode}")
    @Operation(summary = "Check if tenant has access to a specific feature", description = "Returns true if tenant has access (access level > 0 and enabled)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Access check completed"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<ResponseDto<Boolean>> checkFeatureAccess(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Feature code") @PathVariable String featureCode) {
        boolean hasAccess = tenantFeatureService.hasFeatureAccess(tenantId, featureCode);
        return ResponseEntity.ok(new ResponseDto<>(hasAccess, "200"));
    }

    @GetMapping("/{tenantId}/access-level/{featureCode}")
    @Operation(summary = "Get access level for a feature", description = "Returns the access level code (YES, NO, LIMITED, BASIC, ADVANCED, etc.)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Access level retrieved"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<ResponseDto<String>> getAccessLevel(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Feature code") @PathVariable String featureCode) {
        String accessLevel = tenantFeatureService.getFeatureAccessLevelCode(tenantId, featureCode);
        return ResponseEntity.ok(new ResponseDto<>(accessLevel, "200"));
    }

    @GetMapping("/{tenantId}/retention/{featureCode}")
    @Operation(summary = "Get retention period in days for a feature", description = "Returns the number of days for data retention, null means unlimited or not applicable")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Retention days retrieved"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<ResponseDto<Integer>> getRetentionDays(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Feature code") @PathVariable String featureCode) {
        Integer retentionDays = tenantFeatureService.getFeatureRetentionDays(tenantId, featureCode);
        return ResponseEntity.ok(new ResponseDto<>(retentionDays, "200"));
    }

    @PutMapping("/{tenantId}/package")
    @Operation(summary = "Update tenant's subscription package", description = "Assign or change the subscription package for a tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Package updated successfully"),
            @ApiResponse(responseCode = "404", description = "Tenant or package not found")
    })
    public ResponseEntity<ResponseDto<String>> updateTenantPackage(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantPackageRequest request) {
        tenantFeatureService.updateTenantPackage(tenantId, request.getPackageId());
        return ResponseEntity.ok(new ResponseDto<>("Tenant package updated successfully", "200"));
    }
}
