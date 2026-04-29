package com.secufusion.events.controller;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.DeviceStatus;
import com.secufusion.events.service.ActivitySummaryService;
import com.secufusion.events.service.DeviceService;
import com.secufusion.events.service.EventService;
import com.secufusion.events.service.UserActivityService;
import com.secufusion.events.util.JwtUtl;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events/devices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Devices", description = "Device management and activity endpoints")
public class DeviceController {

    private final DeviceService deviceService;
    private final ActivitySummaryService activitySummaryService;
    private final EventService eventService;
    private final UserActivityService userActivityService;
    private final JwtUtl jwtUtl;

    @PostMapping("/register")
    @Operation(summary = "Register a device",
            description = "Registers a new device or updates an existing one based on device fingerprint")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device registered successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceResponse>> registerDevice(
            HttpServletRequest request,
            @Valid @RequestBody DeviceRegistrationRequest registrationRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getPreferredUsernameFromRequest(request);
        String email = jwtUtl.getEmail(request);
        String displayName = jwtUtl.getDisplayName(request);

        log.info("Device registration request for tenant={} user={} email={}", tenantId, userName, email);

        try {
            DeviceResponse device = deviceService.registerDevice(
                    tenantId, userId, userName, email, displayName, registrationRequest, request);
            return ResponseEntity.ok(new ResponseDto<>(device,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to register device: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "List devices",
            description = "Get all devices for the current tenant with optional status filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<DeviceResponse>>> getDevices(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Filter by status") @RequestParam(required = false) DeviceStatus status) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting devices for tenant={} status={}", tenantId, status);

        try {
            Page<DeviceResponse> devices;
            if (status != null) {
                devices = deviceService.getDevicesByStatus(tenantId, status, page, size);
            } else {
                devices = deviceService.getDevices(tenantId, page, size);
            }

            return ResponseEntity.ok(new ResponseDto<>(devices,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get devices: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}")
    @Operation(summary = "Get device details",
            description = "Get details of a specific device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceResponse>> getDevice(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting device={} for tenant={}", deviceId, tenantId);

        try {
            DeviceResponse device = deviceService.getDevice(tenantId, deviceId);
            return ResponseEntity.ok(new ResponseDto<>(device,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @PutMapping("/{deviceId}/status")
    @Operation(summary = "Update device status",
            description = "Update the status of a device (ACTIVE, INACTIVE, BLOCKED)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceResponse>> updateDeviceStatus(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            @Valid @RequestBody DeviceStatusUpdateRequest statusRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("Updating device={} status to {} for tenant={}",
                deviceId, statusRequest.getStatus(), tenantId);

        try {
            DeviceResponse device = deviceService.updateDeviceStatus(
                    tenantId, deviceId, statusRequest);
            return ResponseEntity.ok(new ResponseDto<>(device,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to update device {} status: {}", deviceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}/summary")
    @Operation(summary = "Get device activity summary",
            description = "Get aggregated activity statistics for a specific device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceActivitySummaryDTO>> getDeviceActivitySummary(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting activity summary for device={} tenant={}", deviceId, tenantId);

        try {
            DeviceActivitySummaryDTO summary = activitySummaryService
                    .getDeviceActivitySummary(tenantId, deviceId);
            return ResponseEntity.ok(new ResponseDto<>(summary,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get activity summary for device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Get device statistics",
            description = "Get device count statistics for the tenant (total, active, inactive, blocked)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Map<String, Long>>> getDeviceStats(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting device stats for tenant={}", tenantId);

        try {
            Map<String, Long> stats = deviceService.getDeviceStats(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(stats,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get device stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent devices",
            description = "Get the most recently active devices")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<DeviceResponse>>> getRecentDevices(
            HttpServletRequest request,
            @Parameter(description = "Number of devices to return") @RequestParam(defaultValue = "10") int limit) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting recent devices for tenant={} limit={}", tenantId, limit);

        try {
            List<DeviceResponse> devices = deviceService.getRecentDevices(tenantId, limit);
            return ResponseEntity.ok(new ResponseDto<>(devices,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get recent devices: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/by-type")
    @Operation(summary = "Get devices by type",
            description = "Get device count distribution by type (Desktop, Mobile, Tablet, Unknown)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Distribution retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Map<String, Long>>> getDevicesByType(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting devices by type for tenant={}", tenantId);

        try {
            Map<String, Long> byType = deviceService.getDevicesByType(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(byType,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get devices by type: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/user/{userName}")
    @Operation(summary = "Get devices for user by username",
            description = "Get all devices registered to a specific user by username. Consider using /device-user/{deviceUserId} for more reliable queries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<DeviceResponse>>> getDevicesForUser(
            HttpServletRequest request,
            @Parameter(description = "Username") @PathVariable String userName) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting devices for user={} tenant={}", userName, tenantId);

        try {
            List<DeviceResponse> devices = deviceService.getDevicesForUser(tenantId, userName);
            return ResponseEntity.ok(new ResponseDto<>(devices,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get devices for user {}: {}", userName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/device-user/{deviceUserId}")
    @Operation(summary = "Get devices for device user",
            description = "Get all devices registered to a specific device user by their device_user ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<DeviceResponse>>> getDevicesForDeviceUser(
            HttpServletRequest request,
            @Parameter(description = "Device User ID") @PathVariable String deviceUserId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting devices for deviceUser={} tenant={}", deviceUserId, tenantId);

        try {
            List<DeviceResponse> devices = userActivityService.getDeviceUserDevices(tenantId, deviceUserId);
            return ResponseEntity.ok(new ResponseDto<>(devices,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get devices for device user {}: {}", deviceUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Deactivate device",
            description = "Soft delete a device by setting its status to INACTIVE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<DeviceResponse>> deactivateDevice(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("Deactivating device={} for tenant={}", deviceId, tenantId);

        try {
            DeviceResponse device = deviceService.deactivateDevice(tenantId, deviceId);
            return ResponseEntity.ok(new ResponseDto<>(device,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to deactivate device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Search devices",
            description = "Search devices by name, OS, browser, or username with optional status filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - missing search term"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<DeviceResponse>>> searchDevices(
            HttpServletRequest request,
            @Parameter(description = "Search term (searches name, OS, browser, username)")
            @RequestParam String q,
            @Parameter(description = "Filter by status") @RequestParam(required = false) DeviceStatus status,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Searching devices for tenant={} term={} status={}", tenantId, q, status);

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "Search term 'q' is required"));
        }

        try {
            Page<DeviceResponse> devices = deviceService.searchDevices(tenantId, q, status, page, size);
            return ResponseEntity.ok(new ResponseDto<>(devices,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to search devices: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}/events")
    @Operation(summary = "Get device events history",
            description = "Get detailed event log for a specific device with pagination and optional date range filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<?>> getDeviceEventsHistory(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Start date for filtering (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for filtering (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting events history for device={} tenant={} page={} size={} startDate={} endDate={}",
                deviceId, tenantId, page, size, startDate, endDate);

        try {
            // Verify device belongs to tenant
            deviceService.getDevice(tenantId, deviceId);

            // If date range is provided, use time range query
            if (startDate != null && endDate != null) {
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(LocalTime.MAX);
                List<EventDto> events = eventService.getEventsByDeviceAndTimeRange(deviceId, start, end);
                return ResponseEntity.ok(new ResponseDto<>(events,
                        String.valueOf(HttpStatus.OK.value())));
            }

            // Otherwise use paginated query
            Page<EventDto> events = eventService.getEventsByDevice(deviceId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events,
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get events for device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }
}
