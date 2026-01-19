package com.secufusion.iam.controller;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.service.TenantAddonFeatureService;
import com.secufusion.iam.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tenant-addon-features")
@RequiredArgsConstructor
@Tag(name = "Tenant Add-on Features", description = "APIs for managing tenant add-on features purchased beyond base package")
public class TenantAddonFeatureController {

    private final TenantAddonFeatureService tenantAddonFeatureService;
    private final JwtUtl jwtUtl;

    @PostMapping
    @Operation(summary = "Create addon feature", description = "Add a new addon feature to a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Addon feature created successfully",
                    content = @Content(schema = @Schema(implementation = TenantAddonFeatureResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Tenant or feature not found"),
            @ApiResponse(responseCode = "409", description = "Addon already exists")
    })
    public ResponseEntity<ResponseDto<TenantAddonFeatureResponse>> createAddonFeature(
            @RequestBody CreateTenantAddonFeatureRequest request,
            HttpServletRequest httpRequest) {
        String userId = jwtUtl.getUserId(httpRequest);
        log.info("POST /tenant-addon-features - Creating addon for tenant: {}", request.getTenantId());
        TenantAddonFeatureResponse response = tenantAddonFeatureService.createAddonFeature(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseDto<>(response, String.valueOf(HttpStatus.CREATED.value())));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Create bulk addon features", description = "Add multiple addon features to a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Addon features created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Tenant or feature not found")
    })
    public ResponseEntity<ResponseDto<List<TenantAddonFeatureResponse>>> createBulkAddonFeatures(
            @RequestBody BulkTenantAddonFeatureRequest request,
            HttpServletRequest httpRequest) {
        String userId = jwtUtl.getUserId(httpRequest);
        log.info("POST /tenant-addon-features/bulk - Creating bulk addons for tenant: {}", request.getTenantId());
        List<TenantAddonFeatureResponse> responses = tenantAddonFeatureService.createBulkAddonFeatures(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseDto<>(responses, String.valueOf(HttpStatus.CREATED.value())));
    }

    @GetMapping("/{addonId}")
    @Operation(summary = "Get addon by ID", description = "Retrieve a specific addon feature by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Addon feature found",
                    content = @Content(schema = @Schema(implementation = TenantAddonFeatureResponse.class))),
            @ApiResponse(responseCode = "404", description = "Addon not found")
    })
    public ResponseEntity<ResponseDto<TenantAddonFeatureResponse>> getAddonById(
            @Parameter(description = "Addon ID") @PathVariable Long addonId) {
        log.debug("GET /tenant-addon-features/{} - Fetching addon", addonId);
        TenantAddonFeatureResponse response = tenantAddonFeatureService.getAddonById(addonId);
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Get all addons for tenant", description = "Retrieve all addon features for a specific tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of addon features",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TenantAddonFeatureResponse.class))))
    })
    public ResponseEntity<ResponseDto<List<TenantAddonFeatureResponse>>> getAddonsByTenant(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        log.debug("GET /tenant-addon-features/tenant/{} - Fetching all addons", tenantId);
        List<TenantAddonFeatureResponse> responses = tenantAddonFeatureService.getAddonsByTenant(tenantId);
        return ResponseEntity.ok(new ResponseDto<>(responses, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/tenant/{tenantId}/active")
    @Operation(summary = "Get active addons for tenant", description = "Retrieve only active (non-expired, enabled) addon features for a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of active addon features")
    })
    public ResponseEntity<ResponseDto<List<TenantAddonFeatureResponse>>> getActiveAddonsByTenant(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        log.debug("GET /tenant-addon-features/tenant/{}/active - Fetching active addons", tenantId);
        List<TenantAddonFeatureResponse> responses = tenantAddonFeatureService.getActiveAddonsByTenant(tenantId);
        return ResponseEntity.ok(new ResponseDto<>(responses, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/tenant/{tenantId}/feature/{featureCode}")
    @Operation(summary = "Get addon by tenant and feature code", description = "Check if a tenant has a specific addon feature")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Addon feature found or not found")
    })
    public ResponseEntity<ResponseDto<TenantAddonFeatureResponse>> getAddonByTenantAndFeature(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Feature code") @PathVariable String featureCode) {
        log.debug("GET /tenant-addon-features/tenant/{}/feature/{} - Checking addon", tenantId, featureCode);
        return tenantAddonFeatureService.getAddonByTenantAndFeatureCode(tenantId, featureCode)
                .map(response -> ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value()))))
                .orElse(ResponseEntity.ok(new ResponseDto<>(null, String.valueOf(HttpStatus.OK.value()))));
    }

    @GetMapping("/tenant/{tenantId}/has-addon/{featureCode}")
    @Operation(summary = "Check if tenant has addon", description = "Quick check if tenant has an active addon for a feature")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Returns true/false")
    })
    public ResponseEntity<ResponseDto<Boolean>> hasActiveAddon(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Feature code") @PathVariable String featureCode) {
        log.debug("GET /tenant-addon-features/tenant/{}/has-addon/{} - Checking", tenantId, featureCode);
        boolean hasAddon = tenantAddonFeatureService.hasActiveAddon(tenantId, featureCode);
        return ResponseEntity.ok(new ResponseDto<>(hasAddon, String.valueOf(HttpStatus.OK.value())));
    }

    @PutMapping("/{addonId}")
    @Operation(summary = "Update addon feature", description = "Update an existing addon feature")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Addon feature updated successfully"),
            @ApiResponse(responseCode = "404", description = "Addon not found")
    })
    public ResponseEntity<ResponseDto<TenantAddonFeatureResponse>> updateAddonFeature(
            @Parameter(description = "Addon ID") @PathVariable Long addonId,
            @RequestBody CreateTenantAddonFeatureRequest request,
            HttpServletRequest httpRequest) {
        String userId = jwtUtl.getUserId(httpRequest);
        log.info("PUT /tenant-addon-features/{} - Updating addon", addonId);
        TenantAddonFeatureResponse response = tenantAddonFeatureService.updateAddonFeature(addonId, request, userId);
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @PatchMapping("/{addonId}/toggle")
    @Operation(summary = "Toggle addon enabled/disabled", description = "Enable or disable an addon feature")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Addon feature toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Addon not found")
    })
    public ResponseEntity<ResponseDto<TenantAddonFeatureResponse>> toggleAddonFeature(
            @Parameter(description = "Addon ID") @PathVariable Long addonId,
            @Parameter(description = "Enable or disable") @RequestParam boolean enabled,
            HttpServletRequest httpRequest) {
        String userId = jwtUtl.getUserId(httpRequest);
        log.info("PATCH /tenant-addon-features/{}/toggle?enabled={} - Toggling addon", addonId, enabled);
        TenantAddonFeatureResponse response = tenantAddonFeatureService.toggleAddonFeature(addonId, enabled, userId);
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @DeleteMapping("/{addonId}")
    @Operation(summary = "Delete addon feature", description = "Remove an addon feature from a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Addon feature deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Addon not found")
    })
    public ResponseEntity<Void> deleteAddonFeature(
            @Parameter(description = "Addon ID") @PathVariable Long addonId) {
        log.info("DELETE /tenant-addon-features/{} - Deleting addon", addonId);
        tenantAddonFeatureService.deleteAddonFeature(addonId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tenant/{tenantId}/feature/{featureId}")
    @Operation(summary = "Delete addon by tenant and feature", description = "Remove a specific addon feature from a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Addon feature deleted successfully")
    })
    public ResponseEntity<Void> deleteAddonByTenantAndFeature(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Feature ID") @PathVariable Long featureId) {
        log.info("DELETE /tenant-addon-features/tenant/{}/feature/{} - Deleting addon", tenantId, featureId);
        tenantAddonFeatureService.deleteAddonByTenantAndFeature(tenantId, featureId);
        return ResponseEntity.noContent().build();
    }
}
