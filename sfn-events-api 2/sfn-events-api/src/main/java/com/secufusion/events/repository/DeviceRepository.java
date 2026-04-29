package com.secufusion.events.repository;

import com.secufusion.events.entity.Device;
import com.secufusion.events.entity.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String> {

    // Basic queries
    Page<Device> findByTenantIdOrderByLastSeenAtDesc(String tenantId, Pageable pageable);

    Page<Device> findByTenantIdAndStatusOrderByLastSeenAtDesc(
            String tenantId, DeviceStatus status, Pageable pageable);

    Optional<Device> findByDeviceIdAndTenantId(String deviceId, String tenantId);

    Optional<Device> findByDeviceFingerprintAndTenantId(String fingerprint, String tenantId);

    List<Device> findByTenantIdAndUserNameOrderByLastSeenAtDesc(String tenantId, String userName);

    // Count queries
    long countByTenantId(String tenantId);

    long countByTenantIdAndStatus(String tenantId, DeviceStatus status);

    @Query("SELECT COUNT(DISTINCT d) FROM Device d WHERE d.tenantId = :tenantId " +
            "AND d.lastSeenAt >= :since")
    long countActiveDevicesSince(@Param("tenantId") String tenantId,
                                 @Param("since") LocalDateTime since);

    // Device type distribution
    @Query(value = """
            SELECT device_type, COUNT(*) as device_count
            FROM devices
            WHERE tenant_id = :tenantId
            GROUP BY device_type
            """, nativeQuery = true)
    List<Object[]> countDevicesByType(@Param("tenantId") String tenantId);

    @Query(value = """
            SELECT device_type, COUNT(*) as device_count
            FROM devices
            WHERE tenant_id = :tenantId AND last_seen_at >= :since
            GROUP BY device_type
            """, nativeQuery = true)
    List<Object[]> countActiveDevicesByType(@Param("tenantId") String tenantId,
                                            @Param("since") LocalDateTime since);

    // Recent devices
    @Query(value = """
            SELECT * FROM devices
            WHERE tenant_id = :tenantId
            ORDER BY last_seen_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Device> findRecentDevices(@Param("tenantId") String tenantId,
                                   @Param("limit") int limit);

    // Browser type distribution
    @Query(value = """
            SELECT browser_type, COUNT(*) as browser_count
            FROM devices
            WHERE tenant_id = :tenantId
            GROUP BY browser_type
            """, nativeQuery = true)
    List<Object[]> countDevicesByBrowserType(@Param("tenantId") String tenantId);

    // Devices by extension version
    @Query(value = """
            SELECT extension_version, COUNT(*) as version_count
            FROM devices
            WHERE tenant_id = :tenantId
            GROUP BY extension_version
            ORDER BY version_count DESC
            """, nativeQuery = true)
    List<Object[]> countDevicesByExtensionVersion(@Param("tenantId") String tenantId);

    // Find inactive devices (not seen since specified time)
    @Query("SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
            "AND d.status = 'ACTIVE' AND d.lastSeenAt < :since")
    List<Device> findInactiveDevices(@Param("tenantId") String tenantId,
                                     @Param("since") LocalDateTime since);

    // Get all distinct tenant IDs (for scheduled tasks)
    @Query("SELECT DISTINCT d.tenantId FROM Device d")
    List<String> findDistinctTenantIds();

    // Search devices by name, OS, or browser
    @Query("SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
            "AND (LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.osInfo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.browserType) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.userName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY d.lastSeenAt DESC")
    Page<Device> searchDevices(@Param("tenantId") String tenantId,
                               @Param("searchTerm") String searchTerm,
                               Pageable pageable);

    // Search devices with status filter
    @Query("SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
            "AND d.status = :status " +
            "AND (LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.osInfo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.browserType) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.userName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY d.lastSeenAt DESC")
    Page<Device> searchDevicesByStatus(@Param("tenantId") String tenantId,
                                       @Param("searchTerm") String searchTerm,
                                       @Param("status") DeviceStatus status,
                                       Pageable pageable);

    // ===== Anonymous Device Methods =====

    /**
     * Find device by deviceToken (for anonymous device identification)
     */
    Optional<Device> findByDeviceToken(String deviceToken);

    /**
     * Find device by deviceToken and tenantId
     */
    Optional<Device> findByDeviceTokenAndTenantId(String deviceToken, String tenantId);

    /**
     * Find anonymous devices for a tenant
     */
    @Query("SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
            "AND d.isAnonymous = true ORDER BY d.lastSeenAt DESC")
    List<Device> findAnonymousDevices(@Param("tenantId") String tenantId);

    /**
     * Find anonymous devices that have been linked to a user
     */
    @Query("SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
            "AND d.isAnonymous = true AND d.linkedUserId IS NOT NULL " +
            "ORDER BY d.linkedAt DESC")
    List<Device> findLinkedAnonymousDevices(@Param("tenantId") String tenantId);

    /**
     * Find all devices for a user including linked anonymous devices.
     * Matches by linkedUserId or userName.
     */
    @Query("SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
            "AND (d.linkedUserId = :userId OR d.userName = :userName) " +
            "ORDER BY d.lastSeenAt DESC")
    List<Device> findAllUserDevices(@Param("tenantId") String tenantId,
                                    @Param("userId") String userId,
                                    @Param("userName") String userName);

    /**
     * Count anonymous devices for a tenant
     */
    @Query("SELECT COUNT(d) FROM Device d WHERE d.tenantId = :tenantId " +
            "AND d.isAnonymous = true")
    long countAnonymousDevices(@Param("tenantId") String tenantId);

    // ===== Device User Methods =====

    /**
     * Find devices by device user ID.
     */
    List<Device> findByTenantIdAndDeviceUser_PkDeviceUserIdOrderByLastSeenAtDesc(
            String tenantId, String deviceUserId);

    /**
     * Count devices for a device user.
     */
    long countByTenantIdAndDeviceUser_PkDeviceUserId(String tenantId, String deviceUserId);

    /**
     * Batch count devices grouped by device user ID.
     * Eliminates the N+1 in DeviceUser list enrichment.
     * Returns rows of [deviceUserId (String), count (Long)].
     */
    @Query(value = """
            SELECT fk_device_user_id, COUNT(*) as cnt
            FROM devices
            WHERE tenant_id = :tenantId
              AND fk_device_user_id IN :deviceUserIds
            GROUP BY fk_device_user_id
            """, nativeQuery = true)
    List<Object[]> countDevicesByDeviceUserIds(@Param("tenantId") String tenantId,
                                               @Param("deviceUserIds") List<String> deviceUserIds);

    /**
     * Batch load all devices for a set of users in one query.
     * Used instead of per-user findAllUserDevices in UserActivityService.getUserList.
     * Matches by linkedUserId (FK) or userName (string fallback).
     */
    @Query("SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
           "AND (d.linkedUserId IN :userIds OR d.userName IN :userNames) " +
           "ORDER BY d.lastSeenAt DESC")
    List<Device> findAllDevicesForUsers(@Param("tenantId") String tenantId,
                                        @Param("userIds") List<String> userIds,
                                        @Param("userNames") List<String> userNames);

    /**
     * Count distinct portal users (linkedUserId) that have at least one device.
     * Replaces the all-users N+1 loop in getUserStats.
     */
    @Query(value = """
            SELECT COUNT(DISTINCT linked_user_id)
            FROM devices
            WHERE tenant_id = :tenantId AND linked_user_id IS NOT NULL
            """, nativeQuery = true)
    long countDistinctUsersWithDevices(@Param("tenantId") String tenantId);
}
