package com.secufusion.iam.service;

import com.secufusion.iam.annotation.RequiresFeature;
import com.secufusion.iam.dto.DeviceLoginStatsDTO;
import com.secufusion.iam.dto.LoginAuditEventDTO;
import com.secufusion.iam.dto.LoginAuditPageResponse;
import com.secufusion.iam.dto.LoginDeviceDTO;
import com.secufusion.iam.entity.LoginAuditEvent;
import com.secufusion.iam.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.iam.entity.LoginAuditEvent.SourceService;
import com.secufusion.iam.entity.UserDeviceLogin;
import com.secufusion.iam.entity.UserDeviceLogin.DeviceLoginStatus;
import com.secufusion.iam.repository.LoginAuditRepository;
import com.secufusion.iam.repository.UserDeviceLoginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for logging and querying audit events from IAM operations.
 * Uses SourceService.IAM_API as the default source for all events logged from this service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoginAuditService {

    private final LoginAuditRepository loginAuditRepository;
    private final UserDeviceLoginRepository userDeviceLoginRepository;

    private static final SourceService DEFAULT_SOURCE = SourceService.IAM_API;

    // ============================================================
    // AUDIT LOGGING METHODS
    // ============================================================

    /**
     * Log an audit event asynchronously.
     */
    @Async
    @Transactional
    public void logEventAsync(LoginAuditEvent event) {
        try {
            if (event.getSourceService() == null) {
                event.setSourceService(DEFAULT_SOURCE);
            }
            loginAuditRepository.save(event);
            log.debug("Logged audit event: {} for user {} in tenant {}",
                    event.getEventType(), event.getUsername(), event.getTenantId());
        } catch (Exception e) {
            log.error("Failed to log audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Log an audit event synchronously.
     */
    @Transactional
    public LoginAuditEvent logEvent(LoginAuditEvent event) {
        if (event.getSourceService() == null) {
            event.setSourceService(DEFAULT_SOURCE);
        }
        return loginAuditRepository.save(event);
    }

    // ==================== Authentication Events ====================

    /**
     * Log successful login event.
     */
    public void logLoginSuccess(String tenantId, String realmName, String userId, String username,
                                 String email, String ipAddress, String userAgent, String sessionId,
                                 String clientId, String authMethod) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .email(email)
                .eventType(LoginEventType.LOGIN_SUCCESS)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .sessionId(sessionId)
                .clientId(clientId)
                .authMethod(authMethod)
                .success(true)
                .build();

        logEventAsync(event);
    }

    /**
     * Log failed login event.
     */
    public void logLoginFailure(String tenantId, String realmName, String username, String email,
                                 String ipAddress, String userAgent, String errorMessage, String errorCode) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .username(username)
                .email(email)
                .eventType(LoginEventType.LOGIN_FAILURE)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(false)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .build();

        logEventAsync(event);
    }

    /**
     * Log logout event.
     */
    public void logLogout(String tenantId, String realmName, String userId, String username,
                           String ipAddress, String sessionId) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.LOGOUT)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .sessionId(sessionId)
                .success(true)
                .build();

        logEventAsync(event);
    }

    /**
     * Log token refresh event.
     */
    public void logTokenRefresh(String tenantId, String realmName, String userId, String username,
                                 String ipAddress, String sessionId, boolean success, String errorMessage) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(success ? LoginEventType.TOKEN_REFRESH : LoginEventType.TOKEN_REFRESH_FAILURE)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .sessionId(sessionId)
                .success(success)
                .errorMessage(errorMessage)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM User Management Events ====================

    /**
     * Log user creation event.
     */
    public void logUserCreated(String tenantId, String realmName, String userId, String username,
                                String email, String createdBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .email(email)
                .eventType(LoginEventType.USER_CREATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log user update event.
     */
    public void logUserUpdated(String tenantId, String realmName, String userId, String username,
                                String updatedBy, String changes) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_UPDATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Updated by: " + updatedBy + ", Changes: " + changes)
                .build();

        logEventAsync(event);
    }

    /**
     * Log user deletion event.
     */
    public void logUserDeleted(String tenantId, String realmName, String userId, String username,
                                String deletedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_DELETED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Deleted by: " + deletedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log user enabled/disabled event.
     */
    public void logUserStatusChanged(String tenantId, String realmName, String userId,
                                      String username, boolean enabled, String changedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(enabled ? LoginEventType.USER_ENABLED : LoginEventType.USER_DISABLED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Changed by: " + changedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log user password set/reset event.
     */
    public void logUserPasswordSet(String tenantId, String realmName, String userId,
                                    String username, String setBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_PASSWORD_SET)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Set by: " + setBy)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM Role Management Events ====================

    /**
     * Log role creation event.
     */
    public void logRoleCreated(String tenantId, String realmName, String roleId, String roleName,
                                String createdBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(roleId)
                .username(roleName)
                .eventType(LoginEventType.ROLE_CREATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log role update event.
     */
    public void logRoleUpdated(String tenantId, String realmName, String roleId, String roleName,
                                String updatedBy, String changes) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(roleId)
                .username(roleName)
                .eventType(LoginEventType.ROLE_UPDATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Updated by: " + updatedBy + ", Changes: " + changes)
                .build();

        logEventAsync(event);
    }

    /**
     * Log role deletion event.
     */
    public void logRoleDeleted(String tenantId, String realmName, String roleId, String roleName,
                                String deletedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(roleId)
                .username(roleName)
                .eventType(LoginEventType.ROLE_DELETED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Deleted by: " + deletedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log role assignment to user event.
     */
    public void logRoleAssignedToUser(String tenantId, String realmName, String userId,
                                       String username, String roleName, String assignedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.ROLE_ASSIGNED_TO_USER)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Role: " + roleName + ", Assigned by: " + assignedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log role removal from user event.
     */
    public void logRoleRemovedFromUser(String tenantId, String realmName, String userId,
                                        String username, String roleName, String removedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.ROLE_REMOVED_FROM_USER)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Role: " + roleName + ", Removed by: " + removedBy)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM Group Management Events ====================

    /**
     * Log group creation event.
     */
    public void logGroupCreated(String tenantId, String realmName, String groupId,
                                 String groupName, String createdBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(groupId)
                .username(groupName)
                .eventType(LoginEventType.GROUP_CREATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log group update event.
     */
    public void logGroupUpdated(String tenantId, String realmName, String groupId,
                                 String groupName, String updatedBy, String changes) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(groupId)
                .username(groupName)
                .eventType(LoginEventType.GROUP_UPDATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Updated by: " + updatedBy + ", Changes: " + changes)
                .build();

        logEventAsync(event);
    }

    /**
     * Log group deletion event.
     */
    public void logGroupDeleted(String tenantId, String realmName, String groupId,
                                 String groupName, String deletedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(groupId)
                .username(groupName)
                .eventType(LoginEventType.GROUP_DELETED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Deleted by: " + deletedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log user added to group event.
     */
    public void logUserAddedToGroup(String tenantId, String realmName, String userId,
                                     String username, String groupName, String addedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_ADDED_TO_GROUP)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Group: " + groupName + ", Added by: " + addedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log user removed from group event.
     */
    public void logUserRemovedFromGroup(String tenantId, String realmName, String userId,
                                         String username, String groupName, String removedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_REMOVED_FROM_GROUP)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Group: " + groupName + ", Removed by: " + removedBy)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM Scope Management Events ====================

    /**
     * Log scope creation event.
     */
    public void logScopeCreated(String tenantId, String realmName, String scopeId,
                                 String scopeName, String createdBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(scopeId)
                .username(scopeName)
                .eventType(LoginEventType.SCOPE_CREATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log scope update event.
     */
    public void logScopeUpdated(String tenantId, String realmName, String scopeId,
                                 String scopeName, String updatedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(scopeId)
                .username(scopeName)
                .eventType(LoginEventType.SCOPE_UPDATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Updated by: " + updatedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log scope deletion event.
     */
    public void logScopeDeleted(String tenantId, String realmName, String scopeId,
                                 String scopeName, String deletedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(scopeId)
                .username(scopeName)
                .eventType(LoginEventType.SCOPE_DELETED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Deleted by: " + deletedBy)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM Permission Management Events ====================

    /**
     * Log permission creation event.
     */
    public void logPermissionCreated(String tenantId, String realmName, String permissionId,
                                      String permissionName, String createdBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(permissionId)
                .username(permissionName)
                .eventType(LoginEventType.PERMISSION_CREATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log permission assigned event.
     */
    public void logPermissionAssigned(String tenantId, String realmName, String targetId,
                                       String targetName, String permissionName, String assignedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(targetId)
                .username(targetName)
                .eventType(LoginEventType.PERMISSION_ASSIGNED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Permission: " + permissionName + ", Assigned by: " + assignedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log permission revoked event.
     */
    public void logPermissionRevoked(String tenantId, String realmName, String targetId,
                                      String targetName, String permissionName, String revokedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(targetId)
                .username(targetName)
                .eventType(LoginEventType.PERMISSION_REVOKED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Permission: " + permissionName + ", Revoked by: " + revokedBy)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM Client Management Events ====================

    /**
     * Log client creation event.
     */
    public void logClientCreated(String tenantId, String realmName, String clientId,
                                  String clientName, String createdBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(clientId)
                .username(clientName)
                .eventType(LoginEventType.CLIENT_CREATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log client update event.
     */
    public void logClientUpdated(String tenantId, String realmName, String clientId,
                                  String clientName, String updatedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(clientId)
                .username(clientName)
                .eventType(LoginEventType.CLIENT_UPDATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Updated by: " + updatedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log client deletion event.
     */
    public void logClientDeleted(String tenantId, String realmName, String clientId,
                                  String clientName, String deletedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(clientId)
                .username(clientName)
                .eventType(LoginEventType.CLIENT_DELETED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Deleted by: " + deletedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log client secret rotation event.
     */
    public void logClientSecretRotated(String tenantId, String realmName, String clientId,
                                        String clientName, String rotatedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(clientId)
                .username(clientName)
                .eventType(LoginEventType.CLIENT_SECRET_ROTATED)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Rotated by: " + rotatedBy)
                .build();

        logEventAsync(event);
    }

    // ============================================================
    // QUERY METHODS
    // ============================================================

    /**
     * Get all audit events for a tenant with pagination.
     * Requires POLICY_AUDIT_LOGS feature access.
     */
    @RequiresFeature("POLICY_AUDIT_LOGS")
    public LoginAuditPageResponse getEvents(String tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdOrderByEventTimestampDesc(tenantId, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get audit events for a specific user.
     */
    public LoginAuditPageResponse getEventsByUser(String tenantId, String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndUserIdOrderByEventTimestampDesc(tenantId, userId, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get audit events by event type.
     */
    public LoginAuditPageResponse getEventsByType(String tenantId, LoginEventType eventType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndEventTypeOrderByEventTimestampDesc(tenantId, eventType, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get audit events within a time range.
     */
    public LoginAuditPageResponse getEventsByTimeRange(String tenantId, LocalDateTime start,
                                                        LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndEventTimestampBetweenOrderByEventTimestampDesc(tenantId, start, end, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get events by source service.
     */
    public LoginAuditPageResponse getEventsBySourceService(String tenantId, SourceService sourceService,
                                                            int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndSourceServiceOrderByEventTimestampDesc(tenantId, sourceService, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get user management events.
     * Requires POLICY_ADVANCED_AUDIT feature with at least BASIC access level.
     */
    @RequiresFeature(value = "POLICY_ADVANCED_AUDIT", minimumLevel = "BASIC")
    public LoginAuditPageResponse getUserManagementEvents(String tenantId, LocalDateTime start,
                                                           LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findUserManagementEvents(tenantId, start, end, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get role management events.
     */
    public LoginAuditPageResponse getRoleManagementEvents(String tenantId, LocalDateTime start,
                                                           LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findRoleManagementEvents(tenantId, start, end, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get group management events.
     */
    public LoginAuditPageResponse getGroupManagementEvents(String tenantId, LocalDateTime start,
                                                            LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findGroupManagementEvents(tenantId, start, end, pageable);

        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get event counts by source service for analytics dashboard.
     */
    public Map<String, Long> getEventCountsBySourceService(String tenantId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = loginAuditRepository.getEventCountsBySourceService(tenantId, start, end);
        Map<String, Long> counts = new HashMap<>();

        for (Object[] row : results) {
            SourceService service = (SourceService) row[0];
            Long count = (Long) row[1];
            if (service != null) {
                counts.put(service.name(), count);
            }
        }

        return counts;
    }

    /**
     * Get events for a specific session.
     */
    public List<LoginAuditEventDTO> getSessionEvents(String tenantId, String sessionId) {
        return loginAuditRepository.findByTenantIdAndSessionIdOrderByEventTimestampDesc(tenantId, sessionId)
                .stream()
                .map(LoginAuditEventDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================================
    // CLEANUP METHODS
    // ============================================================

    /**
     * Delete old audit events (for GDPR compliance or storage management).
     */
    @Transactional
    public void deleteOldEvents(String tenantId, int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        log.info("Deleting audit events older than {} for tenant {}", cutoff, tenantId);
        loginAuditRepository.deleteByTenantIdAndEventTimestampBefore(tenantId, cutoff);
    }

    // ============================================================
    // DEVICE LOGIN TRACKING METHODS
    // ============================================================

    /**
     * Log a login event with device information and update device tracking.
     */
    @Transactional
    public void logLoginWithDevice(String tenantId, String realmName, String userId, String username,
                                    String email, String ipAddress, String userAgent, String sessionId,
                                    String clientId, String authMethod, String deviceId, String deviceFingerprint,
                                    String deviceName, String browserType, String osInfo, boolean success,
                                    String errorMessage, String errorCode) {
        log.debug("Logging login with device - tenantId={}, userId={}, deviceFingerprint={}",
                tenantId, userId, deviceFingerprint);

        // Check if device is new for this user
        Optional<UserDeviceLogin> existingDevice = userDeviceLoginRepository
                .findByTenantIdAndUserIdAndDeviceFingerprint(tenantId, userId, deviceFingerprint);

        boolean isNewDevice = existingDevice.isEmpty();
        boolean isTrustedDevice = existingDevice.map(UserDeviceLogin::getIsTrusted).orElse(false);

        // Create login audit event
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .email(email)
                .eventType(success ? LoginEventType.LOGIN_SUCCESS : LoginEventType.LOGIN_FAILURE)
                .sourceService(DEFAULT_SOURCE)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .sessionId(sessionId)
                .clientId(clientId)
                .authMethod(authMethod)
                .success(success)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                // Device fields
                .deviceId(deviceId)
                .deviceFingerprint(deviceFingerprint)
                .deviceName(deviceName)
                .browserType(browserType)
                .osInfo(osInfo)
                .isNewDevice(isNewDevice)
                .isTrustedDevice(isTrustedDevice)
                .build();

        loginAuditRepository.save(event);

        // Update or create user-device login record
        if (success) {
            updateOrCreateDeviceLogin(tenantId, userId, username, deviceId, deviceFingerprint,
                    deviceName, browserType, osInfo, userAgent, ipAddress, null, sessionId, true);
        } else {
            // Track failed login attempt on device
            existingDevice.ifPresent(device -> {
                device.setFailedLoginCount(device.getFailedLoginCount() + 1);
                device.setLastIpAddress(ipAddress);
                userDeviceLoginRepository.save(device);
            });
        }

        log.info("Logged {} login for user {} from device {} (new={})",
                success ? "successful" : "failed", username, deviceFingerprint, isNewDevice);
    }

    /**
     * Update or create a user-device login record.
     */
    @Transactional
    public UserDeviceLogin updateOrCreateDeviceLogin(String tenantId, String userId, String username,
                                                      String deviceId, String deviceFingerprint,
                                                      String deviceName, String browserType, String osInfo,
                                                      String userAgent, String ipAddress, String location,
                                                      String sessionId, boolean successfulLogin) {
        Optional<UserDeviceLogin> existingOpt = userDeviceLoginRepository
                .findByTenantIdAndUserIdAndDeviceFingerprint(tenantId, userId, deviceFingerprint);

        UserDeviceLogin deviceLogin;
        if (existingOpt.isPresent()) {
            deviceLogin = existingOpt.get();
            deviceLogin.setLastLoginAt(LocalDateTime.now());
            deviceLogin.setLastIpAddress(ipAddress);
            deviceLogin.setLastLocation(location);
            deviceLogin.setLastSessionId(sessionId);
            if (successfulLogin) {
                deviceLogin.setLoginCount(deviceLogin.getLoginCount() + 1);
                deviceLogin.setStatus(DeviceLoginStatus.ACTIVE);
            }
            // Update device info if provided
            if (deviceName != null) deviceLogin.setDeviceName(deviceName);
            if (browserType != null) deviceLogin.setBrowserType(browserType);
            if (osInfo != null) deviceLogin.setOsInfo(osInfo);
            if (userAgent != null) deviceLogin.setUserAgent(userAgent);
        } else {
            deviceLogin = UserDeviceLogin.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .username(username)
                    .deviceId(deviceId)
                    .deviceFingerprint(deviceFingerprint)
                    .deviceName(deviceName)
                    .browserType(browserType)
                    .osInfo(osInfo)
                    .userAgent(userAgent)
                    .firstLoginAt(LocalDateTime.now())
                    .lastLoginAt(LocalDateTime.now())
                    .lastIpAddress(ipAddress)
                    .lastLocation(location)
                    .lastSessionId(sessionId)
                    .loginCount(1)
                    .failedLoginCount(0)
                    .status(DeviceLoginStatus.ACTIVE)
                    .isTrusted(false)
                    .build();
        }

        return userDeviceLoginRepository.save(deviceLogin);
    }

    /**
     * Get all devices for a user.
     */
    @Transactional(readOnly = true)
    public Page<LoginDeviceDTO> getUserDevices(String tenantId, String userId, int page, int size) {
        log.debug("Getting devices for user - tenantId={}, userId={}", tenantId, userId);
        Pageable pageable = PageRequest.of(page, size);
        return userDeviceLoginRepository
                .findByTenantIdAndUserIdOrderByLastLoginAtDesc(tenantId, userId, pageable)
                .map(LoginDeviceDTO::fromEntity);
    }

    /**
     * Get active devices for a user.
     */
    @Transactional(readOnly = true)
    public Page<LoginDeviceDTO> getActiveUserDevices(String tenantId, String userId, int page, int size) {
        log.debug("Getting active devices for user - tenantId={}, userId={}", tenantId, userId);
        Pageable pageable = PageRequest.of(page, size);
        return userDeviceLoginRepository
                .findByTenantIdAndUserIdAndStatusOrderByLastLoginAtDesc(tenantId, userId,
                        DeviceLoginStatus.ACTIVE, pageable)
                .map(LoginDeviceDTO::fromEntity);
    }

    /**
     * Get trusted devices for a user.
     */
    @Transactional(readOnly = true)
    public List<LoginDeviceDTO> getTrustedDevices(String tenantId, String userId) {
        log.debug("Getting trusted devices for user - tenantId={}, userId={}", tenantId, userId);
        return userDeviceLoginRepository
                .findByTenantIdAndUserIdAndIsTrustedTrueOrderByLastLoginAtDesc(tenantId, userId)
                .stream()
                .map(LoginDeviceDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get all devices for a tenant.
     */
    @Transactional(readOnly = true)
    public Page<LoginDeviceDTO> getTenantDevices(String tenantId, int page, int size) {
        log.debug("Getting all devices for tenant - tenantId={}", tenantId);
        Pageable pageable = PageRequest.of(page, size);
        return userDeviceLoginRepository
                .findByTenantIdOrderByLastLoginAtDesc(tenantId, pageable)
                .map(LoginDeviceDTO::fromEntity);
    }

    /**
     * Get devices by status for a tenant.
     */
    @Transactional(readOnly = true)
    public Page<LoginDeviceDTO> getTenantDevicesByStatus(String tenantId, DeviceLoginStatus status,
                                                          int page, int size) {
        log.debug("Getting devices by status for tenant - tenantId={}, status={}", tenantId, status);
        Pageable pageable = PageRequest.of(page, size);
        return userDeviceLoginRepository
                .findByTenantIdAndStatusOrderByLastLoginAtDesc(tenantId, status, pageable)
                .map(LoginDeviceDTO::fromEntity);
    }

    /**
     * Get login events for a specific device.
     */
    @Transactional(readOnly = true)
    public LoginAuditPageResponse getDeviceLoginEvents(String tenantId, String deviceId, int page, int size) {
        log.debug("Getting login events for device - tenantId={}, deviceId={}", tenantId, deviceId);
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndDeviceIdOrderByEventTimestampDesc(tenantId, deviceId, pageable);
        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Get login events by device fingerprint.
     */
    @Transactional(readOnly = true)
    public LoginAuditPageResponse getDeviceLoginEventsByFingerprint(String tenantId, String fingerprint,
                                                                     int page, int size) {
        log.debug("Getting login events by fingerprint - tenantId={}, fingerprint={}", tenantId, fingerprint);
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndDeviceFingerprintOrderByEventTimestampDesc(tenantId, fingerprint, pageable);
        Page<LoginAuditEventDTO> dtoPage = events.map(LoginAuditEventDTO::fromEntity);
        return LoginAuditPageResponse.from(dtoPage);
    }

    /**
     * Trust a device.
     */
    @Transactional
    public boolean trustDevice(String tenantId, String userId, String deviceFingerprint, String trustedBy) {
        log.info("Trusting device - tenantId={}, userId={}, fingerprint={}, trustedBy={}",
                tenantId, userId, deviceFingerprint, trustedBy);
        int updated = userDeviceLoginRepository.trustDevice(tenantId, userId, deviceFingerprint, trustedBy);
        return updated > 0;
    }

    /**
     * Block a device.
     */
    @Transactional
    public boolean blockDevice(String tenantId, String userId, String deviceFingerprint,
                                String blockedBy, String reason) {
        log.info("Blocking device - tenantId={}, userId={}, fingerprint={}, blockedBy={}, reason={}",
                tenantId, userId, deviceFingerprint, blockedBy, reason);
        int updated = userDeviceLoginRepository.blockDevice(tenantId, userId, deviceFingerprint, blockedBy, reason);
        return updated > 0;
    }

    /**
     * Revoke all devices for a user.
     */
    @Transactional
    public int revokeAllUserDevices(String tenantId, String userId) {
        log.info("Revoking all devices for user - tenantId={}, userId={}", tenantId, userId);
        return userDeviceLoginRepository.revokeAllDevicesForUser(tenantId, userId);
    }

    /**
     * Search devices.
     */
    @Transactional(readOnly = true)
    public Page<LoginDeviceDTO> searchDevices(String tenantId, String searchTerm, int page, int size) {
        log.debug("Searching devices - tenantId={}, searchTerm={}", tenantId, searchTerm);
        Pageable pageable = PageRequest.of(page, size);
        return userDeviceLoginRepository.searchDevices(tenantId, searchTerm, pageable)
                .map(LoginDeviceDTO::fromEntity);
    }

    /**
     * Get device login statistics.
     */
    @Transactional(readOnly = true)
    public DeviceLoginStatsDTO getDeviceLoginStats(String tenantId, LocalDateTime start, LocalDateTime end) {
        log.debug("Getting device login stats - tenantId={}, start={}, end={}", tenantId, start, end);

        // Count by status
        long activeDevices = userDeviceLoginRepository.countByTenantIdAndStatus(tenantId, DeviceLoginStatus.ACTIVE);
        long blockedDevices = userDeviceLoginRepository.countByTenantIdAndStatus(tenantId, DeviceLoginStatus.BLOCKED);
        long trustedDevices = userDeviceLoginRepository.countByTenantIdAndIsTrustedTrue(tenantId);

        // Login counts by device type
        Map<String, Long> loginsByDeviceType = new HashMap<>();
        loginAuditRepository.getLoginCountsByDeviceType(tenantId, start, end).forEach(row -> {
            String deviceType = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            loginsByDeviceType.put(deviceType, count);
        });

        // Login counts by browser
        Map<String, Long> loginsByBrowser = new HashMap<>();
        loginAuditRepository.getLoginCountsByBrowser(tenantId, start, end).forEach(row -> {
            String browser = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            loginsByBrowser.put(browser, count);
        });

        // Login counts by OS
        Map<String, Long> loginsByOS = new HashMap<>();
        loginAuditRepository.getLoginCountsByOS(tenantId, start, end).forEach(row -> {
            String os = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            loginsByOS.put(os, count);
        });

        // Devices with most login failures
        List<DeviceLoginStatsDTO.DeviceFailureDTO> devicesWithMostFailures =
                loginAuditRepository.getDevicesWithMostLoginFailures(tenantId, start, end, PageRequest.of(0, 10))
                        .stream()
                        .map(row -> DeviceLoginStatsDTO.DeviceFailureDTO.builder()
                                .deviceFingerprint(row[0] != null ? row[0].toString() : null)
                                .deviceName(row[1] != null ? row[1].toString() : null)
                                .failureCount(((Number) row[2]).longValue())
                                .build())
                        .collect(Collectors.toList());

        // Daily activity
        List<DeviceLoginStatsDTO.DailyDeviceActivityDTO> dailyActivity =
                loginAuditRepository.getDailyLoginActivityWithDevices(tenantId, start, end)
                        .stream()
                        .map(row -> DeviceLoginStatsDTO.DailyDeviceActivityDTO.builder()
                                .date(row[0] != null ? row[0].toString() : "")
                                .totalLogins(((Number) row[1]).longValue())
                                .successfulLogins(((Number) row[2]).longValue())
                                .failedLogins(((Number) row[3]).longValue())
                                .uniqueDevices(((Number) row[4]).longValue())
                                .build())
                        .collect(Collectors.toList());

        // New device logins count
        long newDeviceLogins = loginAuditRepository.countNewDeviceLogins(tenantId, start, end);

        // Total and successful/failed logins
        long totalLogins = loginAuditRepository.countLoginsByTimeRange(tenantId, start, end);
        long successfulLogins = loginAuditRepository.countSuccessfulLoginsByTimeRange(tenantId, start, end);
        long failedLogins = totalLogins - successfulLogins;

        return DeviceLoginStatsDTO.builder()
                .totalDevices(activeDevices + blockedDevices)
                .activeDevices(activeDevices)
                .blockedDevices(blockedDevices)
                .trustedDevices(trustedDevices)
                .totalLogins(totalLogins)
                .successfulLogins(successfulLogins)
                .failedLogins(failedLogins)
                .newDeviceLogins(newDeviceLogins)
                .loginsByDeviceType(loginsByDeviceType)
                .loginsByBrowser(loginsByBrowser)
                .loginsByOS(loginsByOS)
                .devicesWithMostFailures(devicesWithMostFailures)
                .dailyActivity(dailyActivity)
                .periodStart(start)
                .periodEnd(end)
                .build();
    }

    /**
     * Get new device login events within a time range.
     */
    @Transactional(readOnly = true)
    public Page<LoginAuditEventDTO> getNewDeviceLogins(String tenantId, LocalDateTime start, LocalDateTime end,
                                                        int page, int size) {
        log.debug("Getting new device logins - tenantId={}, start={}, end={}", tenantId, start, end);
        Pageable pageable = PageRequest.of(page, size);
        return loginAuditRepository.findNewDeviceLogins(tenantId, start, end, pageable)
                .map(LoginAuditEventDTO::fromEntity);
    }

    /**
     * Count unique devices for a user.
     */
    @Transactional(readOnly = true)
    public long countUniqueDevicesForUser(String tenantId, String userId) {
        return userDeviceLoginRepository.countByTenantIdAndUserId(tenantId, userId);
    }

    /**
     * Get devices with high failure counts (potential security issues).
     */
    @Transactional(readOnly = true)
    public List<LoginDeviceDTO> getDevicesWithHighFailures(String tenantId, int threshold) {
        log.debug("Getting devices with high failures - tenantId={}, threshold={}", tenantId, threshold);
        return userDeviceLoginRepository.findDevicesWithHighFailures(tenantId, threshold)
                .stream()
                .map(LoginDeviceDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get inactive devices (not seen since a given time).
     */
    @Transactional(readOnly = true)
    public List<LoginDeviceDTO> getInactiveDevices(String tenantId, LocalDateTime since) {
        log.debug("Getting inactive devices - tenantId={}, since={}", tenantId, since);
        return userDeviceLoginRepository.findInactiveDevices(tenantId, since)
                .stream()
                .map(LoginDeviceDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
