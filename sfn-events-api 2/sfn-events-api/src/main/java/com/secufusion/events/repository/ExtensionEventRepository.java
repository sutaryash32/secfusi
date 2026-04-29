package com.secufusion.events.repository;

import com.secufusion.events.entity.ExtensionEvent;
import com.secufusion.events.entity.ExtensionEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExtensionEventRepository extends JpaRepository<ExtensionEvent, String> {

    // ===== Basic Queries =====

    /**
     * Find events for a specific device
     */
    Page<ExtensionEvent> findByDeviceDeviceIdOrderByEventTimestampDesc(
            String deviceId, Pageable pageable);

    /**
     * Find events for a tenant
     */
    Page<ExtensionEvent> findByTenantIdOrderByEventTimestampDesc(
            String tenantId, Pageable pageable);

    /**
     * Find events for a specific extension
     */
    Page<ExtensionEvent> findByTenantIdAndExtensionIdOrderByEventTimestampDesc(
            String tenantId, String extensionId, Pageable pageable);

    /**
     * Find events for a user
     */
    Page<ExtensionEvent> findByTenantIdAndUserIdOrderByEventTimestampDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find events by type for a tenant
     */
    Page<ExtensionEvent> findByTenantIdAndEventTypeOrderByEventTimestampDesc(
            String tenantId, ExtensionEventType eventType, Pageable pageable);

    // ===== Time-Based Queries =====

    /**
     * Find events in a time range
     */
    @Query("SELECT ee FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.eventTimestamp BETWEEN :startTime AND :endTime " +
            "ORDER BY ee.eventTimestamp DESC")
    Page<ExtensionEvent> findByTenantIdAndTimeRange(
            @Param("tenantId") String tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Find recent events for a tenant
     */
    @Query("SELECT ee FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.eventTimestamp >= :since ORDER BY ee.eventTimestamp DESC")
    List<ExtensionEvent> findRecentEvents(@Param("tenantId") String tenantId,
                                          @Param("since") LocalDateTime since);

    /**
     * Find recent events for a device
     */
    @Query("SELECT ee FROM ExtensionEvent ee WHERE ee.device.deviceId = :deviceId " +
            "AND ee.eventTimestamp >= :since ORDER BY ee.eventTimestamp DESC")
    List<ExtensionEvent> findRecentDeviceEvents(@Param("deviceId") String deviceId,
                                                @Param("since") LocalDateTime since);

    // ===== Policy-Related Queries =====

    /**
     * Find block events for a tenant
     */
    @Query("SELECT ee FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.eventType = 'EXTENSION_BLOCKED' ORDER BY ee.eventTimestamp DESC")
    Page<ExtensionEvent> findBlockEvents(@Param("tenantId") String tenantId, Pageable pageable);

    /**
     * Find warning events for a tenant
     */
    @Query("SELECT ee FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.eventType = 'EXTENSION_WARNING_SHOWN' ORDER BY ee.eventTimestamp DESC")
    Page<ExtensionEvent> findWarningEvents(@Param("tenantId") String tenantId, Pageable pageable);

    /**
     * Find events by policy action
     */
    Page<ExtensionEvent> findByTenantIdAndPolicyActionOrderByEventTimestampDesc(
            String tenantId, String policyAction, Pageable pageable);

    // ===== Analytics Queries =====

    /**
     * Count events by type for a tenant
     */
    @Query(value = """
            SELECT event_type, COUNT(*) as count
            FROM extension_events
            WHERE tenant_id = :tenantId
            AND event_timestamp >= :since
            GROUP BY event_type
            """, nativeQuery = true)
    List<Object[]> countEventsByType(@Param("tenantId") String tenantId,
                                     @Param("since") LocalDateTime since);

    /**
     * Count events by policy action
     */
    @Query(value = """
            SELECT policy_action, COUNT(*) as count
            FROM extension_events
            WHERE tenant_id = :tenantId
            AND policy_action IS NOT NULL
            AND event_timestamp >= :since
            GROUP BY policy_action
            """, nativeQuery = true)
    List<Object[]> countEventsByPolicyAction(@Param("tenantId") String tenantId,
                                             @Param("since") LocalDateTime since);

    /**
     * Get daily event counts
     */
    @Query(value = """
            SELECT DATE(event_timestamp) as event_date, COUNT(*) as count
            FROM extension_events
            WHERE tenant_id = :tenantId
            AND event_timestamp >= :since
            GROUP BY DATE(event_timestamp)
            ORDER BY event_date DESC
            """, nativeQuery = true)
    List<Object[]> getDailyEventCounts(@Param("tenantId") String tenantId,
                                       @Param("since") LocalDateTime since);

    /**
     * Get most blocked extensions
     */
    @Query(value = """
            SELECT extension_id, extension_name, COUNT(*) as block_count
            FROM extension_events
            WHERE tenant_id = :tenantId
            AND event_type = 'EXTENSION_BLOCKED'
            AND event_timestamp >= :since
            GROUP BY extension_id, extension_name
            ORDER BY block_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getMostBlockedExtensions(@Param("tenantId") String tenantId,
                                            @Param("since") LocalDateTime since,
                                            @Param("limit") int limit);

    /**
     * Get install/uninstall trends
     */
    @Query(value = """
            SELECT DATE(event_timestamp) as event_date,
                   SUM(CASE WHEN event_type = 'EXTENSION_INSTALLED' THEN 1 ELSE 0 END) as installs,
                   SUM(CASE WHEN event_type = 'EXTENSION_UNINSTALLED' THEN 1 ELSE 0 END) as uninstalls
            FROM extension_events
            WHERE tenant_id = :tenantId
            AND event_timestamp >= :since
            GROUP BY DATE(event_timestamp)
            ORDER BY event_date DESC
            """, nativeQuery = true)
    List<Object[]> getInstallUninstallTrends(@Param("tenantId") String tenantId,
                                             @Param("since") LocalDateTime since);

    // ===== Count Queries =====

    /**
     * Count events for a tenant since a time
     */
    long countByTenantIdAndEventTimestampAfter(String tenantId, LocalDateTime since);

    /**
     * Count events by type
     */
    long countByTenantIdAndEventType(String tenantId, ExtensionEventType eventType);

    /**
     * Count block events since a time
     */
    @Query("SELECT COUNT(ee) FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.eventType = 'EXTENSION_BLOCKED' AND ee.eventTimestamp >= :since")
    long countBlockEventsSince(@Param("tenantId") String tenantId,
                               @Param("since") LocalDateTime since);

    /**
     * Count warning events since a time
     */
    @Query("SELECT COUNT(ee) FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.eventType = 'EXTENSION_WARNING_SHOWN' AND ee.eventTimestamp >= :since")
    long countWarningEventsSince(@Param("tenantId") String tenantId,
                                 @Param("since") LocalDateTime since);

    // ===== Dashboard Analytics Queries =====

    /**
     * Daily activity breakdown with installs, uninstalls, blocks, and warnings.
     * Returns [date, installed, uninstalled, blocked, warned].
     */
    @Query(value = """
            SELECT DATE(event_timestamp) AS event_date,
                   COUNT(*) FILTER (WHERE event_type = 'EXTENSION_INSTALLED') AS installed,
                   COUNT(*) FILTER (WHERE event_type = 'EXTENSION_UNINSTALLED') AS uninstalled,
                   COUNT(*) FILTER (WHERE event_type = 'EXTENSION_BLOCKED') AS blocked,
                   COUNT(*) FILTER (WHERE event_type = 'EXTENSION_WARNING_SHOWN') AS warned
            FROM extension_events
            WHERE tenant_id = :tenantId AND event_timestamp >= :since
            GROUP BY DATE(event_timestamp)
            ORDER BY event_date DESC
            """, nativeQuery = true)
    List<Object[]> getDailyActivityBreakdown(@Param("tenantId") String tenantId,
                                              @Param("since") LocalDateTime since);

    /**
     * Most warned extensions with acknowledgement counts.
     * Returns [extensionId, extensionName, warnCount, acknowledgedCount].
     */
    @Query(value = """
            SELECT e.extension_id, e.extension_name,
                   COUNT(*) FILTER (WHERE e.event_type = 'EXTENSION_WARNING_SHOWN') AS warn_count,
                   COUNT(*) FILTER (WHERE e.event_type = 'EXTENSION_WARNING_ACKNOWLEDGED') AS ack_count
            FROM extension_events e
            WHERE e.tenant_id = :tenantId
              AND e.event_type IN ('EXTENSION_WARNING_SHOWN', 'EXTENSION_WARNING_ACKNOWLEDGED')
              AND e.event_timestamp >= :since
            GROUP BY e.extension_id, e.extension_name
            ORDER BY warn_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getMostWarnedExtensions(@Param("tenantId") String tenantId,
                                            @Param("since") LocalDateTime since,
                                            @Param("limit") int limit);

    /**
     * Most blocked extensions with unique user counts.
     * Returns [extensionId, extensionName, blockCount, uniqueUsers].
     */
    @Query(value = """
            SELECT e.extension_id, e.extension_name,
                   COUNT(*) AS block_count,
                   COUNT(DISTINCT e.user_id) AS unique_users
            FROM extension_events e
            WHERE e.tenant_id = :tenantId
              AND e.event_type = 'EXTENSION_BLOCKED'
              AND e.event_timestamp >= :since
            GROUP BY e.extension_id, e.extension_name
            ORDER BY block_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getMostBlockedWithUsers(@Param("tenantId") String tenantId,
                                            @Param("since") LocalDateTime since,
                                            @Param("limit") int limit);

    /**
     * Count warning acknowledged events since a time.
     */
    @Query("SELECT COUNT(ee) FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.eventType = 'EXTENSION_WARNING_ACKNOWLEDGED' AND ee.eventTimestamp >= :since")
    long countWarningAcknowledgedSince(@Param("tenantId") String tenantId,
                                       @Param("since") LocalDateTime since);

    /**
     * Top recently removed (uninstalled) extensions.
     * Returns [extensionId, extensionName, uninstallCount].
     */
    @Query(value = """
            SELECT extension_id, extension_name, COUNT(*) AS uninstall_count
            FROM extension_events
            WHERE tenant_id = :tenantId
              AND event_type = 'EXTENSION_UNINSTALLED'
              AND event_timestamp >= :since
            GROUP BY extension_id, extension_name
            ORDER BY uninstall_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopRemovedExtensions(@Param("tenantId") String tenantId,
                                            @Param("since") LocalDateTime since,
                                            @Param("limit") int limit);

    // ===== Extension History =====

    /**
     * Get complete history of an extension on a device
     */
    @Query("SELECT ee FROM ExtensionEvent ee WHERE ee.device.deviceId = :deviceId " +
            "AND ee.extensionId = :extensionId ORDER BY ee.eventTimestamp DESC")
    List<ExtensionEvent> getExtensionHistory(@Param("deviceId") String deviceId,
                                             @Param("extensionId") String extensionId);

    /**
     * Get complete history of an extension across all devices in a tenant
     */
    @Query("SELECT ee FROM ExtensionEvent ee WHERE ee.tenantId = :tenantId " +
            "AND ee.extensionId = :extensionId ORDER BY ee.eventTimestamp DESC")
    Page<ExtensionEvent> getTenantExtensionHistory(@Param("tenantId") String tenantId,
                                                    @Param("extensionId") String extensionId,
                                                    Pageable pageable);
}
