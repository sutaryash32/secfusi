package com.secufusion.events.controller;

import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.entity.ExtensionApiKeyRotationHistory;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.ExtensionApiKeyConfigurationService;
import com.secufusion.events.service.ExtensionApiKeyService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ExtensionApiKeyManagementController
 *
 * REST controller for Extension API Key management (admin/portal).
 * Provides endpoints for key lifecycle management.
 *
 * All endpoints automatically resolve tenant ID and user email from JWT token.
 */
@RestController
@RequestMapping("/api/events/extension-api-keys")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Extension API Key Management",
    description = "Admin/Portal endpoints for managing Extension API keys. " +
                  "Create, update, rotate, revoke, and monitor API keys for browser extension authentication. " +
                  "Tenant ID and user email are automatically extracted from JWT token using HttpServletRequest."
)
public class ExtensionApiKeyManagementController {

    private final ExtensionApiKeyService apiKeyService;
    private final ExtensionApiKeyConfigurationService configService;
    private final JwtUtl jwtUtl;

    /**
     * Helper method to extract tenant from request
     */
    private Tenant getTenantOrThrow(HttpServletRequest request) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        if (tenant == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Unable to resolve tenant from JWT token"
            );
        }
        return tenant;
    }

    /**
     * Helper method to extract user email from request
     */
    private String getUserEmail(HttpServletRequest request) {
        String email = jwtUtl.getEmail(request);
        if (email == null) {
            email = jwtUtl.getPreferredUsernameFromRequest(request);
        }
        return email != null ? email : "SYSTEM";
    }

    @PostMapping
    @Operation(
        summary = "Create new API key",
        description = "Generate a new Extension API key for your tenant. " +
                      "Only requires name and description - all other fields (tenant ID, client credentials, owner email, expiry) are auto-resolved. " +
                      "Returns the raw API key which will NEVER be shown again - store it securely!"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "API key created successfully. Store the rawKey securely - it will not be shown again!",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ExtensionApiKeyCreationResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "key": {
                            "pkExtensionApiKeyId": "key-uuid-123",
                            "tenantId": "tenant-uuid-456",
                            "keyPrefix": "sk_AbCdEfGh",
                            "name": "Production Extension Key",
                            "status": "ACTIVE",
                            "expiresAt": "2027-05-27T00:00:00",
                            "daysRemaining": 90
                          },
                          "rawKey": "sk_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCdEfGhIjKlMnOpQrSt",
                          "warning": "Store this API key securely. It will not be shown again."
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request - check validation errors"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid"),
        @ApiResponse(responseCode = "409", description = "Conflict - maximum active keys limit reached for tenant")
    })
    public ResponseEntity<ExtensionApiKeyCreationResponse> createApiKey(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "API key creation request. Only name and description required.",
            required = true,
            content = @Content(
                examples = @ExampleObject(
                    value = """
                        {
                          "name": "Production Extension Key",
                          "description": "API key for production browser extension"
                        }
                        """
                )
            )
        )
        CreateExtensionApiKeyRequest request,
        HttpServletRequest httpRequest
    ) {
        // Auto-resolve tenant from JWT token via HttpServletRequest
        Tenant tenant = jwtUtl.getTenantFromRequest(httpRequest);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(null); // Could return error message
        }

        // Auto-resolve user email from JWT token
        String userEmail = jwtUtl.getEmail(httpRequest);
        if (userEmail == null) {
            userEmail = jwtUtl.getPreferredUsernameFromRequest(httpRequest);
        }
        if (userEmail == null) {
            userEmail = "SYSTEM";
        }

        log.info("[API-KEY-MGMT] Creating API key: tenant={}, name={}, user={}",
            tenant.getTenantName(), request.getName(), userEmail);

        ExtensionApiKeyCreationResponse response = apiKeyService.createApiKey(
            request, tenant.getTenantID(), userEmail
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
        summary = "List API keys",
        description = "Get paginated list of API keys for your tenant. " +
                      "Tenant ID is automatically extracted from JWT token. " +
                      "Results include computed fields like isExpired, daysRemaining, etc."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API keys retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid")
    })
    public ResponseEntity<Page<ExtensionApiKeyResponse>> listApiKeys(
        @Parameter(description = "Filter by status (ACTIVE, INACTIVE, REVOKED, EXPIRED)")
        @RequestParam(required = false) String status,

        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0") int page,

        @Parameter(description = "Page size", example = "20")
        @RequestParam(defaultValue = "20") int size,

        HttpServletRequest httpRequest
    ) {
        String tenantId = getTenantOrThrow(httpRequest).getTenantID();
        log.info("[API-KEY-MGMT] Listing API keys: tenant={}, status={}, page={}, size={}",
            tenantId, status, page, size);

        Page<ExtensionApiKeyResponse> keys = apiKeyService.listApiKeys(tenantId, status, page, size);
        return ResponseEntity.ok(keys);
    }

    @GetMapping("/id")
    @Operation(
        summary = "Get API key details",
        description = "Retrieve full details of a specific API key. " +
                      "Returns all metadata except the raw key (which is never stored)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API key details retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ExtensionApiKeyResponse> getApiKey(
        @Parameter(description = "API key ID (UUID)", example = "key-uuid-123")
        @RequestParam String keyId
    ) {
        log.info("[API-KEY-MGMT] Getting API key: keyId={}", keyId);
        ExtensionApiKeyResponse response = apiKeyService.getApiKey(keyId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{keyId}")
    @Operation(
        summary = "Update API key",
        description = "Update API key metadata (name, description, expiry, status). " +
                      "All fields are optional - only provided fields will be updated. " +
                      "Cannot update REVOKED keys."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API key updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request - check validation errors"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "409", description = "Cannot update revoked API key")
    })
    public ResponseEntity<ExtensionApiKeyResponse> updateApiKey(
        @Parameter(description = "API key ID to update") @PathVariable String keyId,
        @Valid @RequestBody UpdateExtensionApiKeyRequest request,
        HttpServletRequest httpRequest
    ) {
        String userEmail = getUserEmail(httpRequest);

        log.info("[API-KEY-MGMT] Updating API key: keyId={}, user={}", keyId, userEmail);
        ExtensionApiKeyResponse response = apiKeyService.updateApiKey(keyId, request, userEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{keyId}/revoke")
    @Operation(
        summary = "Revoke API key",
        description = "Permanently revoke an API key. This action CANNOT be undone. " +
                      "Revoked keys will fail all validation attempts. " +
                      "Use this when a key is compromised or no longer needed."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "API key revoked successfully",
            content = @Content(
                examples = @ExampleObject(
                    value = """
                        {
                          "message": "API key revoked successfully",
                          "keyId": "key-uuid-123",
                          "status": "REVOKED"
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    public ResponseEntity<Map<String, String>> revokeApiKey(
        @Parameter(description = "API key ID to revoke") @PathVariable String keyId,
        HttpServletRequest httpRequest
    ) {
        String userEmail = getUserEmail(httpRequest);

        log.info("[API-KEY-MGMT] Revoking API key: keyId={}, user={}", keyId, userEmail);
        apiKeyService.revokeApiKey(keyId, userEmail);
        return ResponseEntity.ok(Map.of(
            "message", "API key revoked successfully",
            "keyId", keyId,
            "status", "REVOKED"
        ));
    }

    @PostMapping("/{keyId}/reactivate")
    @Operation(
        summary = "Reactivate API key",
        description = "Reactivate an inactive or revoked API key. " +
                      "Cannot reactivate expired keys - extend expiry first."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API key reactivated successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "409", description = "Cannot reactivate expired key - extend expiry first")
    })
    public ResponseEntity<ExtensionApiKeyResponse> reactivateApiKey(
        @Parameter(description = "API key ID to reactivate") @PathVariable String keyId,
        HttpServletRequest httpRequest
    ) {
        String userEmail = getUserEmail(httpRequest);

        log.info("[API-KEY-MGMT] Reactivating API key: keyId={}, user={}", keyId, userEmail);
        ExtensionApiKeyResponse response = apiKeyService.reactivateApiKey(keyId, userEmail);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{keyId}")
    @Operation(
        summary = "Delete API key",
        description = "Permanently delete an API key from the database (hard delete). " +
                      "This action CANNOT be undone. Use with caution!"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API key deleted successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    public ResponseEntity<Map<String, String>> deleteApiKey(
        @Parameter(description = "API key ID to delete") @PathVariable String keyId
    ) {
        log.info("[API-KEY-MGMT] Deleting API key: keyId={}", keyId);
        apiKeyService.deleteApiKey(keyId);
        return ResponseEntity.ok(Map.of(
            "message", "API key deleted successfully",
            "keyId", keyId
        ));
    }

    @PostMapping("/{keyId}/rotate")
    @Operation(
        summary = "Rotate API key",
        description = "Generate a new API key and revoke the old one. " +
                      "The new key inherits the same metadata (name, description) but gets a new expiry date. " +
                      "Importantly: Keycloak client credentials remain UNCHANGED - only the API key changes. " +
                      "Returns the new raw key which will NEVER be shown again."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "API key rotated successfully. Old key is REVOKED, new key is ACTIVE.",
            content = @Content(
                schema = @Schema(implementation = ExtensionApiKeyCreationResponse.class)
            )
        ),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    public ResponseEntity<ExtensionApiKeyCreationResponse> rotateApiKey(
        @Parameter(description = "API key ID to rotate") @PathVariable String keyId,
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Rotation details including reason and type",
            content = @Content(
                examples = @ExampleObject(
                    value = """
                        {
                          "reason": "Scheduled quarterly rotation",
                          "rotationType": "MANUAL"
                        }
                        """
                )
            )
        )
        RotateExtensionApiKeyRequest request,
        HttpServletRequest httpRequest
    ) {
        String userEmail = getUserEmail(httpRequest);
        String clientIp = httpRequest.getRemoteAddr();

        log.info("[API-KEY-MGMT] Rotating API key: keyId={}, reason={}, user={}",
            keyId, request.getReason(), userEmail);

        ExtensionApiKeyCreationResponse response = apiKeyService.rotateApiKey(
            keyId, request, userEmail, clientIp
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{keyId}/extend-expiry")
    @Operation(
        summary = "Extend API key expiry",
        description = "Extend the expiry date of an API key. " +
                      "Extension must be within policy limits (MAX_EXPIRY_EXTENSION_DAYS). " +
                      "Automatically reactivates EXPIRED keys if extended."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expiry date extended successfully"),
        @ApiResponse(responseCode = "400", description = "Extension exceeds policy limits"),
        @ApiResponse(responseCode = "403", description = "Expiry extension not allowed by policy"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "409", description = "Cannot extend revoked key")
    })
    public ResponseEntity<ExtensionApiKeyResponse> extendExpiry(
        @Parameter(description = "API key ID") @PathVariable String keyId,
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "New expiry date (must be in the future)",
            content = @Content(
                examples = @ExampleObject(
                    value = """
                        {
                          "newExpiryDate": "2027-12-31T23:59:59"
                        }
                        """
                )
            )
        )
        ExtendExtensionApiKeyExpiryRequest request,
        HttpServletRequest httpRequest
    ) {
        String userEmail = getUserEmail(httpRequest);

        log.info("[API-KEY-MGMT] Extending API key expiry: keyId={}, newExpiry={}, user={}",
            keyId, request.getNewExpiryDate(), userEmail);

        ExtensionApiKeyResponse response = apiKeyService.extendExpiry(keyId, request, userEmail);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{keyId}/rotation-history")
    @Operation(
        summary = "Get rotation history",
        description = "Retrieve complete rotation history for an API key. " +
                      "Shows all previous rotations including old/new key prefixes, reasons, and timestamps."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rotation history retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    public ResponseEntity<List<ExtensionApiKeyRotationHistory>> getRotationHistory(
        @Parameter(description = "API key ID") @PathVariable String keyId
    ) {
        log.info("[API-KEY-MGMT] Getting rotation history: keyId={}", keyId);
        List<ExtensionApiKeyRotationHistory> history = apiKeyService.getRotationHistory(keyId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/stats")
    @Operation(
        summary = "Get API key statistics",
        description = "Get aggregated statistics for API keys in your tenant. " +
                      "Shows counts by status (active, revoked, expired, expiring soon). " +
                      "Uses efficient database aggregation queries."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Statistics retrieved successfully",
            content = @Content(
                examples = @ExampleObject(
                    value = """
                        {
                          "totalKeys": 15,
                          "activeKeys": 12,
                          "inactiveKeys": 1,
                          "revokedKeys": 1,
                          "expiredKeys": 1,
                          "expiringSoonKeys": 2
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiKeyStatisticsResponse> getStatistics(
        HttpServletRequest httpRequest
    ) {
        String tenantId = getTenantOrThrow(httpRequest).getTenantID();
        log.info("[API-KEY-MGMT] Getting API key statistics: tenant={}", tenantId);

        ApiKeyStatisticsResponse stats = apiKeyService.getStatistics(tenantId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/settings")
    @Operation(
        summary = "Get API key settings",
        description = "Retrieve all configuration settings/policies for API key management. " +
                      "Includes default expiry days, warning thresholds, max keys per tenant, etc."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settings retrieved successfully")
    })
    public ResponseEntity<ApiKeySettingsResponse> getSettings() {
        log.info("[API-KEY-MGMT] Getting API key settings");
        Map<String, String> settings = configService.getAllConfigurations();
        return ResponseEntity.ok(new ApiKeySettingsResponse(settings));
    }

    @PatchMapping("/settings")
    @Operation(
        summary = "Update API key settings",
        description = "Update configuration settings/policies for API key management. " +
                      "Only editable settings can be updated. " +
                      "Requires admin privileges."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settings updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid settings"),
        @ApiResponse(responseCode = "403", description = "Cannot modify non-editable settings")
    })
    public ResponseEntity<ApiKeySettingsResponse> updateSettings(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Settings to update (key-value pairs)",
            content = @Content(
                examples = @ExampleObject(
                    value = """
                        {
                          "settings": {
                            "DEFAULT_EXPIRY_DAYS": "180",
                            "ALLOW_EXPIRY_EXTENSION": "true",
                            "MAX_KEYS_PER_TENANT": "10"
                          }
                        }
                        """
                )
            )
        )
        UpdateApiKeySettingsRequest request,
        HttpServletRequest httpRequest
    ) {
        String userEmail = getUserEmail(httpRequest);

        log.info("[API-KEY-MGMT] Updating API key settings: user={}", userEmail);
        configService.updateConfigurations(request.getSettings(), userEmail);

        Map<String, String> updatedSettings = configService.getAllConfigurations();
        return ResponseEntity.ok(new ApiKeySettingsResponse(updatedSettings));
    }
}
