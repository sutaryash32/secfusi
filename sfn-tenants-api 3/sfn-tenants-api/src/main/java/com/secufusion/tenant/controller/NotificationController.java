package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.NotificationDTO;
import com.secufusion.tenant.dto.NotificationPreferenceDTO;
import com.secufusion.tenant.dto.ResponseDto;
import com.secufusion.tenant.dto.UnreadCountDTO;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.service.NotificationService;
import com.secufusion.tenant.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tenants/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "APIs for in-app notification management and preferences")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtl jwtUtl;

    // ==================== NOTIFICATION CRUD ====================

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Returns paginated list of notifications for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ResponseDto<Page<NotificationDTO>>> getUserNotifications(
            @Parameter(hidden = true) HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User user = jwtUtl.getUserFromRequest(request);
        if (tenant == null || user == null) {
            return ResponseEntity.status(401)
                    .body(new ResponseDto<>("Unauthorized: unable to resolve tenant or user", "401"));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDTO> notifications = notificationService.getUserNotifications(
                tenant.getTenantID(), user.getPkUserId(), pageable);

        return ResponseEntity.ok(new ResponseDto<>(notifications, "200"));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count", description = "Returns total unread count with breakdown by type and severity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ResponseDto<UnreadCountDTO>> getUnreadCount(
            @Parameter(hidden = true) HttpServletRequest request) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User user = jwtUtl.getUserFromRequest(request);
        if (tenant == null || user == null) {
            return ResponseEntity.status(401)
                    .body(new ResponseDto<>("Unauthorized: unable to resolve tenant or user", "401"));
        }

        UnreadCountDTO count = notificationService.getUnreadCount(tenant.getTenantID(), user.getPkUserId());
        return ResponseEntity.ok(new ResponseDto<>(count, "200"));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read", description = "Marks a single notification as read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<ResponseDto<NotificationDTO>> markAsRead(
            @Parameter(hidden = true) HttpServletRequest request,
            @PathVariable String notificationId) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User user = jwtUtl.getUserFromRequest(request);
        if (tenant == null || user == null) {
            return ResponseEntity.status(401)
                    .body(new ResponseDto<>("Unauthorized: unable to resolve tenant or user", "401"));
        }

        NotificationDTO dto = notificationService.markAsRead(
                tenant.getTenantID(), user.getPkUserId(), notificationId);
        return ResponseEntity.ok(new ResponseDto<>(dto, "200"));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications as read for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All notifications marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ResponseDto<Integer>> markAllAsRead(
            @Parameter(hidden = true) HttpServletRequest request) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User user = jwtUtl.getUserFromRequest(request);
        if (tenant == null || user == null) {
            return ResponseEntity.status(401)
                    .body(new ResponseDto<>("Unauthorized: unable to resolve tenant or user", "401"));
        }

        int updated = notificationService.markAllAsRead(tenant.getTenantID(), user.getPkUserId());
        return ResponseEntity.ok(new ResponseDto<>(updated, "200"));
    }

    // ==================== PREFERENCES ====================

    @GetMapping("/preferences")
    @Operation(summary = "Get notification preferences", description = "Returns the authenticated user's notification channel preferences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ResponseDto<List<NotificationPreferenceDTO>>> getPreferences(
            @Parameter(hidden = true) HttpServletRequest request) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User user = jwtUtl.getUserFromRequest(request);
        if (tenant == null || user == null) {
            return ResponseEntity.status(401)
                    .body(new ResponseDto<>("Unauthorized: unable to resolve tenant or user", "401"));
        }

        List<NotificationPreferenceDTO> prefs = notificationService.getPreferences(
                tenant.getTenantID(), user.getPkUserId());
        return ResponseEntity.ok(new ResponseDto<>(prefs, "200"));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update notification preferences", description = "Creates or updates notification channel preferences for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ResponseDto<List<NotificationPreferenceDTO>>> updatePreferences(
            @Parameter(hidden = true) HttpServletRequest request,
            @RequestBody List<NotificationPreferenceDTO> preferences) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User user = jwtUtl.getUserFromRequest(request);
        if (tenant == null || user == null) {
            return ResponseEntity.status(401)
                    .body(new ResponseDto<>("Unauthorized: unable to resolve tenant or user", "401"));
        }

        List<NotificationPreferenceDTO> updated = notificationService.updatePreferences(
                tenant.getTenantID(), user.getPkUserId(), preferences);
        return ResponseEntity.ok(new ResponseDto<>(updated, "200"));
    }
}
