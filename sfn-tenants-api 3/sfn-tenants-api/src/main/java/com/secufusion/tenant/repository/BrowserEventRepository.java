package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.BrowserEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for browser events - used for dashboard statistics.
 * Read-only access to the events table.
 */
@Repository
public interface BrowserEventRepository extends JpaRepository<BrowserEvent, String> {

    // ==================== COUNT QUERIES ====================

    /**
     * Count total browser events for a tenant within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countByTenantIdAndTimeRange(@Param("tenantId") String tenantId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * Count browser events across all tenants within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.timeStamp >= :start AND e.timeStamp <= :end")
    long countAllByTimeRange(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /**
     * Count policy violations for a tenant within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.isPolicyViolation = true " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countPolicyViolations(@Param("tenantId") String tenantId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /**
     * Count policy violations across all tenants within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.isPolicyViolation = true " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countPolicyViolationsAllTenants(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * Count blocked events for a tenant within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.isBlocked = true " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countBlockedEvents(@Param("tenantId") String tenantId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    /**
     * Count blocked events across all tenants within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.isBlocked = true " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countBlockedEventsAllTenants(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * Count security events for a tenant within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.isSecurityEvent = true " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countSecurityEvents(@Param("tenantId") String tenantId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /**
     * Count security events across all tenants within a time range.
     */
    @Query("SELECT COUNT(e) FROM BrowserEvent e WHERE e.isSecurityEvent = true " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countSecurityEventsAllTenants(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * Count unique users with events for a tenant within a time range.
     */
    @Query("SELECT COUNT(DISTINCT e.userName) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.userName IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countActiveUsers(@Param("tenantId") String tenantId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    /**
     * Count unique users with events across all tenants within a time range.
     */
    @Query("SELECT COUNT(DISTINCT e.userName) FROM BrowserEvent e WHERE e.userName IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end")
    long countActiveUsersAllTenants(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    // ==================== AGGREGATION QUERIES ====================

    /**
     * Count events by event type for a tenant within a time range.
     */
    @Query("SELECT e.eventType, COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.eventType ORDER BY COUNT(e) DESC")
    List<Object[]> countByEventType(@Param("tenantId") String tenantId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /**
     * Count events by event type across all tenants within a time range.
     */
    @Query("SELECT e.eventType, COUNT(e) FROM BrowserEvent e " +
            "WHERE e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.eventType ORDER BY COUNT(e) DESC")
    List<Object[]> countByEventTypeAllTenants(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * Count events by category for a tenant within a time range.
     */
    @Query("SELECT e.category, COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.category IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.category ORDER BY COUNT(e) DESC")
    List<Object[]> countByCategory(@Param("tenantId") String tenantId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * Count events by category across all tenants within a time range.
     */
    @Query("SELECT e.category, COUNT(e) FROM BrowserEvent e " +
            "WHERE e.category IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.category ORDER BY COUNT(e) DESC")
    List<Object[]> countByCategoryAllTenants(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /**
     * Count events by threat type for a tenant within a time range.
     */
    @Query("SELECT e.threatType, COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.threatType IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.threatType ORDER BY COUNT(e) DESC")
    List<Object[]> countByThreatType(@Param("tenantId") String tenantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * Count events by threat type across all tenants within a time range.
     */
    @Query("SELECT e.threatType, COUNT(e) FROM BrowserEvent e " +
            "WHERE e.threatType IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.threatType ORDER BY COUNT(e) DESC")
    List<Object[]> countByThreatTypeAllTenants(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /**
     * Count events by severity for a tenant within a time range.
     */
    @Query("SELECT e.severity, COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.severity IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.severity ORDER BY COUNT(e) DESC")
    List<Object[]> countBySeverity(@Param("tenantId") String tenantId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * Count events by severity across all tenants within a time range.
     */
    @Query("SELECT e.severity, COUNT(e) FROM BrowserEvent e " +
            "WHERE e.severity IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.severity ORDER BY COUNT(e) DESC")
    List<Object[]> countBySeverityAllTenants(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /**
     * Count events by file operation type for a tenant within a time range.
     */
    @Query("SELECT e.fileOperationType, COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.fileOperationType IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.fileOperationType ORDER BY COUNT(e) DESC")
    List<Object[]> countByFileOperationType(@Param("tenantId") String tenantId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /**
     * Count events by file operation type across all tenants within a time range.
     */
    @Query("SELECT e.fileOperationType, COUNT(e) FROM BrowserEvent e " +
            "WHERE e.fileOperationType IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.fileOperationType ORDER BY COUNT(e) DESC")
    List<Object[]> countByFileOperationTypeAllTenants(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    /**
     * Get top domains by event count for a tenant within a time range.
     */
    @Query("SELECT e.domain, COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.domain IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.domain ORDER BY COUNT(e) DESC")
    List<Object[]> getTopDomains(@Param("tenantId") String tenantId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * Get top domains by event count across all tenants within a time range.
     */
    @Query("SELECT e.domain, COUNT(e) FROM BrowserEvent e " +
            "WHERE e.domain IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.domain ORDER BY COUNT(e) DESC")
    List<Object[]> getTopDomainsAllTenants(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    /**
     * Get top users by event count for a tenant within a time range.
     */
    @Query("SELECT e.userName, COUNT(e) FROM BrowserEvent e WHERE e.tenantId = :tenantId " +
            "AND e.userName IS NOT NULL " +
            "AND e.timeStamp >= :start AND e.timeStamp <= :end " +
            "GROUP BY e.userName ORDER BY COUNT(e) DESC")
    List<Object[]> getTopUsers(@Param("tenantId") String tenantId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /**
     * Get daily event trends for a tenant within a time range.
     */
    @Query(value = "SELECT CAST(e.time_stamp AS DATE) as event_date, COUNT(*) as count " +
            "FROM events e WHERE e.fk_tenant_id = :tenantId " +
            "AND e.time_stamp >= :start AND e.time_stamp <= :end " +
            "GROUP BY CAST(e.time_stamp AS DATE) ORDER BY event_date DESC",
            nativeQuery = true)
    List<Object[]> getDailyTrends(@Param("tenantId") String tenantId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /**
     * Get daily event trends across all tenants within a time range.
     */
    @Query(value = "SELECT CAST(e.time_stamp AS DATE) as event_date, COUNT(*) as count " +
            "FROM events e WHERE e.time_stamp >= :start AND e.time_stamp <= :end " +
            "GROUP BY CAST(e.time_stamp AS DATE) ORDER BY event_date DESC",
            nativeQuery = true)
    List<Object[]> getDailyTrendsAllTenants(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /**
     * Get hourly event distribution for a tenant within a time range.
     */
    @Query(value = "SELECT EXTRACT(HOUR FROM e.time_stamp) as hour, COUNT(*) as count " +
            "FROM events e WHERE e.fk_tenant_id = :tenantId " +
            "AND e.time_stamp >= :start AND e.time_stamp <= :end " +
            "GROUP BY EXTRACT(HOUR FROM e.time_stamp) ORDER BY hour",
            nativeQuery = true)
    List<Object[]> getHourlyDistribution(@Param("tenantId") String tenantId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    // ==================== CONSOLIDATED COUNT QUERIES ====================

    /**
     * Consolidated count query for a tenant - replaces 15 individual count queries with 1.
     * Returns a single row with all counts using PostgreSQL FILTER clauses.
     * Column order: total_today, total_week, total_month,
     *   violations_today, violations_week, violations_month,
     *   blocked_today, blocked_week, blocked_month,
     *   security_today, security_week, security_month,
     *   users_today, users_week, users_month
     */
    @Query(value = "SELECT " +
            "COUNT(*) FILTER (WHERE time_stamp >= :startOfDay) as total_today, " +
            "COUNT(*) FILTER (WHERE time_stamp >= :startOfWeek) as total_week, " +
            "COUNT(*) as total_month, " +
            "COUNT(*) FILTER (WHERE is_policy_violation = true AND time_stamp >= :startOfDay) as violations_today, " +
            "COUNT(*) FILTER (WHERE is_policy_violation = true AND time_stamp >= :startOfWeek) as violations_week, " +
            "COUNT(*) FILTER (WHERE is_policy_violation = true) as violations_month, " +
            "COUNT(*) FILTER (WHERE is_blocked = true AND time_stamp >= :startOfDay) as blocked_today, " +
            "COUNT(*) FILTER (WHERE is_blocked = true AND time_stamp >= :startOfWeek) as blocked_week, " +
            "COUNT(*) FILTER (WHERE is_blocked = true) as blocked_month, " +
            "COUNT(*) FILTER (WHERE is_security_event = true AND time_stamp >= :startOfDay) as security_today, " +
            "COUNT(*) FILTER (WHERE is_security_event = true AND time_stamp >= :startOfWeek) as security_week, " +
            "COUNT(*) FILTER (WHERE is_security_event = true) as security_month, " +
            "COUNT(DISTINCT CASE WHEN time_stamp >= :startOfDay AND user_name IS NOT NULL THEN user_name END) as users_today, " +
            "COUNT(DISTINCT CASE WHEN time_stamp >= :startOfWeek AND user_name IS NOT NULL THEN user_name END) as users_week, " +
            "COUNT(DISTINCT CASE WHEN user_name IS NOT NULL THEN user_name END) as users_month " +
            "FROM events WHERE fk_tenant_id = :tenantId " +
            "AND time_stamp >= :startOfMonth AND time_stamp <= :now",
            nativeQuery = true)
    List<Object[]> getConsolidatedCounts(@Param("tenantId") String tenantId,
                                          @Param("startOfDay") LocalDateTime startOfDay,
                                          @Param("startOfWeek") LocalDateTime startOfWeek,
                                          @Param("startOfMonth") LocalDateTime startOfMonth,
                                          @Param("now") LocalDateTime now);

    /**
     * Consolidated count query across ALL tenants - replaces 15 individual count queries with 1.
     */
    @Query(value = "SELECT " +
            "COUNT(*) FILTER (WHERE time_stamp >= :startOfDay) as total_today, " +
            "COUNT(*) FILTER (WHERE time_stamp >= :startOfWeek) as total_week, " +
            "COUNT(*) as total_month, " +
            "COUNT(*) FILTER (WHERE is_policy_violation = true AND time_stamp >= :startOfDay) as violations_today, " +
            "COUNT(*) FILTER (WHERE is_policy_violation = true AND time_stamp >= :startOfWeek) as violations_week, " +
            "COUNT(*) FILTER (WHERE is_policy_violation = true) as violations_month, " +
            "COUNT(*) FILTER (WHERE is_blocked = true AND time_stamp >= :startOfDay) as blocked_today, " +
            "COUNT(*) FILTER (WHERE is_blocked = true AND time_stamp >= :startOfWeek) as blocked_week, " +
            "COUNT(*) FILTER (WHERE is_blocked = true) as blocked_month, " +
            "COUNT(*) FILTER (WHERE is_security_event = true AND time_stamp >= :startOfDay) as security_today, " +
            "COUNT(*) FILTER (WHERE is_security_event = true AND time_stamp >= :startOfWeek) as security_week, " +
            "COUNT(*) FILTER (WHERE is_security_event = true) as security_month, " +
            "COUNT(DISTINCT CASE WHEN time_stamp >= :startOfDay AND user_name IS NOT NULL THEN user_name END) as users_today, " +
            "COUNT(DISTINCT CASE WHEN time_stamp >= :startOfWeek AND user_name IS NOT NULL THEN user_name END) as users_week, " +
            "COUNT(DISTINCT CASE WHEN user_name IS NOT NULL THEN user_name END) as users_month " +
            "FROM events WHERE time_stamp >= :startOfMonth AND time_stamp <= :now",
            nativeQuery = true)
    List<Object[]> getConsolidatedCountsAllTenants(@Param("startOfDay") LocalDateTime startOfDay,
                                                     @Param("startOfWeek") LocalDateTime startOfWeek,
                                                     @Param("startOfMonth") LocalDateTime startOfMonth,
                                                     @Param("now") LocalDateTime now);
}
