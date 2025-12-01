package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateFeatureRequest;
import com.secufusion.iam.dto.FeatureResponse;
import com.secufusion.iam.service.FeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/features")
@Tag(name = "Feature", description = "Operations related to features")
public class FeatureController {

    @Autowired
    private FeatureService featureService;

    @PostMapping
    @Operation(summary = "Create a feature", description = "Create a new feature")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature created",
                    content = @Content(schema = @Schema(implementation = FeatureResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<FeatureResponse> createFeature(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Feature to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateFeatureRequest.class)))
            @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.ok(featureService.createFeature(request));
    }

    @GetMapping
    @Operation(summary = "Get all features", description = "Retrieve all features")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of features",
                    content = @Content(schema = @Schema(implementation = FeatureResponse.class)))
    })
    public ResponseEntity<List<FeatureResponse>> getAllFeatures() {
        return ResponseEntity.ok(featureService.getAllFeatures());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a feature", description = "Retrieve a feature by its id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature found",
                    content = @Content(schema = @Schema(implementation = FeatureResponse.class))),
            @ApiResponse(responseCode = "404", description = "Feature not found")
    })
    public ResponseEntity<FeatureResponse> getFeature(
            @Parameter(description = "ID of the feature", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(featureService.getFeature(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a feature", description = "Update an existing feature by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature updated",
                    content = @Content(schema = @Schema(implementation = FeatureResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Feature not found")
    })
    public ResponseEntity<FeatureResponse> updateFeature(
            @Parameter(description = "ID of the feature", required = true) @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated feature data", required = true,
                    content = @Content(schema = @Schema(implementation = CreateFeatureRequest.class)))
            @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.ok(featureService.updateFeature(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a feature", description = "Delete a feature by its id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature deleted"),
            @ApiResponse(responseCode = "404", description = "Feature not found")
    })
    public ResponseEntity<String> deleteFeature(
            @Parameter(description = "ID of the feature", required = true) @PathVariable Long id) {
        featureService.deleteFeature(id);
        return ResponseEntity.ok("Feature deleted successfully.");
    }
}