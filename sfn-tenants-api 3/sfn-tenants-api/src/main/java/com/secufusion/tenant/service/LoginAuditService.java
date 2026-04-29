package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.LoginAuditEventDTO;
import com.secufusion.tenant.dto.LoginAuditPageResponse;
import com.secufusion.tenant.dto.LoginAuditSearchRequest;
import com.secufusion.tenant.dto.LoginAuditStatsDTO;
import com.secufusion.tenant.entity.LoginAuditEvent;
import com.secufusion.tenant.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.tenant.entity.LoginAuditEvent.SourceService;
import com.secufusion.tenant.repository.LoginAuditRepository;
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
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginAuditService {

    private final LoginAuditRepository loginAuditRepository;

    // ============================================================
    // AUDIT LOGGING METHODS
    // ============================================================

    /**
     * Log a login audit event asynchronously.
     */
    @Async
    @Transactional
    public void logEventAsync(LoginAuditEvent event) {
        try {
            loginAuditRepository.save(event);
            log.debug("Logged audit event: {} for user {} in tenant {}",
                    event.getEventType(), event.getUsername(), event.getTenantId());
        } catch (Exception e) {
            log.error("Failed to log audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Log a login audit event synchronously.
     */
    @Transactional
    public LoginAuditEvent logEvent(LoginAuditEvent event) {
        return loginAuditRepository.save(event);
    }

    /**
     * Log a successful login.
     */
    public void logLoginSuccess(String tenantId, String realmName, String userId, String username,
                                 String email, String ipAddress, String userAgent, String clientId,
                                 String sessionId, String authMethod, Boolean mfaUsed, Boolean rememberMe) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .email(email)
                .eventType(LoginEventType.LOGIN_SUCCESS)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .clientId(clientId)
                .sessionId(sessionId)
                .success(true)
                .authMethod(authMethod)
                .mfaUsed(mfaUsed)
                .rememberMe(rememberMe)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a failed login attempt.
     */
    public void logLoginFailure(String tenantId, String realmName, String username,
                                 String ipAddress, String userAgent, String clientId,
                                 String errorCode, String errorMessage) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .username(username)
                .eventType(LoginEventType.LOGIN_FAILURE)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .clientId(clientId)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a logout event.
     */
    public void logLogout(String tenantId, String realmName, String userId, String username,
                          String ipAddress, String sessionId, boolean logoutAll) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(logoutAll ? LoginEventType.LOGOUT_ALL : LoginEventType.LOGOUT)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .sessionId(sessionId)
                .success(true)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a failed logout attempt.
     */
    public void logLogoutFailure(String tenantId, String realmName, String userId,
                                  String username, String sessionId, String errorMessage) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.LOGOUT)
                .eventTimestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .success(false)
                .errorMessage(errorMessage)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a session revocation.
     */
    public void logSessionRevoked(String tenantId, String realmName, String userId,
                                   String username, String sessionId, String revokedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.SESSION_REVOKED)
                .eventTimestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .success(true)
                .additionalDetails("Revoked by: " + revokedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log an account lockout.
     */
    public void logAccountLocked(String tenantId, String realmName, String userId,
                                  String username, String ipAddress, String reason) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.ACCOUNT_LOCKED)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .success(false)
                .additionalDetails(reason)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a password reset request.
     */
    public void logPasswordResetRequest(String tenantId, String realmName, String userId,
                                         String username, String email, String ipAddress) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .email(email)
                .eventType(LoginEventType.PASSWORD_RESET_REQUEST)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .success(true)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a password change.
     */
    public void logPasswordChange(String tenantId, String realmName, String userId,
                                   String username, String ipAddress) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.PASSWORD_CHANGE)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .success(true)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a password change with change type (self or admin).
     */
    public void logPasswordChange(String tenantId, String realmName, String userId, String changedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .eventType(LoginEventType.PASSWORD_CHANGE)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Changed by: " + changedBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a password reset (admin force reset).
     */
    public void logPasswordReset(String tenantId, String realmName, String userId, String resetBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .eventType(LoginEventType.PASSWORD_RESET_REQUEST)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Force reset by: " + resetBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log a bulk session revoke operation.
     */
    public void logBulkSessionRevoke(String tenantId, String realmName, int revokedCount,
                                      int failedCount, String revokedBy) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .eventType(LoginEventType.SESSION_REVOKED)
                .eventTimestamp(LocalDateTime.now())
                .success(failedCount == 0)
                .additionalDetails(String.format("Bulk revoke by %s: %d revoked, %d failed",
                        revokedBy, revokedCount, failedCount))
                .build();

        logEventAsync(event);
    }

    // ============================================================
    // QUERY METHODS
    // ============================================================

    /**
     * Get all audit events for a tenant with pagination.
     */
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
     * Get audit events for a specific username.
     */
    public LoginAuditPageResponse getEventsByUsername(String tenantId, String username, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndUsernameOrderByEventTimestampDesc(tenantId, username, pageable);

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
     * Get audit events by IP address.
     */
    public LoginAuditPageResponse getEventsByIpAddress(String tenantId, String ipAddress, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findByTenantIdAndIpAddressOrderByEventTimestampDesc(tenantId, ipAddress, pageable);

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
     * Search audit events with multiple criteria.
     */
    public LoginAuditPageResponse searchEvents(String tenantId, LoginAuditSearchRequest request) {
        // For complex searches, use specific queries based on provided filters
        if (request.getUserId() != null) {
            return getEventsByUser(tenantId, request.getUserId(), request.getPage(), request.getSize());
        }
        if (request.getUsername() != null) {
            return getEventsByUsername(tenantId, request.getUsername(), request.getPage(), request.getSize());
        }
        if (request.getEventType() != null) {
            return getEventsByType(tenantId, request.getEventType(), request.getPage(), request.getSize());
        }
        if (request.getIpAddress() != null) {
            return getEventsByIpAddress(tenantId, request.getIpAddress(), request.getPage(), request.getSize());
        }
        if (request.getStartTime() != null && request.getEndTime() != null) {
            return getEventsByTimeRange(tenantId, request.getStartTime(), request.getEndTime(),
                    request.getPage(), request.getSize());
        }

        return getEvents(tenantId, request.getPage(), request.getSize());
    }

    // ============================================================
    // STATISTICS METHODS
    // ============================================================

    /**
     * Get login audit statistics for a tenant.
     */
    public LoginAuditStatsDTO getStats(String tenantId, LocalDateTime start, LocalDateTime end) {
        log.debug("Getting login audit stats for tenant {} from {} to {}", tenantId, start, end);

        List<Object[]> eventCounts = loginAuditRepository.getEventCountsByType(tenantId, start, end);
        Map<String, Long> countsByType = new HashMap<>();
        long successfulLogins = 0;
        long failedLogins = 0;
        long totalLogouts = 0;
        long passwordResets = 0;
        long accountLockouts = 0;

        for (Object[] row : eventCounts) {
            LoginEventType type = (LoginEventType) row[0];
            Long count = (Long) row[1];
            countsByType.put(type.name(), count);

            switch (type) {
                case LOGIN_SUCCESS -> successfulLogins = count;
                case LOGIN_FAILURE -> failedLogins = count;
                case LOGOUT, LOGOUT_ALL -> totalLogouts += count;
                case PASSWORD_RESET_REQUEST, PASSWORD_RESET_SUCCESS -> passwordResets += count;
                case ACCOUNT_LOCKED -> accountLockouts = count;
                default -> {}
            }
        }

        long totalLoginAttempts = successfulLogins + failedLogins;
        double successRate = totalLoginAttempts > 0
                ? (double) successfulLogins / totalLoginAttempts * 100
                : 0.0;

        long uniqueActiveUsers = loginAuditRepository.countUniqueActiveUsers(tenantId, start, end);

        // Get logins by hour
        Map<Integer, Long> loginsByHour = new HashMap<>();
        List<Object[]> hourlyData = loginAuditRepository.getLoginsByHour(tenantId, start, end);
        for (Object[] row : hourlyData) {
            Integer hour = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            loginsByHour.put(hour, count);
        }

        return LoginAuditStatsDTO.builder()
                .totalLoginAttempts(totalLoginAttempts)
                .successfulLogins(successfulLogins)
                .failedLogins(failedLogins)
                .uniqueActiveUsers(uniqueActiveUsers)
                .totalLogouts(totalLogouts)
                .passwordResets(passwordResets)
                .accountLockouts(accountLockouts)
                .loginSuccessRate(Math.round(successRate * 100.0) / 100.0)
                .eventCountsByType(countsByType)
                .loginsByHour(loginsByHour)
                .periodStart(start)
                .periodEnd(end)
                .build();
    }

    /**
     * Count recent failed login attempts for a user.
     */
    public long countRecentLoginFailures(String tenantId, String username, int minutesBack) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        return loginAuditRepository.countLoginFailuresSince(tenantId, username, since);
    }

    /**
     * Count recent failed login attempts from an IP address.
     */
    public long countRecentLoginFailuresFromIp(String tenantId, String ipAddress, int minutesBack) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        return loginAuditRepository.countLoginFailuresFromIpSince(tenantId, ipAddress, since);
    }

    /**
     * Find suspicious IP addresses with many failed login attempts.
     */
    public List<Map<String, Object>> findSuspiciousIps(String tenantId, int minutesBack, long threshold) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        List<Object[]> results = loginAuditRepository.findSuspiciousIps(tenantId, since, threshold);

        return results.stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("ipAddress", row[0]);
                    map.put("failedAttempts", row[1]);
                    return map;
                })
                .collect(Collectors.toList());
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
    // UNIFIED AUDIT METHODS (Multi-Service Support)
    // ============================================================

    /**
     * Log a generic audit event with source service.
     */
    @Async
    @Transactional
    public void logUnifiedEvent(String tenantId, String realmName, LoginEventType eventType,
                                 SourceService sourceService, String userId, String username,
                                 String additionalDetails) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .eventType(eventType)
                .sourceService(sourceService)
                .userId(userId)
                .username(username)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails(additionalDetails)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM User Management Events ====================

    /**
     * Log user creation event.
     */
    public void logUserCreated(String tenantId, String realmName, String userId, String username,
                                String email, String createdBy, SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .email(email)
                .eventType(LoginEventType.USER_CREATED)
                .sourceService(sourceService)
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
                                String updatedBy, String changes, SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_UPDATED)
                .sourceService(sourceService)
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
                                String deletedBy, SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_DELETED)
                .sourceService(sourceService)
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
                                      String username, boolean enabled, String changedBy,
                                      SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(enabled ? LoginEventType.USER_ENABLED : LoginEventType.USER_DISABLED)
                .sourceService(sourceService)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Changed by: " + changedBy)
                .build();

        logEventAsync(event);
    }

    // ==================== IAM Role Management Events ====================

    /**
     * Log role creation event.
     */
    public void logRoleCreated(String tenantId, String realmName, String roleId, String roleName,
                                String createdBy, SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(roleId)
                .username(roleName)
                .eventType(LoginEventType.ROLE_CREATED)
                .sourceService(sourceService)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log role assignment to user event.
     */
    public void logRoleAssignedToUser(String tenantId, String realmName, String userId,
                                       String username, String roleName, String assignedBy,
                                       SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.ROLE_ASSIGNED_TO_USER)
                .sourceService(sourceService)
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
                                        String username, String roleName, String removedBy,
                                        SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.ROLE_REMOVED_FROM_USER)
                .sourceService(sourceService)
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
                                 String groupName, String createdBy, SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(groupId)
                .username(groupName)
                .eventType(LoginEventType.GROUP_CREATED)
                .sourceService(sourceService)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log user added to group event.
     */
    public void logUserAddedToGroup(String tenantId, String realmName, String userId,
                                     String username, String groupName, String addedBy,
                                     SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_ADDED_TO_GROUP)
                .sourceService(sourceService)
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
                                         String username, String groupName, String removedBy,
                                         SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .realmName(realmName)
                .userId(userId)
                .username(username)
                .eventType(LoginEventType.USER_REMOVED_FROM_GROUP)
                .sourceService(sourceService)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Group: " + groupName + ", Removed by: " + removedBy)
                .build();

        logEventAsync(event);
    }

    // ==================== Tenant Management Events ====================

    /**
     * Log tenant creation event.
     */
    public void logTenantCreated(String tenantId, String tenantName, String createdBy,
                                  SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .username(tenantName)
                .eventType(LoginEventType.TENANT_CREATED)
                .sourceService(sourceService)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Created by: " + createdBy)
                .build();

        logEventAsync(event);
    }

    /**
     * Log tenant status change event.
     */
    public void logTenantStatusChanged(String tenantId, String tenantName, boolean activated,
                                        String changedBy, SourceService sourceService) {
        LoginAuditEvent event = LoginAuditEvent.builder()
                .tenantId(tenantId)
                .username(tenantName)
                .eventType(activated ? LoginEventType.TENANT_ACTIVATED : LoginEventType.TENANT_SUSPENDED)
                .sourceService(sourceService)
                .eventTimestamp(LocalDateTime.now())
                .success(true)
                .additionalDetails("Changed by: " + changedBy)
                .build();

        logEventAsync(event);
    }

    // ==================== Query Methods for Unified Audit ====================

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
     */
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
     * Get tenant management events.
     */
    public LoginAuditPageResponse getTenantManagementEvents(String tenantId, LocalDateTime start,
                                                             LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LoginAuditEvent> events = loginAuditRepository
                .findTenantManagementEvents(tenantId, start, end, pageable);

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

    // ============================================================
    // CLEANUP METHODS
    // ============================================================

    /**
     * Delete old audit events (for GDPR compliance or storage management).
     */
    @Transactional
    public void deleteOldEvents(String tenantId, int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        log.info("Deleting login audit events older than {} for tenant {}", cutoff, tenantId);
        loginAuditRepository.deleteByTenantIdAndEventTimestampBefore(tenantId, cutoff);
    }
}
