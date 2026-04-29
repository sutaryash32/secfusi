package com.secufusion.events.controller;

import com.secufusion.events.dto.*;
import com.secufusion.events.service.ExtensionSyncService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Authenticated endpoints for extension management and monitoring.
 * Requires JWT authentication.
 */
@RestController
@RequestMapping("/api/events/extensions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Extensions", description = "Extension management and monitoring endpoints (requires authentication)")
public class ExtensionController {

    private final ExtensionSyncService extensionSyncService;
    private final JwtUtl jwtUtl;

    // ===== Extension Sync (Authenticated) =====

    @PostMapping("/sync")
    @Operation(summary = "Sync extensions (authenticated)",
            description = "Sync installed browser extensions for an authenticated user. " +
                    "Can optionally link an anonymous device to the user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extensions synced successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ExtensionSyncResponse> syncExtensions(
            HttpServletRequest request,
            @Valid @RequestBody ExtensionSyncRequest syncRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getPreferredUsernameFromRequest(request);

        log.info("Authenticated extension sync for user={} tenant={}, extensions count={}",
                userName, tenantId, syncRequest.getExtensions() != null ? syncRequest.getExtensions().size() : 0);

        try {
            String clientIp = getClientIp(request);
            ExtensionSyncResponse response = extensionSyncService.syncExtensionsAuthenticated(
                    syncRequest, tenantId, userId, userName, clientIp);
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

    @PostMapping("/link-device")
    @Operation(summary = "Link anonymous device to user",
            description = "Link an anonymous device (registered before login) to the authenticated user account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device linked successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceResponse>> linkAnonymousDevice(
            HttpServletRequest request,
            @Parameter(description = "Device token from anonymous registration")
            @RequestParam String deviceToken) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getPreferredUsernameFromRequest(request);

        log.info("Linking anonymous device to user={} tenant={}", userName, tenantId);

        try {
            DeviceResponse device = extensionSyncService.linkAnonymousDeviceToUser(
                    deviceToken, tenantId, userId, userName);
            return ResponseEntity.ok(new ResponseDto<>(device, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to link device: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    // ===== Warning Acknowledgment =====

    @PostMapping("/acknowledge-warning")
    @Operation(summary = "Acknowledge extension warning",
            description = "Record user's response to extension warning (ACKNOWLEDGED, PROCEEDED, DISMISSED, UNINSTALLED)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Acknowledgment recorded successfully"),
            @ApiResponse(responseCode = "404", description = "Extension or device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<String>> acknowledgeWarning(
            HttpServletRequest request,
            @Valid @RequestBody WarningAcknowledgmentRequest ackRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getPreferredUsernameFromRequest(request);

        log.info("Recording warning acknowledgment for extension={} device={} action={}",
                ackRequest.getExtensionId(), ackRequest.getDeviceId(), ackRequest.getUserAction());

        try {
            extensionSyncService.acknowledgeWarning(
                    tenantId,
                    ackRequest.getDeviceId(),
                    ackRequest.getExtensionId(),
                    userId,
                    userName,
                    ackRequest.getUserAction(),
                    ackRequest.getUserReason());

            return ResponseEntity.ok(new ResponseDto<>("Acknowledgment recorded", String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to record acknowledgment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    /**
     * Request DTO for warning acknowledgment
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WarningAcknowledgmentRequest {
        @jakarta.validation.constraints.NotBlank(message = "deviceId is required")
        private String deviceId;

        @jakarta.validation.constraints.NotBlank(message = "extensionId is required")
        private String extensionId;

        @jakarta.validation.constraints.NotBlank(message = "userAction is required")
        private String userAction; // ACKNOWLEDGED, PROCEEDED, DISMISSED, UNINSTALLED

        private String userReason;
    }

    // ===== Extension Search =====

    @GetMapping("/search")
    @Operation(summary = "Search extensions",
            description = "Search extensions by name or ID across the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - missing search term"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<InstalledExtensionDto>>> searchExtensions(
            HttpServletRequest request,
            @Parameter(description = "Search term (extension name or ID)") @RequestParam String q,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Searching extensions for tenant={} term={}", tenantId, q);

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "Search term 'q' is required"));
        }

        try {
            Page<InstalledExtensionDto> extensions = extensionSyncService.searchExtensions(tenantId, q, page, size);
            return ResponseEntity.ok(new ResponseDto<>(extensions, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to search extensions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Get all extensions",
            description = "Get all extensions in the tenant with pagination")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extensions retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<InstalledExtensionDto>>> getAllExtensions(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting all extensions for tenant={} page={} size={}", tenantId, page, size);

        try {
            Page<InstalledExtensionDto> extensions = extensionSyncService.getAllExtensions(tenantId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(extensions, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get extensions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    // ===== Extension Queries =====

    @GetMapping("/device/{deviceId}")
    @Operation(summary = "Get device extensions",
            description = "Get all extensions installed on a specific device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extensions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<InstalledExtensionDto>>> getDeviceExtensions(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting extensions for device={} tenant={}", deviceId, tenantId);

        try {
            List<InstalledExtensionDto> extensions = extensionSyncService.getDeviceExtensions(deviceId);
            return ResponseEntity.ok(new ResponseDto<>(extensions, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get device extensions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/high-risk")
    @Operation(summary = "Get high-risk extensions",
            description = "Get all extensions with HIGH risk level in the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extensions retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<InstalledExtensionDto>>> getHighRiskExtensions(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting high-risk extensions for tenant={}", tenantId);

        try {
            List<InstalledExtensionDto> extensions = extensionSyncService.getHighRiskExtensions(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(extensions, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get high-risk extensions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/blocked")
    @Operation(summary = "Get blocked extensions",
            description = "Get all extensions that are blocked by policy in the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extensions retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<InstalledExtensionDto>>> getBlockedExtensions(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting blocked extensions for tenant={}", tenantId);

        try {
            List<InstalledExtensionDto> extensions = extensionSyncService.getBlockedExtensions(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(extensions, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get blocked extensions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Get extension statistics",
            description = "Get extension statistics for the tenant including risk distribution and most common extensions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Map<String, Object>>> getExtensionStats(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting extension stats for tenant={}", tenantId);

        try {
            Map<String, Object> stats = extensionSyncService.getExtensionStats(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get extension stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    // ===== Extension Events =====

    @GetMapping("/events")
    @Operation(summary = "Get extension events",
            description = "Get extension events (install, uninstall, update, block, etc.) for the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<ExtensionEventDto>>> getExtensionEvents(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting extension events for tenant={} page={} size={}", tenantId, page, size);

        try {
            Page<ExtensionEventDto> events = extensionSyncService.getExtensionEvents(tenantId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get extension events: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/events/device/{deviceId}")
    @Operation(summary = "Get device extension events",
            description = "Get extension events for a specific device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<ExtensionEventDto>>> getDeviceExtensionEvents(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting extension events for device={} tenant={}", deviceId, tenantId);

        try {
            Page<ExtensionEventDto> events = extensionSyncService.getDeviceExtensionEvents(deviceId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get device extension events: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    /**
     * Get client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
