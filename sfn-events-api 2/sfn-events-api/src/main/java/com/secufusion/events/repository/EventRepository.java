package com.secufusion.events.repository;

import com.secufusion.events.entity.Event;
import com.secufusion.events.entity.EventType;
import com.secufusion.events.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.secufusion.events.entity.FileOperationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    // Existing method
    Optional<List<Event>> findByTenant(Tenant tenant);

    // By Tenant with pagination
    Page<Event> findByTenant_TenantIDOrderByTimeStampDesc(String tenantId, Pageable pageable);

    // By Device
    Page<Event> findByDevice_DeviceIdOrderByTimeStampDesc(String deviceId, Pageable pageable);

    List<Event> findByDevice_DeviceIdAndTimeStampBetweenOrderByTimeStampDesc(
            String deviceId, LocalDateTime start, LocalDateTime end);

    // By User
    Page<Event> findByTenant_TenantIDAndUserNameOrderByTimeStampDesc(
            String tenantId, String userName, Pageable pageable);

    // By Event Type
    Page<Event> findByTenant_TenantIDAndEventTypeOrderByTimeStampDesc(
            String tenantId, EventType eventType, Pageable pageable);

    // Time-based queries
    List<Event> findByTenant_TenantIDAndTimeStampBetweenOrderByTimeStampDesc(
            String tenantId, LocalDateTime start, LocalDateTime end);

    // ==================== COUNT QUERIES ====================

    long countByTenant_TenantID(String tenantId);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countEventsByTenantAndTimeRange(@Param("tenantId") String tenantId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.device.deviceId = :deviceId " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countEventsByDeviceAndTimeRange(@Param("deviceId") String deviceId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    // ==================== EVENT TYPE DISTRIBUTION ====================

    @Query("SELECT e.eventType, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.eventType")
    List<Object[]> countEventsByType(@Param("tenantId") String tenantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query("SELECT e.eventType, COUNT(e) FROM Event e " +
            "WHERE e.device.deviceId = :deviceId " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.eventType")
    List<Object[]> countEventsByTypeForDevice(@Param("deviceId") String deviceId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    // ==================== DOMAIN ANALYTICS ====================

    @Query("SELECT e.domain, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.eventType = 'WEBSITE_VISIT' " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "AND e.domain IS NOT NULL " +
            "GROUP BY e.domain " +
            "ORDER BY COUNT(e) DESC")
    List<Object[]> getTopDomains(@Param("tenantId") String tenantId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 Pageable pageable);

    @Query("SELECT e.domain, COUNT(e) FROM Event e " +
            "WHERE e.device.deviceId = :deviceId " +
            "AND e.eventType = 'WEBSITE_VISIT' " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "AND e.domain IS NOT NULL " +
            "GROUP BY e.domain " +
            "ORDER BY COUNT(e) DESC")
    List<Object[]> getTopDomainsForDevice(@Param("deviceId") String deviceId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          Pageable pageable);

    @Query("SELECT COUNT(DISTINCT e.domain) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.eventType = 'WEBSITE_VISIT' " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countUniqueDomains(@Param("tenantId") String tenantId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT e.domain) FROM Event e " +
            "WHERE e.device.deviceId = :deviceId " +
            "AND e.eventType = 'WEBSITE_VISIT' " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countUniqueDomainsForDevice(@Param("deviceId") String deviceId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    // ==================== POLICY VIOLATIONS ====================

    @Query("SELECT COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isPolicyViolation = true " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countPolicyViolations(@Param("tenantId") String tenantId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(e) FROM Event e " +
            "WHERE e.device.deviceId = :deviceId " +
            "AND e.isPolicyViolation = true " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countPolicyViolationsForDevice(@Param("deviceId") String deviceId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    @Query("SELECT e.eventType, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isPolicyViolation = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.eventType")
    List<Object[]> countViolationsByType(@Param("tenantId") String tenantId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    // ==================== USER ANALYTICS ====================

    @Query("SELECT COUNT(DISTINCT e.userName) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countActiveUsers(@Param("tenantId") String tenantId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    @Query("SELECT e.userName, COUNT(e) as eventCount, MAX(e.timeStamp) as lastActivity " +
            "FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.userName " +
            "ORDER BY eventCount DESC")
    List<Object[]> getTopActiveUsers(@Param("tenantId") String tenantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     Pageable pageable);

    // ==================== RECENT EVENTS ====================

    @Query(value = """
            SELECT pk_event_id, event_type, url, title, user_name, fk_device_id, time_stamp
            FROM events
            WHERE fk_tenant_id = :tenantId
            ORDER BY time_stamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findRecentEvents(@Param("tenantId") String tenantId,
                                    @Param("limit") int limit);

    @Query(value = """
            SELECT pk_event_id, event_type, url, title, user_name, fk_device_id, time_stamp
            FROM events
            WHERE fk_device_id = :deviceId
            ORDER BY time_stamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findRecentEventsForDevice(@Param("deviceId") String deviceId,
                                             @Param("limit") int limit);

    // ==================== DURATION AGGREGATION ====================

    @Query("SELECT COALESCE(SUM(e.durationSeconds), 0) FROM Event e " +
            "WHERE e.device.deviceId = :deviceId " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long sumDurationByDevice(@Param("deviceId") String deviceId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(e.durationSeconds), 0) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long sumDurationByTenant(@Param("tenantId") String tenantId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    // ==================== CATEGORY ANALYTICS ====================

    @Query("SELECT e.category, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "AND e.category IS NOT NULL " +
            "GROUP BY e.category " +
            "ORDER BY COUNT(e) DESC")
    List<Object[]> countEventsByCategory(@Param("tenantId") String tenantId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    // ==================== FILE OPERATIONS ANALYTICS ====================

    @Query("SELECT COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.fileOperationType = :operationType " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countFileOperations(@Param("tenantId") String tenantId,
                             @Param("operationType") FileOperationType operationType,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.fileOperationType = :operationType " +
            "AND e.isBlocked = true " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countBlockedFileOperations(@Param("tenantId") String tenantId,
                                    @Param("operationType") FileOperationType operationType,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Query("SELECT e.fileOperationType, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.fileOperationType IS NOT NULL " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.fileOperationType")
    List<Object[]> countFileOperationsByType(@Param("tenantId") String tenantId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Query("SELECT e.fileOperationType, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.fileOperationType IS NOT NULL " +
            "AND e.isBlocked = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.fileOperationType")
    List<Object[]> countBlockedFileOperationsByType(@Param("tenantId") String tenantId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    // ==================== DAILY TRENDS ====================

    @Query(value = """
            SELECT DATE(time_stamp) as event_date,
                   COUNT(*) as total_events,
                   COUNT(DISTINCT fk_device_id) as active_devices
            FROM events
            WHERE fk_tenant_id = :tenantId
            AND time_stamp BETWEEN :start AND :end
            GROUP BY DATE(time_stamp)
            ORDER BY event_date ASC
            """, nativeQuery = true)
    List<Object[]> getDailyEventTrends(@Param("tenantId") String tenantId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT DATE(time_stamp) as event_date,
                   COUNT(CASE WHEN file_operation_type = 'DOWNLOAD' THEN 1 END) as downloads,
                   COUNT(CASE WHEN file_operation_type = 'UPLOAD' THEN 1 END) as uploads,
                   COUNT(CASE WHEN is_blocked = true THEN 1 END) as violations
            FROM events
            WHERE fk_tenant_id = :tenantId
            AND time_stamp BETWEEN :start AND :end
            GROUP BY DATE(time_stamp)
            ORDER BY event_date ASC
            """, nativeQuery = true)
    List<Object[]> getDailyFileOperationTrends(@Param("tenantId") String tenantId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    // ==================== DOMAIN ANALYTICS WITH TOTAL ====================

    @Query("SELECT SUM(1) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.eventType = 'WEBSITE_VISIT' " +
            "AND e.timeStamp BETWEEN :start AND :end")
    Long countTotalDomainVisits(@Param("tenantId") String tenantId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("SELECT e.domain, e.category, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.eventType = 'WEBSITE_VISIT' " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "AND e.domain IS NOT NULL " +
            "GROUP BY e.domain, e.category " +
            "ORDER BY COUNT(e) DESC")
    List<Object[]> getTopDomainsWithCategory(@Param("tenantId") String tenantId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             Pageable pageable);

    // ==================== SECURITY EVENT QUERIES ====================

    /**
     * Get all security events for a tenant with pagination.
     */
    Page<Event> findByTenant_TenantIDAndIsSecurityEventTrueOrderByTimeStampDesc(
            String tenantId, Pageable pageable);

    /**
     * Get security events by severity level.
     */
    Page<Event> findByTenant_TenantIDAndIsSecurityEventTrueAndSeverityOrderByTimeStampDesc(
            String tenantId, String severity, Pageable pageable);

    /**
     * Get security events by threat type.
     */
    Page<Event> findByTenant_TenantIDAndIsSecurityEventTrueAndThreatTypeOrderByTimeStampDesc(
            String tenantId, String threatType, Pageable pageable);

    /**
     * Get security events by risk level.
     */
    Page<Event> findByTenant_TenantIDAndIsSecurityEventTrueAndRiskLevelOrderByTimeStampDesc(
            String tenantId, String riskLevel, Pageable pageable);

    /**
     * Get security events within a time range.
     */
    List<Event> findByTenant_TenantIDAndIsSecurityEventTrueAndTimeStampBetweenOrderByTimeStampDesc(
            String tenantId, LocalDateTime start, LocalDateTime end);

    /**
     * Count total security events for a tenant.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId AND e.isSecurityEvent = true")
    long countSecurityEvents(@Param("tenantId") String tenantId);

    /**
     * Count security events within a time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countSecurityEventsByTimeRange(@Param("tenantId") String tenantId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * Count security events by severity.
     */
    @Query("SELECT e.severity, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.severity")
    List<Object[]> countSecurityEventsBySeverity(@Param("tenantId") String tenantId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    /**
     * Count security events by threat type.
     */
    @Query("SELECT e.threatType, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.threatType")
    List<Object[]> countSecurityEventsByThreatType(@Param("tenantId") String tenantId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    /**
     * Count security events by risk level.
     */
    @Query("SELECT e.riskLevel, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.riskLevel")
    List<Object[]> countSecurityEventsByRiskLevel(@Param("tenantId") String tenantId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * Count security events by action taken (blocked, allowed, warned).
     */
    @Query("SELECT e.actionTaken, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.actionTaken")
    List<Object[]> countSecurityEventsByActionTaken(@Param("tenantId") String tenantId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    /**
     * Count security events by policy type.
     */
    @Query("SELECT e.policyType, COUNT(e) FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.policyType")
    List<Object[]> countSecurityEventsByPolicyType(@Param("tenantId") String tenantId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    /**
     * Get recent security events (native query for performance).
     */
    @Query(value = """
            SELECT pk_event_id, event_type, url, title, user_name, fk_device_id, time_stamp,
                   severity, threat_type, threat_level, action_taken, risk_level,
                   policy_name, policy_type, matched_pattern
            FROM events
            WHERE fk_tenant_id = :tenantId
            AND is_security_event = true
            ORDER BY time_stamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findRecentSecurityEvents(@Param("tenantId") String tenantId,
                                            @Param("limit") int limit);

    /**
     * Get critical security events (severity = 'critical' or risk_level = 'Critical').
     */
    @Query("SELECT e FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (e.severity = 'critical' OR e.riskLevel = 'Critical') " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    List<Event> findCriticalSecurityEvents(@Param("tenantId") String tenantId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    /**
     * Daily security event trends.
     */
    @Query(value = """
            SELECT DATE(time_stamp) as event_date,
                   COUNT(*) as total_security_events,
                   COUNT(CASE WHEN severity = 'critical' THEN 1 END) as critical_count,
                   COUNT(CASE WHEN severity = 'high' THEN 1 END) as high_count,
                   COUNT(CASE WHEN severity = 'medium' THEN 1 END) as medium_count,
                   COUNT(CASE WHEN severity = 'low' THEN 1 END) as low_count
            FROM events
            WHERE fk_tenant_id = :tenantId
            AND is_security_event = true
            AND time_stamp BETWEEN :start AND :end
            GROUP BY DATE(time_stamp)
            ORDER BY event_date ASC
            """, nativeQuery = true)
    List<Object[]> getDailySecurityEventTrends(@Param("tenantId") String tenantId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /**
     * Get security events by user.
     */
    Page<Event> findByTenant_TenantIDAndIsSecurityEventTrueAndUserNameOrderByTimeStampDesc(
            String tenantId, String userName, Pageable pageable);

    /**
     * Get security events by device.
     */
    Page<Event> findByDevice_DeviceIdAndIsSecurityEventTrueOrderByTimeStampDesc(
            String deviceId, Pageable pageable);

    /**
     * Top users with security events.
     */
    @Query("SELECT e.userName, COUNT(e) as eventCount FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.userName " +
            "ORDER BY eventCount DESC")
    List<Object[]> getTopUsersWithSecurityEvents(@Param("tenantId") String tenantId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
                                                  Pageable pageable);

    /**
     * Top threat types.
     */
    @Query("SELECT e.threatType, COUNT(e) as eventCount FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.threatType IS NOT NULL " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY e.threatType " +
            "ORDER BY eventCount DESC")
    List<Object[]> getTopThreatTypes(@Param("tenantId") String tenantId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      Pageable pageable);

    /**
     * Get security events with filters.
     */
    @Query("SELECT e FROM Event e " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (:severity IS NULL OR e.severity = :severity) " +
            "AND (:threatType IS NULL OR e.threatType = :threatType) " +
            "AND (:riskLevel IS NULL OR e.riskLevel = :riskLevel) " +
            "AND (:actionTaken IS NULL OR e.actionTaken = :actionTaken) " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    Page<Event> findSecurityEventsWithFilters(@Param("tenantId") String tenantId,
                                              @Param("severity") String severity,
                                              @Param("threatType") String threatType,
                                              @Param("riskLevel") String riskLevel,
                                              @Param("actionTaken") String actionTaken,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              Pageable pageable);

    // ==================== ADVANCED EVENT QUERIES ====================

    /**
     * Get all events for a tenant with pagination and time range filter.
     * Using native query to handle PostgreSQL parameter type inference.
     */
    @Query(value = """
            SELECT * FROM events e WHERE e.fk_tenant_id = :tenantId
            AND (CAST(:start AS TIMESTAMP) IS NULL OR e.time_stamp >= CAST(:start AS TIMESTAMP))
            AND (CAST(:end AS TIMESTAMP) IS NULL OR e.time_stamp <= CAST(:end AS TIMESTAMP))
            ORDER BY e.time_stamp DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM events e WHERE e.fk_tenant_id = :tenantId
            AND (CAST(:start AS TIMESTAMP) IS NULL OR e.time_stamp >= CAST(:start AS TIMESTAMP))
            AND (CAST(:end AS TIMESTAMP) IS NULL OR e.time_stamp <= CAST(:end AS TIMESTAMP))
            """,
            nativeQuery = true)
    Page<Event> findAllEventsWithTimeRange(@Param("tenantId") String tenantId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           Pageable pageable);

    /**
     * Search events by URL, title, or domain with pagination.
     */
    @Query(value = """
            SELECT * FROM events e WHERE e.fk_tenant_id = :tenantId
            AND (LOWER(e.url) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(e.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(e.domain) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            ORDER BY e.time_stamp DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM events e WHERE e.fk_tenant_id = :tenantId
            AND (LOWER(e.url) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(e.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(e.domain) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """,
            nativeQuery = true)
    Page<Event> searchEvents(@Param("tenantId") String tenantId,
                             @Param("searchTerm") String searchTerm,
                             Pageable pageable);

    /**
     * Advanced filter with multiple optional criteria.
     * Using native query to handle PostgreSQL parameter type inference.
     */
    @Query(value = """
            SELECT * FROM events e WHERE e.fk_tenant_id = :tenantId
            AND (CAST(:eventType AS VARCHAR) IS NULL OR e.event_type = CAST(:eventType AS VARCHAR))
            AND (CAST(:userName AS VARCHAR) IS NULL OR e.user_name = CAST(:userName AS VARCHAR))
            AND (CAST(:deviceId AS VARCHAR) IS NULL OR e.fk_device_id = CAST(:deviceId AS VARCHAR))
            AND (CAST(:isPolicyViolation AS BOOLEAN) IS NULL OR e.is_policy_violation = CAST(:isPolicyViolation AS BOOLEAN))
            AND (CAST(:isSecurityEvent AS BOOLEAN) IS NULL OR e.is_security_event = CAST(:isSecurityEvent AS BOOLEAN))
            AND (CAST(:isBlocked AS BOOLEAN) IS NULL OR e.is_blocked = CAST(:isBlocked AS BOOLEAN))
            AND (CAST(:fileOperationType AS VARCHAR) IS NULL OR e.file_operation_type = CAST(:fileOperationType AS VARCHAR))
            AND (CAST(:category AS VARCHAR) IS NULL OR e.category = CAST(:category AS VARCHAR))
            AND (CAST(:start AS TIMESTAMP) IS NULL OR e.time_stamp >= CAST(:start AS TIMESTAMP))
            AND (CAST(:end AS TIMESTAMP) IS NULL OR e.time_stamp <= CAST(:end AS TIMESTAMP))
            ORDER BY e.time_stamp DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM events e WHERE e.fk_tenant_id = :tenantId
            AND (CAST(:eventType AS VARCHAR) IS NULL OR e.event_type = CAST(:eventType AS VARCHAR))
            AND (CAST(:userName AS VARCHAR) IS NULL OR e.user_name = CAST(:userName AS VARCHAR))
            AND (CAST(:deviceId AS VARCHAR) IS NULL OR e.fk_device_id = CAST(:deviceId AS VARCHAR))
            AND (CAST(:isPolicyViolation AS BOOLEAN) IS NULL OR e.is_policy_violation = CAST(:isPolicyViolation AS BOOLEAN))
            AND (CAST(:isSecurityEvent AS BOOLEAN) IS NULL OR e.is_security_event = CAST(:isSecurityEvent AS BOOLEAN))
            AND (CAST(:isBlocked AS BOOLEAN) IS NULL OR e.is_blocked = CAST(:isBlocked AS BOOLEAN))
            AND (CAST(:fileOperationType AS VARCHAR) IS NULL OR e.file_operation_type = CAST(:fileOperationType AS VARCHAR))
            AND (CAST(:category AS VARCHAR) IS NULL OR e.category = CAST(:category AS VARCHAR))
            AND (CAST(:start AS TIMESTAMP) IS NULL OR e.time_stamp >= CAST(:start AS TIMESTAMP))
            AND (CAST(:end AS TIMESTAMP) IS NULL OR e.time_stamp <= CAST(:end AS TIMESTAMP))
            """,
            nativeQuery = true)
    Page<Event> findEventsWithFilters(@Param("tenantId") String tenantId,
                                      @Param("eventType") String eventType,
                                      @Param("userName") String userName,
                                      @Param("deviceId") String deviceId,
                                      @Param("isPolicyViolation") Boolean isPolicyViolation,
                                      @Param("isSecurityEvent") Boolean isSecurityEvent,
                                      @Param("isBlocked") Boolean isBlocked,
                                      @Param("fileOperationType") String fileOperationType,
                                      @Param("category") String category,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      Pageable pageable);

    /**
     * Get distinct categories for a tenant.
     */
    @Query("SELECT DISTINCT e.category FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.category IS NOT NULL ORDER BY e.category")
    List<String> findDistinctCategories(@Param("tenantId") String tenantId);

    /**
     * Get distinct users for a tenant.
     */
    @Query("SELECT DISTINCT e.userName FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.userName IS NOT NULL ORDER BY e.userName")
    List<String> findDistinctUserNames(@Param("tenantId") String tenantId);

    // ==================== DASHBOARD QUERIES ====================

    /**
     * Find event by ID and tenant ID.
     */
    @Query("SELECT e FROM Event e WHERE e.pkEventId = :eventId AND e.tenant.tenantID = :tenantId")
    Optional<Event> findByPkEventIdAndTenant_TenantID(@Param("eventId") String eventId,
                                                       @Param("tenantId") String tenantId);

    /**
     * Batch fetch events by a list of IDs scoped to a tenant.
     * Used to replace the per-event SELECT in linkEventsInternal, eliminating the N+1 query problem.
     */
    @Query("SELECT e FROM Event e WHERE e.pkEventId IN :eventIds AND e.tenant.tenantID = :tenantId")
    List<Event> findAllByPkEventIdInAndTenant_TenantID(@Param("eventIds") List<String> eventIds,
                                                        @Param("tenantId") String tenantId);

    /**
     * Get distinct device names for a tenant.
     */
    @Query("SELECT DISTINCT d.deviceName FROM Event e JOIN e.device d " +
            "WHERE e.tenant.tenantID = :tenantId AND d.deviceName IS NOT NULL ORDER BY d.deviceName")
    List<String> findDistinctDeviceNamesByTenantId(@Param("tenantId") String tenantId);

    /**
     * Get distinct user names for a tenant (alias for consistency).
     */
    @Query("SELECT DISTINCT e.userName FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.userName IS NOT NULL ORDER BY e.userName")
    List<String> findDistinctUserNamesByTenantId(@Param("tenantId") String tenantId);

    /**
     * Count security events by severity and time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true AND e.severity = :severity " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countByTenantIdAndSeverityAndTimeRange(@Param("tenantId") String tenantId,
                                                 @Param("severity") String severity,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    /**
     * Count security events by multiple severities and time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true AND e.severity IN :severities " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countByTenantIdAndSeverityInAndTimeRange(@Param("tenantId") String tenantId,
                                                   @Param("severities") List<String> severities,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * Count policy violations in time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isPolicyViolation = true " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countPolicyViolationsByTimeRange(@Param("tenantId") String tenantId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Count DLP alerts in time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (LOWER(e.threatType) LIKE '%dlp%' OR LOWER(e.policyType) LIKE '%dlp%' " +
            "OR (e.isBlocked = true AND e.fileOperationType IS NOT NULL)) " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countDlpAlertsByTimeRange(@Param("tenantId") String tenantId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * Count compliance events in time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true AND e.complianceImpact IS NOT NULL " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countComplianceEventsByTimeRange(@Param("tenantId") String tenantId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Dashboard event type breakdown in a single query (replaces 4 individual counts).
     * Returns rows: [type_label, count]
     */
    @Query("SELECT " +
            "CASE " +
            "  WHEN e.severity IN ('critical', 'high') THEN 'SECURITY_THREAT' " +
            "  WHEN e.isPolicyViolation = true THEN 'POLICY_VIOLATION' " +
            "  WHEN (LOWER(e.threatType) LIKE '%dlp%' OR LOWER(e.policyType) LIKE '%dlp%' " +
            "        OR (e.isBlocked = true AND e.fileOperationType IS NOT NULL)) THEN 'DLP_ALERT' " +
            "  WHEN e.complianceImpact IS NOT NULL THEN 'COMPLIANCE' " +
            "  ELSE 'OTHER' " +
            "END AS eventType, COUNT(e) " +
            "FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "GROUP BY eventType")
    List<Object[]> countEventTypeBreakdownByTimeRange(@Param("tenantId") String tenantId,
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    /**
     * Search security events by term.
     */
    @Query("SELECT e FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (LOWER(e.url) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(e.domain) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(e.userName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(e.threatType) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    Page<Event> searchSecurityEvents(@Param("tenantId") String tenantId,
                                     @Param("searchTerm") String searchTerm,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     Pageable pageable);

    /**
     * Find security threats (severity = critical or high).
     */
    @Query("SELECT e FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.severity IN ('critical', 'high') " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    Page<Event> findSecurityThreats(@Param("tenantId") String tenantId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    Pageable pageable);

    /**
     * Find policy violations.
     */
    @Query("SELECT e FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isPolicyViolation = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    Page<Event> findPolicyViolations(@Param("tenantId") String tenantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     Pageable pageable);

    /**
     * Find DLP alerts.
     */
    @Query("SELECT e FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (LOWER(e.threatType) LIKE '%dlp%' OR LOWER(e.policyType) LIKE '%dlp%' " +
            "OR (e.isBlocked = true AND e.fileOperationType IS NOT NULL)) " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    Page<Event> findDlpAlerts(@Param("tenantId") String tenantId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              Pageable pageable);

    /**
     * Find compliance events.
     */
    @Query("SELECT e FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true AND e.complianceImpact IS NOT NULL " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    Page<Event> findComplianceEvents(@Param("tenantId") String tenantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     Pageable pageable);

    /**
     * Find security events by time range with pagination.
     */
    @Query("SELECT e FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end " +
            "ORDER BY e.timeStamp DESC")
    Page<Event> findSecurityEventsByTimeRange(@Param("tenantId") String tenantId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              Pageable pageable);

    // ==================== USER-SPECIFIC QUERIES ====================

    /**
     * Count events by user name.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId AND e.userName = :userName")
    long countByTenantIdAndUserName(@Param("tenantId") String tenantId, @Param("userName") String userName);

    /**
     * Count security events by user name.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.userName = :userName AND e.isSecurityEvent = true")
    long countSecurityEventsByUserName(@Param("tenantId") String tenantId, @Param("userName") String userName);

    /**
     * Count policy violations by user name.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.userName = :userName AND e.isPolicyViolation = true")
    long countPolicyViolationsByUserName(@Param("tenantId") String tenantId, @Param("userName") String userName);

    /**
     * Get last event timestamp by user name.
     */
    @Query("SELECT MAX(e.timeStamp) FROM Event e WHERE e.tenant.tenantID = :tenantId AND e.userName = :userName")
    LocalDateTime findLastEventTimeByUserName(@Param("tenantId") String tenantId, @Param("userName") String userName);

    // ==================== DEVICE USER QUERIES ====================

    /**
     * Find events by device user ID with pagination.
     */
    Page<Event> findByTenant_TenantIDAndDeviceUser_PkDeviceUserIdOrderByTimeStampDesc(
            String tenantId, String deviceUserId, Pageable pageable);

    /**
     * Count events for a device user.
     */
    long countByTenant_TenantIDAndDeviceUser_PkDeviceUserId(String tenantId, String deviceUserId);

    /**
     * Count security events for a device user.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId AND e.isSecurityEvent = true")
    long countSecurityEventsByDeviceUserId(@Param("tenantId") String tenantId,
                                           @Param("deviceUserId") String deviceUserId);

    /**
     * Count policy violations for a device user.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId AND e.isPolicyViolation = true")
    long countPolicyViolationsByDeviceUserId(@Param("tenantId") String tenantId,
                                             @Param("deviceUserId") String deviceUserId);

    /**
     * Get last event timestamp for a device user.
     */
    @Query("SELECT MAX(e.timeStamp) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId")
    LocalDateTime findLastEventTimeByDeviceUserId(@Param("tenantId") String tenantId,
                                                   @Param("deviceUserId") String deviceUserId);

    // ==================== COMPREHENSIVE DEVICE USER QUERIES ====================

    /**
     * Count events by device user ID and time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countByDeviceUserIdAndTimeRange(@Param("tenantId") String tenantId,
                                         @Param("deviceUserId") String deviceUserId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * Count security events for device user within time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countSecurityEventsByDeviceUserIdAndTimeRange(@Param("tenantId") String tenantId,
                                                        @Param("deviceUserId") String deviceUserId,
                                                        @Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    /**
     * Count policy violations for device user within time range.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId " +
            "AND e.isPolicyViolation = true " +
            "AND e.timeStamp BETWEEN :start AND :end")
    long countPolicyViolationsByDeviceUserIdAndTimeRange(@Param("tenantId") String tenantId,
                                                          @Param("deviceUserId") String deviceUserId,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);

    /**
     * Count blocked operations for device user.
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId " +
            "AND e.isBlocked = true")
    long countBlockedByDeviceUserId(@Param("tenantId") String tenantId,
                                    @Param("deviceUserId") String deviceUserId);

    /**
     * Count events by type for device user.
     */
    @Query("SELECT e.eventType, COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.deviceUser.pkDeviceUserId = :deviceUserId " +
            "GROUP BY e.eventType ORDER BY COUNT(e) DESC")
    List<Object[]> countEventsByTypeForDeviceUser(@Param("tenantId") String tenantId,
                                                   @Param("deviceUserId") String deviceUserId);

    /**
     * Get top domains for device user.
     */
    @Query(value = """
            SELECT e.domain, COUNT(*) as visit_count
            FROM events e
            WHERE e.fk_tenant_id = :tenantId
            AND e.fk_device_user_id = :deviceUserId
            AND e.event_type = 'WEBSITE_VISIT'
            AND e.domain IS NOT NULL
            GROUP BY e.domain
            ORDER BY visit_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopDomainsForDeviceUser(@Param("tenantId") String tenantId,
                                               @Param("deviceUserId") String deviceUserId,
                                               @Param("limit") int limit);

    /**
     * Find recent events for device user.
     */
    List<Event> findTop20ByDeviceUser_PkDeviceUserIdOrderByTimeStampDesc(String deviceUserId);

    /**
     * Count events by device ID.
     */
    long countByDevice_DeviceId(String deviceId);

    /**
     * Count file operations by type for device user.
     */
    @Query(value = """
            SELECT COUNT(*) FROM events e
            WHERE e.fk_tenant_id = :tenantId
            AND e.fk_device_user_id = :deviceUserId
            AND e.file_operation_type = :operationType
            """, nativeQuery = true)
    long countFileOperationsByDeviceUserId(@Param("tenantId") String tenantId,
                                           @Param("deviceUserId") String deviceUserId,
                                           @Param("operationType") String operationType);

    /**
     * Count blocked file operations by type for device user.
     */
    @Query(value = """
            SELECT COUNT(*) FROM events e
            WHERE e.fk_tenant_id = :tenantId
            AND e.fk_device_user_id = :deviceUserId
            AND e.file_operation_type = :operationType
            AND e.is_blocked = true
            """, nativeQuery = true)
    long countBlockedFileOperationsByDeviceUserId(@Param("tenantId") String tenantId,
                                                   @Param("deviceUserId") String deviceUserId,
                                                   @Param("operationType") String operationType);

    /**
     * Find recent file operations for device user.
     */
    @Query(value = """
            SELECT * FROM events e
            WHERE e.fk_tenant_id = :tenantId
            AND e.fk_device_user_id = :deviceUserId
            AND e.file_operation_type IS NOT NULL
            ORDER BY e.time_stamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Event> findRecentFileOperationsByDeviceUserId(@Param("tenantId") String tenantId,
                                                        @Param("deviceUserId") String deviceUserId,
                                                        @Param("limit") int limit);

    /**
     * Get location stats for device user.
     */
    @Query(value = """
            SELECT e.ip_address, e.location, COUNT(*) as access_count
            FROM events e
            WHERE e.fk_tenant_id = :tenantId
            AND e.fk_device_user_id = :deviceUserId
            AND e.ip_address IS NOT NULL
            GROUP BY e.ip_address, e.location
            ORDER BY access_count DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> getLocationStatsForDeviceUser(@Param("tenantId") String tenantId,
                                                  @Param("deviceUserId") String deviceUserId);

    /**
     * Find recent policy violations for device user.
     */
    @Query(value = """
            SELECT * FROM events e
            WHERE e.fk_tenant_id = :tenantId
            AND e.fk_device_user_id = :deviceUserId
            AND e.is_policy_violation = true
            ORDER BY e.time_stamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Event> findRecentPolicyViolationsByDeviceUserId(@Param("tenantId") String tenantId,
                                                          @Param("deviceUserId") String deviceUserId,
                                                          @Param("limit") int limit);

    /**
     * Find recent security events for device user.
     */
    @Query(value = """
            SELECT * FROM events e
            WHERE e.fk_tenant_id = :tenantId
            AND e.fk_device_user_id = :deviceUserId
            AND e.is_security_event = true
            ORDER BY e.time_stamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Event> findRecentSecurityEventsByDeviceUserId(@Param("tenantId") String tenantId,
                                                        @Param("deviceUserId") String deviceUserId,
                                                        @Param("limit") int limit);

    // ==================== CONSOLIDATED COMPREHENSIVE QUERIES ====================

    /**
     * Batch count events grouped by device ID.
     * Eliminates N+1 in getDeviceUserDetails where countByDevice_DeviceId was called per device.
     * Returns rows of [deviceId (String), count (Long)].
     */
    @Query(value = """
            SELECT fk_device_id, COUNT(*) AS cnt
            FROM events
            WHERE fk_device_id IN :deviceIds
            GROUP BY fk_device_id
            """, nativeQuery = true)
    List<Object[]> countEventsByDeviceIds(@Param("deviceIds") List<String> deviceIds);

    /**
     * Single query to get all event counts for a device user's comprehensive view.
     * Replaces 9 separate count queries (totalEvents, events24h, events7d, events30d,
     * securityEvents, securityEvents30d, policyViolations, policyViolations30d, blockedOps).
     * Returns a single row with 9 columns.
     */
    @Query(value = """
            SELECT
              COUNT(*) AS total_events,
              COUNT(CASE WHEN e.time_stamp >= :last24h THEN 1 END) AS events_24h,
              COUNT(CASE WHEN e.time_stamp >= :last7d THEN 1 END) AS events_7d,
              COUNT(CASE WHEN e.time_stamp >= :last30d THEN 1 END) AS events_30d,
              COUNT(CASE WHEN e.is_security_event = true THEN 1 END) AS security_events,
              COUNT(CASE WHEN e.is_security_event = true AND e.time_stamp >= :last30d THEN 1 END) AS security_events_30d,
              COUNT(CASE WHEN e.is_policy_violation = true THEN 1 END) AS policy_violations,
              COUNT(CASE WHEN e.is_policy_violation = true AND e.time_stamp >= :last30d THEN 1 END) AS policy_violations_30d,
              COUNT(CASE WHEN e.is_blocked = true THEN 1 END) AS blocked_operations
            FROM events e
            WHERE e.fk_tenant_id = :tenantId AND e.fk_device_user_id = :deviceUserId
            """, nativeQuery = true)
    List<Object[]> getComprehensiveCountsForDeviceUser(@Param("tenantId") String tenantId,
                                                        @Param("deviceUserId") String deviceUserId,
                                                        @Param("last24h") LocalDateTime last24h,
                                                        @Param("last7d") LocalDateTime last7d,
                                                        @Param("last30d") LocalDateTime last30d);

    /**
     * Single query to get all file operation counts for a device user.
     * Replaces 6 separate countFileOperationsByDeviceUserId / countBlockedFileOperationsByDeviceUserId calls.
     * Returns a single row with 6 columns.
     */
    @Query(value = """
            SELECT
              COUNT(CASE WHEN e.file_operation_type = 'DOWNLOAD' THEN 1 END) AS downloads,
              COUNT(CASE WHEN e.file_operation_type = 'UPLOAD' THEN 1 END) AS uploads,
              COUNT(CASE WHEN e.file_operation_type = 'DOWNLOAD' AND e.is_blocked = true THEN 1 END) AS blocked_downloads,
              COUNT(CASE WHEN e.file_operation_type = 'UPLOAD' AND e.is_blocked = true THEN 1 END) AS blocked_uploads,
              COUNT(CASE WHEN e.file_operation_type = 'PRINT' THEN 1 END) AS print_ops,
              COUNT(CASE WHEN e.file_operation_type IN ('CLIPBOARD_COPY', 'CLIPBOARD_PASTE') THEN 1 END) AS clipboard_ops
            FROM events e
            WHERE e.fk_tenant_id = :tenantId
              AND e.fk_device_user_id = :deviceUserId
              AND e.file_operation_type IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> getFileOperationCountsForDeviceUser(@Param("tenantId") String tenantId,
                                                        @Param("deviceUserId") String deviceUserId);

    // ==================== INCIDENT MANAGEMENT QUERIES ====================

    /**
     * Find pending high-severity security events for auto-incident creation.
     * Only events with processingStatus='Pending' and severity in ('critical','high').
     */
    @Query("SELECT e FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.processingStatus = 'Pending' " +
            "AND e.severity IN ('critical', 'high') " +
            "ORDER BY e.timeStamp DESC")
    List<Event> findPendingHighSeverityEvents(@Param("tenantId") String tenantId);

    /**
     * Bulk update processingStatus for a list of event IDs.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Event e SET e.processingStatus = :status WHERE e.pkEventId IN :eventIds")
    int bulkUpdateProcessingStatus(@Param("eventIds") List<String> eventIds,
                                   @Param("status") String status);

    /**
     * Count pending security events (eligible for incident creation).
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (e.processingStatus = 'Pending' OR e.processingStatus IS NULL)")
    long countPendingSecurityEvents(@Param("tenantId") String tenantId);

    /**
     * Count pending high-severity security events (eligible for auto-incident creation).
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (e.processingStatus = 'Pending' OR e.processingStatus IS NULL) " +
            "AND e.severity IN ('critical', 'high')")
    long countPendingHighSeverityEvents(@Param("tenantId") String tenantId);

    // ==================== BATCH COUNT QUERIES (eliminates N+1 in list views) ====================

    /**
     * Batch count all events grouped by device user ID.
     * Replaces per-user countByTenant_TenantIDAndDeviceUser_PkDeviceUserId calls in getDeviceUserList.
     * Returns rows of [deviceUserId (String), count (Long)].
     */
    @Query(value = """
            SELECT fk_device_user_id, COUNT(*) AS cnt
            FROM events
            WHERE fk_tenant_id = :tenantId
              AND fk_device_user_id IN :deviceUserIds
            GROUP BY fk_device_user_id
            """, nativeQuery = true)
    List<Object[]> countEventsByDeviceUserIds(@Param("tenantId") String tenantId,
                                              @Param("deviceUserIds") List<String> deviceUserIds);

    /**
     * Batch count security events grouped by device user ID.
     * Replaces per-user countSecurityEventsByDeviceUserId calls in getDeviceUserList.
     * Returns rows of [deviceUserId (String), count (Long)].
     */
    @Query(value = """
            SELECT fk_device_user_id, COUNT(*) AS cnt
            FROM events
            WHERE fk_tenant_id = :tenantId
              AND fk_device_user_id IN :deviceUserIds
              AND is_security_event = true
            GROUP BY fk_device_user_id
            """, nativeQuery = true)
    List<Object[]> countSecurityEventsByDeviceUserIds(@Param("tenantId") String tenantId,
                                                      @Param("deviceUserIds") List<String> deviceUserIds);

    /**
     * Batch count all events grouped by user name.
     * Replaces per-user countByTenantIdAndUserName calls in getUserList enrichment.
     * Returns rows of [userName (String), count (Long)].
     */
    @Query(value = """
            SELECT user_name, COUNT(*) AS cnt
            FROM events
            WHERE fk_tenant_id = :tenantId
              AND user_name IN :userNames
            GROUP BY user_name
            """, nativeQuery = true)
    List<Object[]> countEventsByUserNames(@Param("tenantId") String tenantId,
                                          @Param("userNames") List<String> userNames);

    /**
     * Batch count security events grouped by user name.
     * Replaces per-user countSecurityEventsByUserName calls in getUserList enrichment.
     * Returns rows of [userName (String), count (Long)].
     */
    @Query(value = """
            SELECT user_name, COUNT(*) AS cnt
            FROM events
            WHERE fk_tenant_id = :tenantId
              AND user_name IN :userNames
              AND is_security_event = true
            GROUP BY user_name
            """, nativeQuery = true)
    List<Object[]> countSecurityEventsByUserNames(@Param("tenantId") String tenantId,
                                                   @Param("userNames") List<String> userNames);

    /**
     * Batch find last event timestamp grouped by user name.
     * Replaces per-user findLastEventTimeByUserName calls in getUserList enrichment.
     * Returns rows of [userName (String), maxTimestamp (Timestamp)].
     */
    @Query(value = """
            SELECT user_name, MAX(time_stamp) AS last_time
            FROM events
            WHERE fk_tenant_id = :tenantId
              AND user_name IN :userNames
            GROUP BY user_name
            """, nativeQuery = true)
    List<Object[]> findLastEventTimesByUserNames(@Param("tenantId") String tenantId,
                                                  @Param("userNames") List<String> userNames);

    /**
     * Bulk dismiss old pending security events before a cutoff date.
     * Sets processingStatus to 'Reviewed' so they won't trigger incidents.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Event e SET e.processingStatus = 'Reviewed' " +
            "WHERE e.tenant.tenantID = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND (e.processingStatus = 'Pending' OR e.processingStatus IS NULL) " +
            "AND e.timeStamp < :cutoffDate")
    int bulkDismissOldPendingEvents(@Param("tenantId") String tenantId,
                                     @Param("cutoffDate") LocalDateTime cutoffDate);
}
