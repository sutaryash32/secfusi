package com.secufusion.events.repository;

import com.secufusion.events.entity.ExtensionStatus;
import com.secufusion.events.entity.InstalledExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstalledExtensionRepository extends JpaRepository<InstalledExtension, String> {

    // ===== Basic Queries =====

    /**
     * Find all extensions installed on a specific device
     */
    List<InstalledExtension> findByDeviceDeviceIdOrderByExtensionNameAsc(String deviceId);

    /**
     * Find all active extensions on a device
     */
    List<InstalledExtension> findByDeviceDeviceIdAndStatusOrderByExtensionNameAsc(
            String deviceId, ExtensionStatus status);

    /**
     * Find specific extension on a device
     */
    Optional<InstalledExtension> findByDeviceDeviceIdAndExtensionId(String deviceId, String extensionId);

    /**
     * Batch load multiple extensions on a device by extensionId list.
     * Replaces the per-item findByDeviceDeviceIdAndExtensionId loop in syncExtensions.
     */
    List<InstalledExtension> findByDeviceDeviceIdAndExtensionIdIn(String deviceId, List<String> extensionIds);

    /**
     * Find all installations of a specific extension in a tenant
     */
    List<InstalledExtension> findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
            String tenantId, String extensionId);

    /**
     * Find all extensions for a tenant
     */
    Page<InstalledExtension> findByTenantIdOrderByLastSeenAtDesc(String tenantId, Pageable pageable);

    /**
     * Find all extensions for a user across all their devices
     */
    List<InstalledExtension> findByTenantIdAndUserIdOrderByExtensionNameAsc(String tenantId, String userId);

    // ===== Policy-Based Queries =====

    /**
     * Find all blocked extensions in a tenant
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND ie.policyAction = 'BLOCK' ORDER BY ie.lastSeenAt DESC")
    List<InstalledExtension> findBlockedExtensions(@Param("tenantId") String tenantId);

    /**
     * Find extensions with warnings
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND ie.policyAction = 'WARN' ORDER BY ie.lastSeenAt DESC")
    List<InstalledExtension> findWarningExtensions(@Param("tenantId") String tenantId);

    /**
     * Find whitelisted extensions
     */
    List<InstalledExtension> findByTenantIdAndIsWhitelistedTrueOrderByExtensionNameAsc(String tenantId);

    /**
     * Find blacklisted extensions
     */
    List<InstalledExtension> findByTenantIdAndIsBlacklistedTrueOrderByExtensionNameAsc(String tenantId);

    // ===== Risk-Based Queries =====

    /**
     * Find high-risk extensions in a tenant
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND ie.riskLevel = 'HIGH' ORDER BY ie.riskScore DESC")
    List<InstalledExtension> findHighRiskExtensions(@Param("tenantId") String tenantId);

    /**
     * Find extensions by risk level
     */
    List<InstalledExtension> findByTenantIdAndRiskLevelOrderByRiskScoreDesc(
            String tenantId, String riskLevel);

    /**
     * Find extensions with risk score above threshold
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND ie.riskScore >= :threshold ORDER BY ie.riskScore DESC")
    List<InstalledExtension> findExtensionsAboveRiskThreshold(
            @Param("tenantId") String tenantId,
            @Param("threshold") Integer threshold);

    // ===== Count Queries =====

    /**
     * Count unique extensions in a tenant
     */
    @Query("SELECT COUNT(DISTINCT ie.extensionId) FROM InstalledExtension ie " +
            "WHERE ie.tenantId = :tenantId")
    long countUniqueExtensions(@Param("tenantId") String tenantId);

    /**
     * Count extensions by status
     */
    long countByTenantIdAndStatus(String tenantId, ExtensionStatus status);

    /**
     * Count extensions by policy action
     */
    long countByTenantIdAndPolicyAction(String tenantId, String policyAction);

    /**
     * Count high-risk extensions
     */
    @Query("SELECT COUNT(DISTINCT ie.extensionId) FROM InstalledExtension ie " +
            "WHERE ie.tenantId = :tenantId AND ie.riskLevel = 'HIGH'")
    long countHighRiskExtensions(@Param("tenantId") String tenantId);

    // ===== Analytics Queries =====

    /**
     * Get extension distribution by policy action
     */
    @Query(value = """
            SELECT policy_action, COUNT(*) as count
            FROM installed_extensions
            WHERE tenant_id = :tenantId
            GROUP BY policy_action
            """, nativeQuery = true)
    List<Object[]> countByPolicyAction(@Param("tenantId") String tenantId);

    /**
     * Get extension distribution by risk level
     */
    @Query(value = """
            SELECT risk_level, COUNT(*) as count
            FROM installed_extensions
            WHERE tenant_id = :tenantId
            GROUP BY risk_level
            """, nativeQuery = true)
    List<Object[]> countByRiskLevel(@Param("tenantId") String tenantId);

    /**
     * Get most common extensions in a tenant
     */
    @Query(value = """
            SELECT extension_id, extension_name, COUNT(*) as install_count
            FROM installed_extensions
            WHERE tenant_id = :tenantId AND status = 'ACTIVE'
            GROUP BY extension_id, extension_name
            ORDER BY install_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findMostCommonExtensions(@Param("tenantId") String tenantId,
                                            @Param("limit") int limit);

    /**
     * Get recently installed extensions
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND ie.firstSeenAt >= :since ORDER BY ie.firstSeenAt DESC")
    List<InstalledExtension> findRecentlyInstalled(@Param("tenantId") String tenantId,
                                                    @Param("since") LocalDateTime since);

    // ===== Search Queries =====

    /**
     * Search extensions by name
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND LOWER(ie.extensionName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY ie.extensionName ASC")
    Page<InstalledExtension> searchByName(@Param("tenantId") String tenantId,
                                          @Param("searchTerm") String searchTerm,
                                          Pageable pageable);

    /**
     * Search extensions by extension ID or name
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND (LOWER(ie.extensionName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(ie.extensionId) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY ie.lastSeenAt DESC")
    Page<InstalledExtension> searchExtensions(@Param("tenantId") String tenantId,
                                              @Param("searchTerm") String searchTerm,
                                              Pageable pageable);

    // ===== Device Sync Queries =====

    /**
     * Find extensions not seen since a specific time (potentially uninstalled)
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.device.deviceId = :deviceId " +
            "AND ie.status = 'ACTIVE' AND ie.lastSeenAt < :since")
    List<InstalledExtension> findStaleExtensions(@Param("deviceId") String deviceId,
                                                  @Param("since") LocalDateTime since);

    /**
     * Get all extension IDs currently on a device
     */
    @Query("SELECT ie.extensionId FROM InstalledExtension ie " +
            "WHERE ie.device.deviceId = :deviceId AND ie.status = 'ACTIVE'")
    List<String> findActiveExtensionIds(@Param("deviceId") String deviceId);

    /**
     * Find all extensions for multiple devices (for device user details).
     */
    @Query("SELECT ie FROM InstalledExtension ie WHERE ie.device.deviceId IN :deviceIds " +
            "ORDER BY ie.extensionName ASC")
    List<InstalledExtension> findByDeviceDeviceIdInOrderByExtensionNameAsc(@Param("deviceIds") List<String> deviceIds);

    /**
     * Batch count total extensions per user for a set of userIds.
     * Eliminates per-user extension query N+1 in UserActivityService.enrichUserPage().
     * Returns rows of [userId (String), count (Long)].
     */
    @Query(value = """
            SELECT user_id, COUNT(*) as cnt
            FROM installed_extensions
            WHERE tenant_id = :tenantId
              AND user_id IN :userIds
            GROUP BY user_id
            """, nativeQuery = true)
    List<Object[]> countExtensionsByUserIds(@Param("tenantId") String tenantId,
                                             @Param("userIds") List<String> userIds);

    /**
     * Batch count HIGH risk extensions per user for a set of userIds.
     * Eliminates per-user extension query N+1 in UserActivityService.enrichUserPage().
     * Returns rows of [userId (String), count (Long)].
     */
    @Query(value = """
            SELECT user_id, COUNT(*) as cnt
            FROM installed_extensions
            WHERE tenant_id = :tenantId
              AND user_id IN :userIds
              AND risk_level = 'HIGH'
            GROUP BY user_id
            """, nativeQuery = true)
    List<Object[]> countHighRiskExtensionsByUserIds(@Param("tenantId") String tenantId,
                                                     @Param("userIds") List<String> userIds);

    // ===== Dashboard Aggregation Queries =====

    /**
     * Deduplicated extension inventory with aggregate counts.
     * Groups by extensionId and returns: extensionId, extensionName, latestVersion,
     * maxRiskLevel, maxRiskScore, installCount, activeCount, deviceCount, userCount,
     * firstSeenAt, lastSeenAt, isWhitelisted, isBlacklisted.
     */
    @Query(value = """
            SELECT extension_id,
                   MAX(extension_name) AS extension_name,
                   MAX(version) AS latest_version,
                   MAX(CASE risk_level
                       WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3
                       WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END) AS risk_ord,
                   MAX(risk_level) AS risk_level,
                   COALESCE(MAX(risk_score), 0) AS risk_score,
                   MAX(CASE policy_action
                       WHEN 'BLOCK' THEN 2 WHEN 'WARN' THEN 1 ELSE 0 END) AS policy_ord,
                   MAX(policy_action) AS policy_action,
                   COUNT(*) AS install_count,
                   COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_count,
                   COUNT(DISTINCT fk_device_id) AS device_count,
                   COUNT(DISTINCT user_id) AS user_count,
                   MIN(first_seen_at) AS first_seen_at,
                   MAX(last_seen_at) AS last_seen_at,
                   BOOL_OR(COALESCE(is_whitelisted, false)) AS is_whitelisted,
                   BOOL_OR(COALESCE(is_blacklisted, false)) AS is_blacklisted
            FROM installed_extensions
            WHERE tenant_id = :tenantId
            GROUP BY extension_id
            ORDER BY risk_ord DESC, install_count DESC
            """, nativeQuery = true)
    List<Object[]> getExtensionInventory(@Param("tenantId") String tenantId);

    /**
     * Get all distinct versions for extensions in a tenant.
     * Returns [extensionId, version, firstSeenAt, deviceCount].
     */
    @Query(value = """
            SELECT extension_id, version,
                   MIN(first_seen_at) AS first_seen,
                   COUNT(DISTINCT fk_device_id) AS device_count
            FROM installed_extensions
            WHERE tenant_id = :tenantId AND extension_id = :extensionId
            GROUP BY extension_id, version
            ORDER BY first_seen DESC
            """, nativeQuery = true)
    List<Object[]> getVersionHistory(@Param("tenantId") String tenantId,
                                     @Param("extensionId") String extensionId);

    /**
     * Consolidated status distribution counts.
     * Returns [status, count].
     */
    @Query(value = """
            SELECT status, COUNT(*) AS cnt
            FROM installed_extensions
            WHERE tenant_id = :tenantId
            GROUP BY status
            """, nativeQuery = true)
    List<Object[]> countByStatus(@Param("tenantId") String tenantId);

    /**
     * Count whitelisted extensions.
     */
    @Query("SELECT COUNT(DISTINCT ie.extensionId) FROM InstalledExtension ie " +
            "WHERE ie.tenantId = :tenantId AND ie.isWhitelisted = true")
    long countWhitelistedExtensions(@Param("tenantId") String tenantId);

    /**
     * Count blacklisted extensions.
     */
    @Query("SELECT COUNT(DISTINCT ie.extensionId) FROM InstalledExtension ie " +
            "WHERE ie.tenantId = :tenantId AND ie.isBlacklisted = true")
    long countBlacklistedExtensions(@Param("tenantId") String tenantId);

    /**
     * Bulk update policy action by extensionId list for a tenant.
     */
    @Modifying
    @Query("UPDATE InstalledExtension ie SET ie.policyAction = :action, ie.policyReason = :reason " +
            "WHERE ie.tenantId = :tenantId AND ie.extensionId IN :extensionIds")
    int bulkUpdatePolicyAction(@Param("tenantId") String tenantId,
                                @Param("extensionIds") List<String> extensionIds,
                                @Param("action") String action,
                                @Param("reason") String reason);

    /**
     * Bulk whitelist extensions.
     */
    @Modifying
    @Query("UPDATE InstalledExtension ie SET ie.isWhitelisted = true, ie.isBlacklisted = false, " +
            "ie.policyAction = 'ALLOW', ie.policyReason = :reason " +
            "WHERE ie.tenantId = :tenantId AND ie.extensionId IN :extensionIds")
    int bulkWhitelist(@Param("tenantId") String tenantId,
                       @Param("extensionIds") List<String> extensionIds,
                       @Param("reason") String reason);

    /**
     * Bulk blacklist extensions.
     */
    @Modifying
    @Query("UPDATE InstalledExtension ie SET ie.isBlacklisted = true, ie.isWhitelisted = false, " +
            "ie.policyAction = 'BLOCK', ie.policyReason = :reason " +
            "WHERE ie.tenantId = :tenantId AND ie.extensionId IN :extensionIds")
    int bulkBlacklist(@Param("tenantId") String tenantId,
                       @Param("extensionIds") List<String> extensionIds,
                       @Param("reason") String reason);

    /**
     * User-level extension risk aggregation for dashboard.
     * Returns [deviceUserId, userName, email, displayName, totalExtensions,
     *          highRiskExtensions, blockedExtensions, maxRiskScore, lastSeenAt].
     */
    @Query(value = """
            SELECT du.pk_device_user_id, du.user_name, du.email, du.display_name,
                   COUNT(DISTINCT ie.extension_id) AS total_extensions,
                   COUNT(DISTINCT ie.extension_id) FILTER (WHERE ie.risk_level IN ('HIGH', 'CRITICAL')) AS high_risk_ext,
                   COUNT(DISTINCT ie.extension_id) FILTER (WHERE ie.policy_action = 'BLOCK') AS blocked_ext,
                   COALESCE(MAX(ie.risk_score), 0) AS max_risk_score,
                   MAX(ie.last_seen_at) AS last_activity
            FROM device_user du
            JOIN devices d ON d.fk_device_user_id = du.pk_device_user_id
            JOIN installed_extensions ie ON ie.fk_device_id = d.device_id
            WHERE ie.tenant_id = :tenantId
            GROUP BY du.pk_device_user_id, du.user_name, du.email, du.display_name
            ORDER BY max_risk_score DESC
            """, nativeQuery = true)
    List<Object[]> getUserExtensionRiskProfiles(@Param("tenantId") String tenantId);

    /**
     * Top blocked extensions from installed_extensions (by policy_action = 'BLOCK').
     * Returns [extensionId, extensionName, blockCount, uniqueUsers].
     */
    @Query(value = """
            SELECT extension_id, MAX(extension_name) AS extension_name,
                   COUNT(*) AS block_count,
                   COUNT(DISTINCT user_id) AS unique_users
            FROM installed_extensions
            WHERE tenant_id = :tenantId AND policy_action = 'BLOCK'
            GROUP BY extension_id
            ORDER BY block_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopBlockedByPolicy(@Param("tenantId") String tenantId,
                                          @Param("limit") int limit);

    /**
     * Top warned extensions from installed_extensions (by policy_action = 'WARN').
     * Returns [extensionId, extensionName, warnCount, uniqueUsers].
     */
    @Query(value = """
            SELECT extension_id, MAX(extension_name) AS extension_name,
                   COUNT(*) AS warn_count,
                   COUNT(DISTINCT user_id) AS unique_users
            FROM installed_extensions
            WHERE tenant_id = :tenantId AND policy_action = 'WARN'
            GROUP BY extension_id
            ORDER BY warn_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopWarnedByPolicy(@Param("tenantId") String tenantId,
                                         @Param("limit") int limit);

    /**
     * Find duplicate extensions (same device + extension_id combination).
     * Returns all but the first record for each combination.
     */
    @Query(value = """
            SELECT pk_installed_extension_id FROM (
                SELECT pk_installed_extension_id,
                       ROW_NUMBER() OVER (PARTITION BY fk_device_id, extension_id ORDER BY first_seen_at ASC) as rn
                FROM installed_extensions
            ) sub WHERE rn > 1
            """, nativeQuery = true)
    List<String> findDuplicateExtensionIds();

    /**
     * Delete extensions by IDs.
     */
    @Modifying
    @Query("DELETE FROM InstalledExtension ie WHERE ie.pkInstalledExtensionId IN :ids")
    int deleteByIds(@Param("ids") List<String> ids);
}
