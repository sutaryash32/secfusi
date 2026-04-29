package com.secufusion.iam.repository;

import com.secufusion.iam.entity.LoginAuditEvent;
import com.secufusion.iam.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.iam.entity.LoginAuditEvent.SourceService;
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
     * Delete old audit events (for cleanup).
     */
    void deleteByTenantIdAndEventTimestampBefore(String tenantId, LocalDateTime before);

    /**
     * Find recent events for a session.
     */
    List<LoginAuditEvent> findByTenantIdAndSessionIdOrderByEventTimestampDesc(
            String tenantId, String sessionId);

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
     * Count user management events.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('USER_CREATED', 'USER_UPDATED', 'USER_DELETED') " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countUserManagementEvents(@Param("tenantId") String tenantId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /**
     * Find events by target user ID and source service.
     */
    Page<LoginAuditEvent> findByTenantIdAndUserIdAndSourceServiceOrderByEventTimestampDesc(
            String tenantId, String userId, SourceService sourceService, Pageable pageable);

    // ==================== Device-Based Queries ====================

    /**
     * Find login events by device ID.
     */
    Page<LoginAuditEvent> findByTenantIdAndDeviceIdOrderByEventTimestampDesc(
            String tenantId, String deviceId, Pageable pageable);

    /**
     * Find login events by device fingerprint.
     */
    Page<LoginAuditEvent> findByTenantIdAndDeviceFingerprintOrderByEventTimestampDesc(
            String tenantId, String deviceFingerprint, Pageable pageable);

    /**
     * Find login events for a user on a specific device.
     */
    Page<LoginAuditEvent> findByTenantIdAndUserIdAndDeviceIdOrderByEventTimestampDesc(
            String tenantId, String userId, String deviceId, Pageable pageable);

    /**
     * Find login events for a user on a specific device fingerprint.
     */
    Page<LoginAuditEvent> findByTenantIdAndUserIdAndDeviceFingerprintOrderByEventTimestampDesc(
            String tenantId, String userId, String deviceFingerprint, Pageable pageable);

    /**
     * Count unique devices used for login by a user.
     */
    @Query("SELECT COUNT(DISTINCT e.deviceFingerprint) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId AND e.userId = :userId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countUniqueDevicesByUser(@Param("tenantId") String tenantId,
                                   @Param("userId") String userId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * Count unique devices used for login across tenant.
     */
    @Query("SELECT COUNT(DISTINCT e.deviceFingerprint) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countUniqueDevices(@Param("tenantId") String tenantId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /**
     * Find new device logins (first login from a device).
     */
    @Query("SELECT e FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.isNewDevice = true " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "ORDER BY e.eventTimestamp DESC")
    Page<LoginAuditEvent> findNewDeviceLogins(@Param("tenantId") String tenantId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               Pageable pageable);

    /**
     * Get device login statistics - count by device type.
     */
    @Query("SELECT e.deviceInfo, COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "AND e.deviceInfo IS NOT NULL " +
            "GROUP BY e.deviceInfo")
    List<Object[]> getLoginCountsByDeviceType(@Param("tenantId") String tenantId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /**
     * Get login count by browser type.
     */
    @Query("SELECT e.browserType, COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "AND e.browserType IS NOT NULL " +
            "GROUP BY e.browserType")
    List<Object[]> getLoginCountsByBrowser(@Param("tenantId") String tenantId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /**
     * Get login count by OS.
     */
    @Query("SELECT e.osInfo, COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "AND e.osInfo IS NOT NULL " +
            "GROUP BY e.osInfo")
    List<Object[]> getLoginCountsByOS(@Param("tenantId") String tenantId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * Find login failures by device.
     */
    @Query("SELECT e FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.deviceId = :deviceId " +
            "AND e.eventType = 'LOGIN_FAILURE' " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "ORDER BY e.eventTimestamp DESC")
    List<LoginAuditEvent> findLoginFailuresByDevice(@Param("tenantId") String tenantId,
                                                     @Param("deviceId") String deviceId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    /**
     * Count login failures by device.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.deviceId = :deviceId " +
            "AND e.eventType = 'LOGIN_FAILURE' " +
            "AND e.eventTimestamp > :since")
    long countLoginFailuresByDevice(@Param("tenantId") String tenantId,
                                     @Param("deviceId") String deviceId,
                                     @Param("since") LocalDateTime since);

    /**
     * Get devices with most login failures (suspicious activity).
     */
    @Query("SELECT e.deviceFingerprint, e.deviceName, COUNT(e) as failureCount FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_FAILURE' " +
            "AND e.eventTimestamp BETWEEN :start AND :end " +
            "AND e.deviceFingerprint IS NOT NULL " +
            "GROUP BY e.deviceFingerprint, e.deviceName " +
            "ORDER BY failureCount DESC")
    List<Object[]> getDevicesWithMostLoginFailures(@Param("tenantId") String tenantId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    Pageable pageable);

    /**
     * Get last login event for a device.
     */
    @Query("SELECT e FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.deviceId = :deviceId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "ORDER BY e.eventTimestamp DESC " +
            "LIMIT 1")
    LoginAuditEvent findLastLoginByDevice(@Param("tenantId") String tenantId,
                                           @Param("deviceId") String deviceId);

    /**
     * Get last login event for a user on any device.
     */
    @Query("SELECT e FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.userId = :userId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "ORDER BY e.eventTimestamp DESC " +
            "LIMIT 1")
    LoginAuditEvent findLastLoginByUser(@Param("tenantId") String tenantId,
                                         @Param("userId") String userId);

    /**
     * Get distinct devices used by a user.
     */
    @Query("SELECT DISTINCT e.deviceFingerprint, e.deviceName, e.browserType, e.osInfo, " +
            "MAX(e.eventTimestamp) as lastUsed, COUNT(e) as loginCount " +
            "FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.userId = :userId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.deviceFingerprint IS NOT NULL " +
            "GROUP BY e.deviceFingerprint, e.deviceName, e.browserType, e.osInfo")
    List<Object[]> getDevicesUsedByUser(@Param("tenantId") String tenantId,
                                         @Param("userId") String userId);

    /**
     * Daily login activity by device.
     */
    @Query(value = """
            SELECT DATE(event_timestamp) as login_date,
                   COUNT(*) as total_logins,
                   COUNT(CASE WHEN success = true THEN 1 END) as successful_logins,
                   COUNT(CASE WHEN success = false THEN 1 END) as failed_logins,
                   COUNT(DISTINCT device_fingerprint) as unique_devices
            FROM login_audit_event
            WHERE tenant_id = :tenantId
            AND event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE')
            AND event_timestamp BETWEEN :start AND :end
            GROUP BY DATE(event_timestamp)
            ORDER BY login_date ASC
            """, nativeQuery = true)
    List<Object[]> getDailyLoginActivityWithDevices(@Param("tenantId") String tenantId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    /**
     * Count total login events in a time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE') " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countLoginsByTimeRange(@Param("tenantId") String tenantId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * Count successful logins in a time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countSuccessfulLoginsByTimeRange(@Param("tenantId") String tenantId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    /**
     * Count new device logins in a time range.
     */
    @Query("SELECT COUNT(e) FROM LoginAuditEvent e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.isNewDevice = true " +
            "AND e.eventType = 'LOGIN_SUCCESS' " +
            "AND e.eventTimestamp BETWEEN :start AND :end")
    long countNewDeviceLogins(@Param("tenantId") String tenantId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);
}
