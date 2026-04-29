package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateApiRequest;
import com.secufusion.iam.dto.ToggleRequest;
import com.secufusion.iam.entity.ApiFlagEntity;
import com.secufusion.iam.entity.TenantApiMappingEntity;
import com.secufusion.iam.repository.ApiFlagRepository;
import com.secufusion.iam.repository.TenantApiMappingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Administrative controller for managing API feature flags and tenant mappings.
 * Provides endpoints to create API definitions and enable/disable APIs per tenant.
 */
@Slf4j
@RestController
@RequestMapping("/admin/api-flags")
@Tag(name = "API Flag Admin", description = "Manage API feature flags and tenant mappings")
public class ApiFlagAdminController {

    @Autowired
    private ApiFlagRepository apiFlagRepo;
    @Autowired
    private TenantApiMappingRepository tenantMappingRepo;
    /**
     * Create a new API definition used for feature flagging.
     *
     * @param request request payload containing apiKey, path and optional description
     * @return saved ApiFlagEntity
     */
    @Operation(summary = "Create API definition", description = "Create a new API flag definition used by the feature flag system.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "API created",
                    content = @Content(schema = @Schema(implementation = ApiFlagEntity.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PostMapping("/apis")
    public ResponseEntity<?> createApi(@RequestBody CreateApiRequest request) {
        log.info("Received request to create API with key: {}", request.getApiKey());
        // Build entity from request DTO
        ApiFlagEntity api = new ApiFlagEntity();
        api.setApiKey(request.getApiKey());
        api.setPath(request.getPath());
        api.setDescription(request.getDescription());

        // Persist and refresh runtime cache
        ApiFlagEntity save = apiFlagRepo.save(api);
        log.debug("API saved with id: {} and key: {}", save.getId(), save.getApiKey());
        return ResponseEntity.ok(save);
    }

    /**
     * Enable or disable an API for a specific tenant.
     *
     * @param tenantId tenant identifier
     * @param key      api key to toggle
     * @param request  toggle request containing enabled flag
     * @return saved TenantApiMappingEntity or 404 if API not found
     */
    @Operation(summary = "Enable/Disable API for tenant", description = "Toggle API availability for a tenant.")
    @Parameters({
            @Parameter(name = "tenantId", description = "Tenant identifier", required = true),
            @Parameter(name = "key", description = "API key to toggle", required = true)
    })
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mapping updated",
                    content = @Content(schema = @Schema(implementation = TenantApiMappingEntity.class))),
            @ApiResponse(responseCode = "404", description = "API not found", content = @Content)
    })
    @PutMapping("/tenants")
    public ResponseEntity<?> toggleApiForTenant(@RequestParam String tenantId,
                                                @RequestParam String key,
                                                @RequestBody ToggleRequest request) {
        log.info("Toggle request for tenantId: {}, apiKey: {}, enabled: {}", tenantId, key, request.isEnabled());

        // Resolve API by key
        Optional<ApiFlagEntity> byApiKey = apiFlagRepo.findByApiKey(key);
        if (byApiKey.isEmpty()) {
            log.warn("API with key '{}' not found", key);
            return ResponseEntity.notFound().build();
        }

        ApiFlagEntity api = byApiKey.get();

        // Find existing mapping or create a new one
        Optional<TenantApiMappingEntity> mappingOpt =
                tenantMappingRepo.findByApiIdAndTenantId(api.getId(), tenantId);

        TenantApiMappingEntity mapping = mappingOpt.orElse(new TenantApiMappingEntity());
        mapping.setApi(api);
        mapping.setTenantId(tenantId);
        mapping.setEnabled(request.isEnabled());
        mapping.setUpdatedAt(Instant.now());

        TenantApiMappingEntity save = tenantMappingRepo.save(mapping);
        log.debug("Saved tenant-api mapping id: {}, tenantId: {}, apiId: {}, enabled: {}",
                save.getId(), save.getTenantId(), api.getId(), save.isEnabled());

        return ResponseEntity.ok(save);
    }

    /**
     * List all tenant-API mappings.
     *
     * @return list of TenantApiMappingEntity
     */
    @Operation(summary = "List mappings", description = "Return all tenant to API mappings.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of mappings",
                    content = @Content(schema = @Schema(implementation = TenantApiMappingEntity.class)))
    })
    @GetMapping("/mappings")
    public ResponseEntity<List<TenantApiMappingEntity>> getAllMappings() {
        log.info("Fetching all tenant-api mappings");
        List<TenantApiMappingEntity> mappings = tenantMappingRepo.findAll();
        log.debug("Found {} mappings", mappings.size());
        return ResponseEntity.ok(mappings);
    }
}