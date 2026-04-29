package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.LoginAuditEvent;
import com.secufusion.tenant.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.tenant.entity.LoginAuditEvent.SourceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAuditRepository extends JpaRepository<LoginAuditEvent, Long> {

    /**
     * Find all events for a tenant with pagination.
     */
    Page<LoginAuditEvent> findByTenantIdOrderByEventTimestampDesc(String tenantId, Pageable pageable);

    /**
     * Find events by tenant and user ID.
     */
    Page<LoginAuditEvent> findByTenantIdAndUserIdOrderByEventTimestampDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find events by tenant and username.
     */
    Page<LoginAuditEvent> findByTenantIdAndUsernameOrderByEventTimestampDesc(
            String tenantId, String username, Pageable pageable);

    /**
     * Find events by tenant and event type.
     */
    Page<LoginAuditEvent> findByTenantIdAndEventTypeOrderByEventTimestampDesc(
            String tenantId, LoginEventType eventType, Pageable pageable);

    /**
     * Find events by tenant within a time range.
     */
    Page<LoginAuditEvent> findByTenantIdAndEventTimestampBetweenOrderByEventTimestampDesc(
            String tenantId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find failed login attempts for a user.
     */
    List<LoginAuditEvent> findByTenantIdAndUsernameAndEventTypeAndEventTimestampAfter(
            String tenantId, String username, LoginEventType eventType, LocalDateTime since);

    /**
     * Find events by IP address.
     */
    Page<LoginAuditEvent> findByTenantIdAndIpAddressOrderByEventTimestampDesc(
            String tenantId, String ipAddress, Pageable pageable);

    /**
     * Count login failures for a user since a given time.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.username = :username AND e.eventType = 'LOGIN_FAILURE' " +
            "AND e.eventTimestamp > :since")
    long countLoginFailuresSince(@Param("tenantId") String tenantId,
                                  @Param("username") String username,
                                  @Param("since") LocalDateTime since);

    /**
     * Count login failures from an IP address since a given time.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.ipAddress = :ipAddress AND e.eventType = 'LOGIN_FAILURE' " +
            "AND e.eventTimestamp > :since")
    long countLoginFailuresFromIpSince(@Param("tenantId") String tenantId,
                                        @Param("ipAddress") String ipAddress,
                                        @Param("since") LocalDateTime since);

    /**
     * Get login statistics - count by event type for a tenant.
     */
    @Query("SELECT e.eventType, COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventTimestamp BETWEEN :start AND :end GROUP BY e.eventType")
    List<Object[]> getEventCountsByType(@Param("tenantId") String tenantId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * Get unique active users count (successful logins) within a time range.
     */
    @Query("SELECT COUNT(DISTINCT e.userId) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_SUCCESS' AND e.eventTimestamp BETWEEN :start AND :end")
    long countUniqueActiveUsers(@Param("tenantId") String tenantId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * Get login attempts by hour for analytics.
     */
    @Query(value = "SELECT EXTRACT(HOUR FROM event_timestamp) as hour, COUNT(*) as count " +
            "FROM login_audit_event WHERE tenant_id = :tenantId " +
            "AND event_type = 'LOGIN_SUCCESS' AND event_timestamp BETWEEN :start AND :end " +
            "GROUP BY EXTRACT(HOUR FROM event_timestamp) ORDER BY hour", nativeQuery = true)
    List<Object[]> getLoginsByHour(@Param("tenantId") String tenantId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /**
     * Find suspicious activities - multiple failed logins from same IP.
     */
    @Query("SELECT e.ipAddress, COUNT(e) as failCount FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId AND e.eventType = 'LOGIN_FAILURE' " +
            "AND e.eventTimestamp > :since GROUP BY e.ipAddress HAVING COUNT(e) >= :threshold")
    List<Object[]> findSuspiciousIps(@Param("tenantId") String tenantId,
                                      @Param("since") LocalDateTime since,
                                      @Param("threshold") long threshold);

    /**
     * Delete old audit events (for cleanup).
     */
    void deleteByTenantIdAndEventTimestampBefore(String tenantId, LocalDateTime before);

    /**
     * Find recent events for a session.
     */
    List<LoginAuditEvent> findByTenantIdAndSessionIdOrderByEventTimestampDesc(
            String tenantId, String sessionId);

    // Dashboard Statistics Methods

    /**
     * Count total logins in time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE') " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countTotalLogins(@Param("tenantId") String tenantId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    /**
     * Count successful logins in time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_SUCCESS' AND e.eventTimestamp BETWEEN :start AND :end")
    long countSuccessfulLogins(@Param("tenantId") String tenantId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /**
     * Count failed logins in time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_FAILURE' AND e.eventTimestamp BETWEEN :start AND :end")
    long countFailedLogins(@Param("tenantId") String tenantId,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    /**
     * Get daily login trends.
     */
    @Query(value = "SELECT CAST(event_timestamp AS DATE) as login_date, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS') as successful, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE') as failed " +
            "FROM login_audit_event WHERE tenant_id = :tenantId " +
            "AND event_timestamp BETWEEN :start AND :end " +
            "GROUP BY CAST(event_timestamp AS DATE) ORDER BY login_date", nativeQuery = true)
    List<Object[]> getDailyLoginTrends(@Param("tenantId") String tenantId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * Get failure reasons distribution.
     */
    @Query("SELECT e.errorCode, COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_FAILURE' AND e.eventTimestamp BETWEEN :start AND :end " +
            "AND e.errorCode IS NOT NULL GROUP BY e.errorCode")
    List<Object[]> getFailureReasons(@Param("tenantId") String tenantId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * Count logins across multiple tenants (for hierarchy dashboards).
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId IN :tenantIds " +
            "AND e.eventType = 'LOGIN_SUCCESS' AND e.eventTimestamp BETWEEN :start AND :end")
    long countSuccessfulLoginsAcrossTenants(@Param("tenantIds") List<String> tenantIds,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /**
     * Count MFA events.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'MFA_SUCCESS' AND e.eventTimestamp BETWEEN :start AND :end")
    long countMfaSuccessEvents(@Param("tenantId") String tenantId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /**
     * Count account locked events.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'ACCOUNT_LOCKED' AND e.eventTimestamp BETWEEN :start AND :end")
    long countAccountLockedEvents(@Param("tenantId") String tenantId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    // ==================== Unified Audit (Multi-Service) Methods ====================

    /**
     * Find events by source service.
     */
    Page<LoginAuditEvent> findByTenantIdAndSourceServiceOrderByEventTimestampDesc(
            String tenantId, SourceService sourceService, Pageable pageable);

    /**
     * Find events by source service and event type.
     */
    Page<LoginAuditEvent> findByTenantIdAndSourceServiceAndEventTypeOrderByEventTimestampDesc(
            String tenantId, SourceService sourceService, LoginEventType eventType, Pageable pageable);

    /**
     * Find events by source service within a time range.
     */
    Page<LoginAuditEvent> findByTenantIdAndSourceServiceAndEventTimestampBetweenOrderByEventTimestampDesc(
            String tenantId, SourceService sourceService, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Count events by source service.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.sourceService = :sourceService AND e.eventTimestamp BETWEEN :start AND :end")
    long countEventsBySourceService(@Param("tenantId") String tenantId,
                                     @Param("sourceService") SourceService sourceService,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * Get event counts grouped by source service.
     */
    @Query("SELECT e.sourceService, COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventTimestamp BETWEEN :start AND :end GROUP BY e.sourceService")
    List<Object[]> getEventCountsBySourceService(@Param("tenantId") String tenantId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * Get event counts grouped by source service and event type.
     */
    @Query("SELECT e.sourceService, e.eventType, COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventTimestamp BETWEEN :start AND :end GROUP BY e.sourceService, e.eventType")
    List<Object[]> getEventCountsBySourceServiceAndType(@Param("tenantId") String tenantId,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);

    /**
     * Find IAM user management events.
     */
    @Query("SELECT e FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('USER_CREATED', 'USER_UPDATED', 'USER_DELETED', 'USER_ENABLED', " +
            "'USER_DISABLED', 'USER_EMAIL_VERIFIED', 'USER_PASSWORD_SET', 'USER_ATTRIBUTES_UPDATED') " +
            "AND e.eventTimestamp BETWEEN :start AND :end ORDER BY e.eventTimestamp DESC")
    Page<LoginAuditEvent> findUserManagementEvents(@Param("tenantId") String tenantId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     Pageable pageable);

    /**
     * Find IAM role management events.
     */
    @Query("SELECT e FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_ASSIGNED_TO_USER', " +
            "'ROLE_REMOVED_FROM_USER', 'ROLE_PERMISSIONS_UPDATED', 'ROLE_ASSIGNED_TO_GROUP', 'ROLE_REMOVED_FROM_GROUP') " +
            "AND e.eventTimestamp BETWEEN :start AND :end ORDER BY e.eventTimestamp DESC")
    Page<LoginAuditEvent> findRoleManagementEvents(@Param("tenantId") String tenantId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     Pageable pageable);

    /**
     * Find IAM group management events.
     */
    @Query("SELECT e FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('GROUP_CREATED', 'GROUP_UPDATED', 'GROUP_DELETED', " +
            "'USER_ADDED_TO_GROUP', 'USER_REMOVED_FROM_GROUP') " +
            "AND e.eventTimestamp BETWEEN :start AND :end ORDER BY e.eventTimestamp DESC")
    Page<LoginAuditEvent> findGroupManagementEvents(@Param("tenantId") String tenantId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      Pageable pageable);

    /**
     * Find tenant management events.
     */
    @Query("SELECT e FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('TENANT_CREATED', 'TENANT_UPDATED', 'TENANT_DELETED', " +
            "'TENANT_SUSPENDED', 'TENANT_ACTIVATED', 'TENANT_SETTINGS_UPDATED') " +
            "AND e.eventTimestamp BETWEEN :start AND :end ORDER BY e.eventTimestamp DESC")
    Page<LoginAuditEvent> findTenantManagementEvents(@Param("tenantId") String tenantId,
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end,
                                                       Pageable pageable);

    /**
     * Count user management events.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('USER_CREATED', 'USER_UPDATED', 'USER_DELETED') " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countUserManagementEvents(@Param("tenantId") String tenantId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /**
     * Find events by target user ID (for user activity tracking).
     */
    Page<LoginAuditEvent> findByTenantIdAndUserIdAndSourceServiceOrderByEventTimestampDesc(
            String tenantId, String userId, SourceService sourceService, Pageable pageable);

    // ==================== Platform Admin Methods (All Tenants) ====================

    /**
     * Count total logins across ALL tenants.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventType IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE') " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countTotalLoginsAllTenants(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * Count successful logins across ALL tenants.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventType = 'LOGIN_SUCCESS' AND e.eventTimestamp BETWEEN :start AND :end")
    long countSuccessfulLoginsAllTenants(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Count failed logins across ALL tenants.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventType = 'LOGIN_FAILURE' AND e.eventTimestamp BETWEEN :start AND :end")
    long countFailedLoginsAllTenants(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * Get daily login trends across ALL tenants.
     */
    @Query(value = "SELECT CAST(event_timestamp AS DATE) as login_date, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS') as successful, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE') as failed " +
            "FROM login_audit_event WHERE event_timestamp BETWEEN :start AND :end " +
            "GROUP BY CAST(event_timestamp AS DATE) ORDER BY login_date", nativeQuery = true)
    List<Object[]> getDailyLoginTrendsAllTenants(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * Get logins by hour across ALL tenants.
     */
    @Query(value = "SELECT EXTRACT(HOUR FROM event_timestamp) as hour, COUNT(*) as count " +
            "FROM login_audit_event WHERE event_type = 'LOGIN_SUCCESS' " +
            "AND event_timestamp BETWEEN :start AND :end " +
            "GROUP BY EXTRACT(HOUR FROM event_timestamp) ORDER BY hour", nativeQuery = true)
    List<Object[]> getLoginsByHourAllTenants(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /**
     * Get failure reasons across ALL tenants.
     */
    @Query("SELECT e.errorCode, COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventType = 'LOGIN_FAILURE' AND e.eventTimestamp BETWEEN :start AND :end " +
            "AND e.errorCode IS NOT NULL GROUP BY e.errorCode")
    List<Object[]> getFailureReasonsAllTenants(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    /**
     * Count distinct users who used MFA for a tenant in time range.
     */
    @Query("SELECT COUNT(DISTINCT e.userId) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.mfaUsed = true AND e.eventTimestamp BETWEEN :start AND :end")
    long countDistinctMfaUsers(@Param("tenantId") String tenantId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /**
     * Count distinct users who used MFA across ALL tenants.
     */
    @Query("SELECT COUNT(DISTINCT e.userId) FROM LoginAuditEvent e " +
            "WHERE e.mfaUsed = true AND e.eventTimestamp BETWEEN :start AND :end")
    long countDistinctMfaUsersAllTenants(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Count account locked events across ALL tenants.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventType = 'ACCOUNT_LOCKED' AND e.eventTimestamp BETWEEN :start AND :end")
    long countAccountLockedEventsAllTenants(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /**
     * Find suspicious IPs across ALL tenants.
     */
    @Query("SELECT e.ipAddress, COUNT(e) as failCount FROM LoginAuditEvent e " +
            "WHERE e.eventType = 'LOGIN_FAILURE' AND e.eventTimestamp > :since " +
            "GROUP BY e.ipAddress HAVING COUNT(e) >= :threshold ORDER BY failCount DESC")
    List<Object[]> findSuspiciousIpsAllTenants(@Param("since") LocalDateTime since,
                                                 @Param("threshold") long threshold);

    /**
     * Count security events across ALL tenants.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventType IN ('LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'MFA_FAILURE') " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countSecurityEventsAllTenants(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    // ==================== Tenant Dashboard Stats Methods ====================

    /**
     * Count unique active devices (by deviceInfo) for a tenant in time range.
     */
    @Query("SELECT COUNT(DISTINCT e.deviceInfo) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId AND e.deviceInfo IS NOT NULL " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countActiveDevices(@Param("tenantId") String tenantId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    /**
     * Count devices by type (parsed from userAgent) for a tenant.
     */
    @Query(value = "SELECT " +
            "CASE " +
            "  WHEN LOWER(user_agent) LIKE '%mobile%' OR LOWER(user_agent) LIKE '%android%' OR LOWER(user_agent) LIKE '%iphone%' THEN 'Mobile' " +
            "  WHEN LOWER(user_agent) LIKE '%tablet%' OR LOWER(user_agent) LIKE '%ipad%' THEN 'Tablet' " +
            "  WHEN LOWER(user_agent) LIKE '%windows%' OR LOWER(user_agent) LIKE '%macintosh%' OR LOWER(user_agent) LIKE '%linux%' THEN 'Desktop' " +
            "  ELSE 'Unknown' " +
            "END as device_type, " +
            "COUNT(DISTINCT COALESCE(device_info, ip_address)) as device_count " +
            "FROM login_audit_event " +
            "WHERE tenant_id = :tenantId AND event_timestamp BETWEEN :start AND :end " +
            "GROUP BY device_type", nativeQuery = true)
    List<Object[]> countDevicesByType(@Param("tenantId") String tenantId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * Count total events for a tenant in time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId AND e.eventTimestamp BETWEEN :start AND :end")
    long countTotalEvents(@Param("tenantId") String tenantId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    /**
     * Count security events for a tenant in time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'MFA_FAILURE', 'PASSWORD_RESET_FAILURE', 'SUSPICIOUS_LOGIN') " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countSecurityEvents(@Param("tenantId") String tenantId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /**
     * Get security events grouped by type for a tenant.
     */
    @Query("SELECT e.eventType, COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'MFA_FAILURE', 'PASSWORD_RESET_FAILURE', 'SUSPICIOUS_LOGIN') " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "GROUP BY e.eventType")
    List<Object[]> getSecurityEventsByType(@Param("tenantId") String tenantId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /**
     * Get recent user logins for a tenant (last N users who logged in).
     */
    @Query(value = "SELECT DISTINCT ON (user_id) user_id, username, email, event_timestamp, " +
            "ip_address, location, user_agent, success " +
            "FROM login_audit_event " +
            "WHERE tenant_id = :tenantId AND event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE') " +
            "ORDER BY user_id, event_timestamp DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> findRecentUserLogins(@Param("tenantId") String tenantId,
                                         @Param("limit") int limit);

    /**
     * Get recent devices for a tenant (last N unique devices used).
     */
    @Query(value = "SELECT device_info, user_agent, ip_address, location, " +
            "MAX(event_timestamp) as last_seen, " +
            "(SELECT username FROM login_audit_event e2 " +
            " WHERE e2.device_info = e.device_info AND e2.tenant_id = :tenantId " +
            " ORDER BY e2.event_timestamp DESC LIMIT 1) as last_user, " +
            "COUNT(*) as session_count " +
            "FROM login_audit_event e " +
            "WHERE tenant_id = :tenantId AND device_info IS NOT NULL " +
            "AND event_timestamp >= :since " +
            "GROUP BY device_info, user_agent, ip_address, location " +
            "ORDER BY last_seen DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> findRecentDevices(@Param("tenantId") String tenantId,
                                      @Param("since") LocalDateTime since,
                                      @Param("limit") int limit);

    /**
     * Count total events across ALL tenants in time range (for Platform Admin).
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventTimestamp BETWEEN :start AND :end")
    long countTotalEventsAllTenants(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * Count unique active devices across ALL tenants (for Platform Admin).
     */
    @Query("SELECT COUNT(DISTINCT e.deviceInfo) FROM LoginAuditEvent e " +
            "WHERE e.deviceInfo IS NOT NULL AND e.eventTimestamp BETWEEN :start AND :end")
    long countActiveDevicesAllTenants(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * Count devices by type across ALL tenants (for Platform Admin).
     */
    @Query(value = "SELECT " +
            "CASE " +
            "  WHEN LOWER(user_agent) LIKE '%mobile%' OR LOWER(user_agent) LIKE '%android%' OR LOWER(user_agent) LIKE '%iphone%' THEN 'Mobile' " +
            "  WHEN LOWER(user_agent) LIKE '%tablet%' OR LOWER(user_agent) LIKE '%ipad%' THEN 'Tablet' " +
            "  WHEN LOWER(user_agent) LIKE '%windows%' OR LOWER(user_agent) LIKE '%macintosh%' OR LOWER(user_agent) LIKE '%linux%' THEN 'Desktop' " +
            "  ELSE 'Unknown' " +
            "END as device_type, " +
            "COUNT(DISTINCT COALESCE(device_info, ip_address)) as device_count " +
            "FROM login_audit_event " +
            "WHERE event_timestamp BETWEEN :start AND :end " +
            "GROUP BY device_type", nativeQuery = true)
    List<Object[]> countDevicesByTypeAllTenants(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    /**
     * Get security events grouped by type across ALL tenants (for Platform Admin).
     */
    @Query("SELECT e.eventType, COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.eventType IN ('LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'MFA_FAILURE', 'PASSWORD_RESET_FAILURE', 'SUSPICIOUS_LOGIN') " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "GROUP BY e.eventType")
    List<Object[]> getSecurityEventsByTypeAllTenants(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    /**
     * Get recent user logins across ALL tenants (for Platform Admin).
     */
    @Query(value = "SELECT DISTINCT ON (user_id) user_id, username, email, event_timestamp, " +
            "ip_address, location, user_agent, success " +
            "FROM login_audit_event " +
            "WHERE event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE') " +
            "ORDER BY user_id, event_timestamp DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> findRecentUserLoginsAllTenants(@Param("limit") int limit);

    /**
     * Get recent devices across ALL tenants (for Platform Admin).
     */
    @Query(value = "SELECT device_info, user_agent, ip_address, location, " +
            "MAX(event_timestamp) as last_seen, " +
            "(SELECT username FROM login_audit_event e2 " +
            " WHERE e2.device_info = e.device_info " +
            " ORDER BY e2.event_timestamp DESC LIMIT 1) as last_user, " +
            "COUNT(*) as session_count " +
            "FROM login_audit_event e " +
            "WHERE device_info IS NOT NULL " +
            "AND event_timestamp >= :since " +
            "GROUP BY device_info, user_agent, ip_address, location " +
            "ORDER BY last_seen DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> findRecentDevicesAllTenants(@Param("since") LocalDateTime since,
                                                @Param("limit") int limit);

    // ==================== CONSOLIDATED COUNT QUERIES ====================

    /**
     * Consolidated login count query for a tenant - replaces 9 individual count queries with 1.
     * Returns a single row with:
     *   logins_today, success_today, failed_today,
     *   logins_week, success_week, failed_week,
     *   logins_month, success_month, failed_month
     */
    @Query(value = "SELECT " +
            "COUNT(*) FILTER (WHERE event_type IN ('LOGIN_SUCCESS','LOGIN_FAILURE') AND event_timestamp >= :startOfDay) as logins_today, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS' AND event_timestamp >= :startOfDay) as success_today, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE' AND event_timestamp >= :startOfDay) as failed_today, " +
            "COUNT(*) FILTER (WHERE event_type IN ('LOGIN_SUCCESS','LOGIN_FAILURE') AND event_timestamp >= :startOfWeek) as logins_week, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS' AND event_timestamp >= :startOfWeek) as success_week, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE' AND event_timestamp >= :startOfWeek) as failed_week, " +
            "COUNT(*) FILTER (WHERE event_type IN ('LOGIN_SUCCESS','LOGIN_FAILURE')) as logins_month, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS') as success_month, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE') as failed_month " +
            "FROM login_audit_event WHERE tenant_id = :tenantId " +
            "AND event_timestamp BETWEEN :startOfMonth AND :now",
            nativeQuery = true)
    List<Object[]> getConsolidatedLoginCounts(@Param("tenantId") String tenantId,
                                               @Param("startOfDay") LocalDateTime startOfDay,
                                               @Param("startOfWeek") LocalDateTime startOfWeek,
                                               @Param("startOfMonth") LocalDateTime startOfMonth,
                                               @Param("now") LocalDateTime now);

    /**
     * Consolidated login count query across ALL tenants - replaces 9 individual count queries with 1.
     */
    @Query(value = "SELECT " +
            "COUNT(*) FILTER (WHERE event_type IN ('LOGIN_SUCCESS','LOGIN_FAILURE') AND event_timestamp >= :startOfDay) as logins_today, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS' AND event_timestamp >= :startOfDay) as success_today, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE' AND event_timestamp >= :startOfDay) as failed_today, " +
            "COUNT(*) FILTER (WHERE event_type IN ('LOGIN_SUCCESS','LOGIN_FAILURE') AND event_timestamp >= :startOfWeek) as logins_week, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS' AND event_timestamp >= :startOfWeek) as success_week, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE' AND event_timestamp >= :startOfWeek) as failed_week, " +
            "COUNT(*) FILTER (WHERE event_type IN ('LOGIN_SUCCESS','LOGIN_FAILURE')) as logins_month, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS') as success_month, " +
            "COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE') as failed_month " +
            "FROM login_audit_event WHERE event_timestamp BETWEEN :startOfMonth AND :now",
            nativeQuery = true)
    List<Object[]> getConsolidatedLoginCountsAllTenants(@Param("startOfDay") LocalDateTime startOfDay,
                                                          @Param("startOfWeek") LocalDateTime startOfWeek,
                                                          @Param("startOfMonth") LocalDateTime startOfMonth,
                                                          @Param("now") LocalDateTime now);
}
