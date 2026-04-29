package com.secufusion.events.controller;

import com.secufusion.events.dto.*;
import com.secufusion.events.service.ExtensionSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoints for browser extension operations.
 * These endpoints do NOT require JWT authentication.
 * Used for anonymous device registration and extension sync before user login.
 */
@RestController
@RequestMapping("/api/public/extension")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Public Extension API", description = "Public endpoints for browser extension (no authentication required)")
public class PublicExtensionController {

    private final ExtensionSyncService extensionSyncService;

    @PostMapping("/register-device")
    @Operation(summary = "Register anonymous device",
            description = "Register an anonymous device using tenantCode (for MSI deployments). " +
                    "Returns a deviceToken that should be stored by the extension for subsequent requests.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device registered successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid data"),
            @ApiResponse(responseCode = "404", description = "Tenant not found for the given tenantCode"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AnonymousDeviceResponse> registerAnonymousDevice(
            @Valid @RequestBody AnonymousDeviceRegistrationRequest request) {

        log.info("Anonymous device registration request for tenantCode={}", request.getTenantCode());

        try {
            AnonymousDeviceResponse response = extensionSyncService.registerAnonymousDevice(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to register anonymous device: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AnonymousDeviceResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync extensions (anonymous)",
            description = "Sync installed browser extensions for an anonymous device. " +
                    "Uses deviceToken for authentication. Returns policy evaluation results.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extensions synced successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid data"),
            @ApiResponse(responseCode = "404", description = "Device not found for the given token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ExtensionSyncResponse> syncExtensionsAnonymous(
            @Valid @RequestBody ExtensionSyncRequest request) {

        log.info("Anonymous extension sync request, extensions count={}",
                request.getExtensions() != null ? request.getExtensions().size() : 0);

        if (request.getDeviceToken() == null || request.getDeviceToken().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ExtensionSyncResponse.builder()
                            .success(false)
                            .message("deviceToken is required for anonymous sync")
                            .build());
        }

        try {
            ExtensionSyncResponse response = extensionSyncService.syncExtensionsAnonymous(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to sync extensions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ExtensionSyncResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check",
            description = "Simple health check endpoint for browser extension connectivity test")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    public ResponseEntity<ResponseDto<String>> healthCheck() {
        return ResponseEntity.ok(new ResponseDto<>("OK", String.valueOf(HttpStatus.OK.value())));
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "Device heartbeat",
            description = "Update device last seen timestamp and get sync interval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Heartbeat received"),
            @ApiResponse(responseCode = "404", description = "Device not found")
    })
    public ResponseEntity<ResponseDto<HeartbeatResponse>> heartbeat(
            @RequestBody HeartbeatRequest request) {

        log.debug("Heartbeat from device token={}",
                request.getDeviceToken() != null ? request.getDeviceToken().substring(0, 8) + "..." : "null");

        if (request.getDeviceToken() == null || request.getDeviceToken().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(HeartbeatResponse.builder()
                            .success(false)
                            .message("deviceToken is required")
                            .build(), String.valueOf(HttpStatus.BAD_REQUEST.value())));
        }

        try {
            ExtensionSyncService.HeartbeatResult result = extensionSyncService.processHeartbeat(
                    request.getDeviceToken(), request.getExtensionVersion());

            HeartbeatResponse response = HeartbeatResponse.builder()
                    .success(result.getSuccess())
                    .syncIntervalSeconds(result.getSyncIntervalSeconds())
                    .policyVersion(result.getPolicyVersion())
                    .requiresSync(result.getRequiresSync())
                    .build();

            return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to process heartbeat: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(HeartbeatResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build(), String.valueOf(HttpStatus.NOT_FOUND.value())));
        }
    }

    /**
     * Request DTO for heartbeat
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HeartbeatRequest {
        private String deviceToken;
        private String extensionVersion;
    }

    /**
     * Response DTO for heartbeat
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HeartbeatResponse {
        private Boolean success;
        private Integer syncIntervalSeconds;
        private String policyVersion;
        private Boolean requiresSync;
        private String message;
    }
}
