package com.secufusion.iam.controller;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.service.PackageFeatureMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/package-feature-mappings")
@Tag(name = "Package Feature Mappings", description = "Manage package-feature matrix mappings")
public class PackageFeatureMappingController {

    @Autowired
    private PackageFeatureMappingService mappingService;

    @PostMapping
    @Operation(summary = "Create a package-feature mapping", description = "Create a new mapping between a package and feature with access level")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mapping created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Package, feature, or access level not found"),
            @ApiResponse(responseCode = "409", description = "Mapping already exists for this package and feature")
    })
    public ResponseEntity<ResponseDto<PackageFeatureMappingResponse>> create(
            @Valid @RequestBody CreatePackageFeatureMappingRequest request) {
        PackageFeatureMappingResponse response = mappingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseDto<>(response, "201"));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk create package-feature mappings", description = "Create multiple mappings for a package at once")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mappings created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Package, feature, or access level not found")
    })
    public ResponseEntity<ResponseDto<List<PackageFeatureMappingResponse>>> bulkCreate(
            @Valid @RequestBody BulkPackageFeatureMappingRequest request) {
        List<PackageFeatureMappingResponse> response = mappingService.bulkCreate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseDto<>(response, "201"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a package-feature mapping", description = "Update an existing mapping")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mapping updated successfully"),
            @ApiResponse(responseCode = "404", description = "Mapping not found"),
            @ApiResponse(responseCode = "409", description = "Mapping already exists for this package and feature")
    })
    public ResponseEntity<ResponseDto<PackageFeatureMappingResponse>> update(
            @Parameter(description = "Mapping ID") @PathVariable Long id,
            @Valid @RequestBody CreatePackageFeatureMappingRequest request) {
        PackageFeatureMappingResponse response = mappingService.update(id, request);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a package-feature mapping", description = "Delete a mapping by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mapping deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Mapping not found")
    })
    public ResponseEntity<ResponseDto<String>> delete(
            @Parameter(description = "Mapping ID") @PathVariable Long id) {
        mappingService.delete(id);
        return ResponseEntity.ok(new ResponseDto<>("Mapping deleted successfully", "200"));
    }

    @DeleteMapping("/package/{packageId}")
    @Operation(summary = "Delete all mappings for a package", description = "Delete all feature mappings for a specific package")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mappings deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Package not found")
    })
    public ResponseEntity<ResponseDto<String>> deleteByPackage(
            @Parameter(description = "Package ID") @PathVariable Long packageId) {
        mappingService.deleteByPackage(packageId);
        return ResponseEntity.ok(new ResponseDto<>("All mappings deleted for package", "200"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a mapping by ID", description = "Retrieve a specific mapping")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mapping found"),
            @ApiResponse(responseCode = "404", description = "Mapping not found")
    })
    public ResponseEntity<ResponseDto<PackageFeatureMappingResponse>> getById(
            @Parameter(description = "Mapping ID") @PathVariable Long id) {
        PackageFeatureMappingResponse response = mappingService.getById(id);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/package/{packageId}")
    @Operation(summary = "Get all mappings for a package", description = "Retrieve all feature mappings for a specific package")
    @ApiResponse(responseCode = "200", description = "Mappings retrieved successfully")
    public ResponseEntity<ResponseDto<List<PackageFeatureMappingResponse>>> getByPackage(
            @Parameter(description = "Package ID") @PathVariable Long packageId) {
        List<PackageFeatureMappingResponse> response = mappingService.getMappingsByPackage(packageId);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/matrix/{packageId}")
    @Operation(summary = "Get the feature matrix for a package", description = "Retrieve the complete feature matrix grouped by feature groups")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Matrix retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Package not found")
    })
    public ResponseEntity<ResponseDto<PackageFeatureMatrixResponse>> getPackageMatrix(
            @Parameter(description = "Package ID") @PathVariable Long packageId) {
        PackageFeatureMatrixResponse response = mappingService.getPackageMatrix(packageId);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/matrices")
    @Operation(summary = "Get feature matrices for all packages", description = "Retrieve feature matrices for all packages that have mappings")
    @ApiResponse(responseCode = "200", description = "Matrices retrieved successfully")
    public ResponseEntity<ResponseDto<List<PackageFeatureMatrixResponse>>> getAllMatrices() {
        List<PackageFeatureMatrixResponse> response = mappingService.getAllPackageMatrices();
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/check-access")
    @Operation(summary = "Check feature access for a package", description = "Check if a package has access to a specific feature")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Access check completed")
    })
    public ResponseEntity<ResponseDto<Boolean>> checkAccess(
            @Parameter(description = "Package ID") @RequestParam Long packageId,
            @Parameter(description = "Feature code") @RequestParam String featureCode) {
        boolean hasAccess = mappingService.hasFeatureAccess(packageId, featureCode);
        return ResponseEntity.ok(new ResponseDto<>(hasAccess, "200"));
    }

    @GetMapping("/access-level")
    @Operation(summary = "Get access level for a package feature", description = "Get the access level details for a specific package and feature")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Access level retrieved")
    })
    public ResponseEntity<ResponseDto<AccessLevelResponse>> getAccessLevel(
            @Parameter(description = "Package ID") @RequestParam Long packageId,
            @Parameter(description = "Feature code") @RequestParam String featureCode) {
        AccessLevelResponse response = mappingService.getFeatureAccessLevel(packageId, featureCode);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/retention")
    @Operation(summary = "Get retention period for a package feature", description = "Get the retention period for a specific package and feature")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Retention period retrieved")
    })
    public ResponseEntity<ResponseDto<RetentionPeriodResponse>> getRetention(
            @Parameter(description = "Package ID") @RequestParam Long packageId,
            @Parameter(description = "Feature code") @RequestParam String featureCode) {
        RetentionPeriodResponse response = mappingService.getFeatureRetention(packageId, featureCode);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }
}
