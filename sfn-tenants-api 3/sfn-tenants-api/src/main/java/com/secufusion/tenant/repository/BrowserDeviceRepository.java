package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.BrowserDevice;
import com.secufusion.tenant.entity.BrowserDeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for browser devices - used for dashboard statistics.
 * Read-only access to the devices table.
 */
@Repository
public interface BrowserDeviceRepository extends JpaRepository<BrowserDevice, String> {

    // ==================== COUNT QUERIES ====================

    /**
     * Count total devices for a tenant.
     */
    long countByTenantId(String tenantId);

    /**
     * Count devices by status for a tenant.
     */
    long countByTenantIdAndStatus(String tenantId, BrowserDeviceStatus status);

    /**
     * Count active devices for a tenant.
     */
    @Query("SELECT COUNT(d) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "AND d.status = 'ACTIVE'")
    long countActiveDevices(@Param("tenantId") String tenantId);

    /**
     * Count active devices across all tenants.
     */
    @Query("SELECT COUNT(d) FROM BrowserDevice d WHERE d.status = 'ACTIVE'")
    long countActiveDevicesAllTenants();

    /**
     * Count devices seen within a time range for a tenant.
     */
    @Query("SELECT COUNT(d) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "AND d.lastSeenAt >= :since")
    long countRecentlyActiveDevices(@Param("tenantId") String tenantId,
                                     @Param("since") LocalDateTime since);

    /**
     * Count devices seen within a time range across all tenants.
     */
    @Query("SELECT COUNT(d) FROM BrowserDevice d WHERE d.lastSeenAt >= :since")
    long countRecentlyActiveDevicesAllTenants(@Param("since") LocalDateTime since);

    /**
     * Count unique users with devices for a tenant.
     */
    @Query("SELECT COUNT(DISTINCT d.userName) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "AND d.userName IS NOT NULL")
    long countUniqueUsers(@Param("tenantId") String tenantId);

    /**
     * Count unique users with devices across all tenants.
     */
    @Query("SELECT COUNT(DISTINCT d.userName) FROM BrowserDevice d WHERE d.userName IS NOT NULL")
    long countUniqueUsersAllTenants();

    // ==================== AGGREGATION QUERIES ====================

    /**
     * Count devices by device type for a tenant.
     */
    @Query("SELECT d.deviceType, COUNT(d) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "GROUP BY d.deviceType ORDER BY COUNT(d) DESC")
    List<Object[]> countByDeviceType(@Param("tenantId") String tenantId);

    /**
     * Count devices by device type across all tenants.
     */
    @Query("SELECT d.deviceType, COUNT(d) FROM BrowserDevice d " +
            "GROUP BY d.deviceType ORDER BY COUNT(d) DESC")
    List<Object[]> countByDeviceTypeAllTenants();

    /**
     * Count devices by browser type for a tenant.
     */
    @Query("SELECT d.browserType, COUNT(d) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "GROUP BY d.browserType ORDER BY COUNT(d) DESC")
    List<Object[]> countByBrowserType(@Param("tenantId") String tenantId);

    /**
     * Count devices by browser type across all tenants.
     */
    @Query("SELECT d.browserType, COUNT(d) FROM BrowserDevice d " +
            "GROUP BY d.browserType ORDER BY COUNT(d) DESC")
    List<Object[]> countByBrowserTypeAllTenants();

    /**
     * Count devices by OS for a tenant.
     */
    @Query("SELECT d.osInfo, COUNT(d) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "AND d.osInfo IS NOT NULL " +
            "GROUP BY d.osInfo ORDER BY COUNT(d) DESC")
    List<Object[]> countByOsInfo(@Param("tenantId") String tenantId);

    /**
     * Count devices by OS across all tenants.
     */
    @Query("SELECT d.osInfo, COUNT(d) FROM BrowserDevice d " +
            "WHERE d.osInfo IS NOT NULL " +
            "GROUP BY d.osInfo ORDER BY COUNT(d) DESC")
    List<Object[]> countByOsInfoAllTenants();

    /**
     * Count devices by status for a tenant.
     */
    @Query("SELECT d.status, COUNT(d) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "GROUP BY d.status ORDER BY COUNT(d) DESC")
    List<Object[]> countByStatus(@Param("tenantId") String tenantId);

    /**
     * Count devices by status across all tenants.
     */
    @Query("SELECT d.status, COUNT(d) FROM BrowserDevice d " +
            "GROUP BY d.status ORDER BY COUNT(d) DESC")
    List<Object[]> countByStatusAllTenants();

    /**
     * Count devices by extension version for a tenant.
     */
    @Query("SELECT d.extensionVersion, COUNT(d) FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "AND d.extensionVersion IS NOT NULL " +
            "GROUP BY d.extensionVersion ORDER BY COUNT(d) DESC")
    List<Object[]> countByExtensionVersion(@Param("tenantId") String tenantId);

    /**
     * Count devices by extension version across all tenants.
     */
    @Query("SELECT d.extensionVersion, COUNT(d) FROM BrowserDevice d " +
            "WHERE d.extensionVersion IS NOT NULL " +
            "GROUP BY d.extensionVersion ORDER BY COUNT(d) DESC")
    List<Object[]> countByExtensionVersionAllTenants();

    // ==================== RECENT DEVICES QUERIES ====================

    /**
     * Get recently active devices for a tenant.
     */
    @Query("SELECT d FROM BrowserDevice d WHERE d.tenantId = :tenantId " +
            "ORDER BY d.lastSeenAt DESC")
    List<BrowserDevice> findRecentDevices(@Param("tenantId") String tenantId);

    /**
     * Get recently active devices across all tenants.
     */
    @Query("SELECT d FROM BrowserDevice d ORDER BY d.lastSeenAt DESC")
    List<BrowserDevice> findRecentDevicesAllTenants();

    /**
     * Get devices by user for a tenant.
     */
    List<BrowserDevice> findByTenantIdAndUserNameOrderByLastSeenAtDesc(String tenantId, String userName);

    // ==================== CONSOLIDATED COUNT QUERIES ====================

    /**
     * Consolidated device count query for a tenant - replaces 4 individual status count queries with 1.
     * Returns: total, active, inactive, blocked
     */
    @Query(value = "SELECT COUNT(*) as total, " +
            "COUNT(*) FILTER (WHERE status = 'ACTIVE') as active, " +
            "COUNT(*) FILTER (WHERE status = 'INACTIVE') as inactive, " +
            "COUNT(*) FILTER (WHERE status = 'BLOCKED') as blocked " +
            "FROM devices WHERE tenant_id = :tenantId",
            nativeQuery = true)
    List<Object[]> getConsolidatedDeviceCounts(@Param("tenantId") String tenantId);

    /**
     * Consolidated device count query across ALL tenants.
     */
    @Query(value = "SELECT COUNT(*) as total, " +
            "COUNT(*) FILTER (WHERE status = 'ACTIVE') as active, " +
            "COUNT(*) FILTER (WHERE status = 'INACTIVE') as inactive, " +
            "COUNT(*) FILTER (WHERE status = 'BLOCKED') as blocked " +
            "FROM devices",
            nativeQuery = true)
    List<Object[]> getConsolidatedDeviceCountsAllTenants();
}
