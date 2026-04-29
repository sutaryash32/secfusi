package com.secufusion.events.controller;

import com.secufusion.events.dto.*;
import com.secufusion.events.service.UserActivityService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Device User endpoints for managing extension users.
 * Device users are automatically created by database triggers when
 * devices/events are inserted. They represent users who use the browser extension.
 */
@RestController
@RequestMapping("/api/events/device-users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Device Users", description = "Extension user management - users who use the browser extension")
public class DeviceUserController {

    private final UserActivityService userActivityService;
    private final JwtUtl jwtUtl;

    @GetMapping
    @Operation(summary = "List device users",
            description = "Get paginated list of device users (extension users) with device and event counts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device users retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<DeviceUserDTO>>> getDeviceUsers(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting device users for tenant={} page={} size={}", tenantId, page, size);

        try {
            Page<DeviceUserDTO> deviceUsers = userActivityService.getDeviceUserList(tenantId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(deviceUsers, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get device users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Search device users",
            description = "Search device users by email, username, or display name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - missing search term"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<DeviceUserDTO>>> searchDeviceUsers(
            HttpServletRequest request,
            @Parameter(description = "Search term (email, username, display name)") @RequestParam String q,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Searching device users for tenant={} term={}", tenantId, q);

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "Search term 'q' is required"));
        }

        try {
            Page<DeviceUserDTO> deviceUsers = userActivityService.searchDeviceUsers(tenantId, q, page, size);
            return ResponseEntity.ok(new ResponseDto<>(deviceUsers, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to search device users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{deviceUserId}")
    @Operation(summary = "Get device user details",
            description = "Get device user details with device and event counts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device user retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceUserDTO>> getDeviceUser(
            HttpServletRequest request,
            @Parameter(description = "Device User ID") @PathVariable String deviceUserId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting device user={} for tenant={}", deviceUserId, tenantId);

        DeviceUserDTO deviceUser = userActivityService.getDeviceUser(tenantId, deviceUserId);
        return ResponseEntity.ok(new ResponseDto<>(deviceUser, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/{deviceUserId}/comprehensive")
    @Operation(summary = "Get comprehensive device user details",
            description = "Get comprehensive device user details including all devices, events, extensions, " +
                    "activity breakdown, top domains, file operations, risk assessment, locations, " +
                    "policy violations, and security events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comprehensive details retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceUserDetailsDTO>> getDeviceUserComprehensiveDetails(
            HttpServletRequest request,
            @Parameter(description = "Device User ID") @PathVariable String deviceUserId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting comprehensive details for device user={} tenant={}", deviceUserId, tenantId);

        // ResourceNotFoundException propagates to GlobalExceptionHandler (returns 404)
        // Other unexpected exceptions also propagate to GlobalExceptionHandler (returns 500)
        DeviceUserDetailsDTO details = userActivityService.getDeviceUserDetails(tenantId, deviceUserId);
        return ResponseEntity.ok(new ResponseDto<>(details, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/{deviceUserId}/devices")
    @Operation(summary = "Get devices for device user",
            description = "Get all devices registered to a specific device user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<DeviceResponse>>> getDeviceUserDevices(
            HttpServletRequest request,
            @Parameter(description = "Device User ID") @PathVariable String deviceUserId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting devices for device user={} tenant={}", deviceUserId, tenantId);

        List<DeviceResponse> devices = userActivityService.getDeviceUserDevices(tenantId, deviceUserId);
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/{deviceUserId}/events")
    @Operation(summary = "Get events for device user",
            description = "Get paginated events for a specific device user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<EventDto>>> getDeviceUserEvents(
            HttpServletRequest request,
            @Parameter(description = "Device User ID") @PathVariable String deviceUserId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting events for device user={} tenant={}", deviceUserId, tenantId);

        Page<EventDto> events = userActivityService.getDeviceUserEvents(tenantId, deviceUserId, page, size);
        return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get device user statistics",
            description = "Get device user statistics including total, active, blocked, portal-linked, and extension-only users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Map<String, Object>>> getDeviceUserStats(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting device user stats for tenant={}", tenantId);

        try {
            Map<String, Object> stats = userActivityService.getDeviceUserStats(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get device user stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/by-email/{email}")
    @Operation(summary = "Get device user by email",
            description = "Find a device user by their email address")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device user retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceUserDTO>> getDeviceUserByEmail(
            HttpServletRequest request,
            @Parameter(description = "Email address") @PathVariable String email) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting device user by email={} for tenant={}", email, tenantId);

        try {
            return userActivityService.getDeviceUserByEmail(tenantId, email)
                    .map(deviceUser -> ResponseEntity.ok(new ResponseDto<>(deviceUser,
                            String.valueOf(HttpStatus.OK.value()))))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ResponseDto<>(null, "Device user not found for email: " + email)));
        } catch (Exception e) {
            log.error("Failed to get device user by email {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }
}
