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
 * User activity endpoints for admin dashboard.
 * Provides user list with devices, extensions, and activity summaries.
 */
@RestController
@RequestMapping("/api/events/user-activity")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "User Activity", description = "User list with devices, extensions, and activity")
public class UserActivityController {

    private final UserActivityService userActivityService;
    private final JwtUtl jwtUtl;

    @GetMapping
    @Operation(summary = "Get user list",
            description = "Get paginated list of users with device and extension counts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<UserListDto>>> getUserList(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting user list for tenant={} page={} size={}", tenantId, page, size);

        try {
            Page<UserListDto> users = userActivityService.getUserList(tenantId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(users, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get user list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Search users",
            description = "Search users by name or email with device and extension counts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - missing search term"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<UserListDto>>> searchUsers(
            HttpServletRequest request,
            @Parameter(description = "Search term (name, email)") @RequestParam String q,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Searching users for tenant={} term={}", tenantId, q);

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "Search term 'q' is required"));
        }

        try {
            Page<UserListDto> users = userActivityService.searchUsers(tenantId, q, page, size);
            return ResponseEntity.ok(new ResponseDto<>(users, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to search users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user details",
            description = "Get user details with all devices and extension summary")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<UserWithDevicesDto>> getUserDetails(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting user details for userId={} tenant={}", userId, tenantId);

        try {
            UserWithDevicesDto user = userActivityService.getUserWithDevices(tenantId, userId);
            return ResponseEntity.ok(new ResponseDto<>(user, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get user details: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{userId}/devices")
    @Operation(summary = "Get user devices",
            description = "Get all devices for a specific user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<DeviceResponse>>> getUserDevices(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting devices for userId={} tenant={}", userId, tenantId);

        try {
            List<DeviceResponse> devices = userActivityService.getUserDevices(tenantId, userId);
            return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get user devices: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{userId}/extensions")
    @Operation(summary = "Get user extensions",
            description = "Get all extensions installed on user's devices")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extensions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<InstalledExtensionDto>>> getUserExtensions(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting extensions for userId={} tenant={}", userId, tenantId);

        try {
            List<InstalledExtensionDto> extensions = userActivityService.getUserExtensions(tenantId, userId);
            return ResponseEntity.ok(new ResponseDto<>(extensions, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get user extensions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/{userId}/extension-events")
    @Operation(summary = "Get user extension events",
            description = "Get extension events for a specific user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<ExtensionEventDto>>> getUserExtensionEvents(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting extension events for userId={} tenant={}", userId, tenantId);

        try {
            Page<ExtensionEventDto> events = userActivityService.getUserExtensionEvents(
                    tenantId, userId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get user extension events: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Get user statistics",
            description = "Get user statistics for tenant dashboard")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Map<String, Object>>> getUserStats(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("Getting user stats for tenant={}", tenantId);

        try {
            Map<String, Object> stats = userActivityService.getUserStats(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception e) {
            log.error("Failed to get user stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, e.getMessage()));
        }
    }
}
