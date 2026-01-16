package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateFeatureGroupRequest;
import com.secufusion.iam.dto.FeatureGroupResponse;
import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.service.FeatureGroupService;
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
@RequestMapping("/feature-groups")
@Tag(name = "Feature Groups", description = "Manage feature groups for the package-feature matrix")
public class FeatureGroupController {

    @Autowired
    private FeatureGroupService featureGroupService;

    @PostMapping
    @Operation(summary = "Create a feature group", description = "Create a new feature group for organizing features")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Feature group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Feature group with same code or name already exists")
    })
    public ResponseEntity<ResponseDto<FeatureGroupResponse>> create(
            @Valid @RequestBody CreateFeatureGroupRequest request) {
        FeatureGroupResponse response = featureGroupService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseDto<>(response, "201"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a feature group", description = "Update an existing feature group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature group updated successfully"),
            @ApiResponse(responseCode = "404", description = "Feature group not found"),
            @ApiResponse(responseCode = "409", description = "Feature group with same code or name already exists")
    })
    public ResponseEntity<ResponseDto<FeatureGroupResponse>> update(
            @Parameter(description = "Feature group ID") @PathVariable Long id,
            @Valid @RequestBody CreateFeatureGroupRequest request) {
        FeatureGroupResponse response = featureGroupService.update(id, request);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a feature group by ID", description = "Retrieve a feature group with its features")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature group found"),
            @ApiResponse(responseCode = "404", description = "Feature group not found")
    })
    public ResponseEntity<ResponseDto<FeatureGroupResponse>> getById(
            @Parameter(description = "Feature group ID") @PathVariable Long id) {
        FeatureGroupResponse response = featureGroupService.getById(id);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get a feature group by code", description = "Retrieve a feature group by its unique code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature group found"),
            @ApiResponse(responseCode = "404", description = "Feature group not found")
    })
    public ResponseEntity<ResponseDto<FeatureGroupResponse>> getByCode(
            @Parameter(description = "Feature group code") @PathVariable String code) {
        FeatureGroupResponse response = featureGroupService.getByCode(code);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping
    @Operation(summary = "Get all feature groups", description = "Retrieve all feature groups ordered by display order")
    @ApiResponse(responseCode = "200", description = "Feature groups retrieved successfully")
    public ResponseEntity<ResponseDto<List<FeatureGroupResponse>>> getAll() {
        List<FeatureGroupResponse> response = featureGroupService.getAll();
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active feature groups", description = "Retrieve only active feature groups")
    @ApiResponse(responseCode = "200", description = "Active feature groups retrieved successfully")
    public ResponseEntity<ResponseDto<List<FeatureGroupResponse>>> getActiveGroups() {
        List<FeatureGroupResponse> response = featureGroupService.getActiveGroups();
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a feature group", description = "Delete a feature group by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature group deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Feature group not found")
    })
    public ResponseEntity<ResponseDto<String>> delete(
            @Parameter(description = "Feature group ID") @PathVariable Long id) {
        featureGroupService.delete(id);
        return ResponseEntity.ok(new ResponseDto<>("Feature group deleted successfully", "200"));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a feature group", description = "Set feature group active status to true")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature group activated"),
            @ApiResponse(responseCode = "404", description = "Feature group not found")
    })
    public ResponseEntity<ResponseDto<FeatureGroupResponse>> activate(
            @Parameter(description = "Feature group ID") @PathVariable Long id) {
        FeatureGroupResponse response = featureGroupService.toggleActive(id, true);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a feature group", description = "Set feature group active status to false")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature group deactivated"),
            @ApiResponse(responseCode = "404", description = "Feature group not found")
    })
    public ResponseEntity<ResponseDto<FeatureGroupResponse>> deactivate(
            @Parameter(description = "Feature group ID") @PathVariable Long id) {
        FeatureGroupResponse response = featureGroupService.toggleActive(id, false);
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }
}
