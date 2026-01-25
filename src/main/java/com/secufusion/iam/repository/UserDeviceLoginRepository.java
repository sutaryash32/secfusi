package com.secufusion.iam.repository;

import com.secufusion.iam.entity.UserDeviceLogin;
import com.secufusion.iam.entity.UserDeviceLogin.DeviceLoginStatus;
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
public interface UserDeviceLoginRepository extends JpaRepository<UserDeviceLogin, Long> {

    /**
     * Find user-device login record by user and device fingerprint.
     */
    Optional<UserDeviceLogin> findByTenantIdAndUserIdAndDeviceFingerprint(
            String tenantId, String userId, String deviceFingerprint);

    /**
     * Find all devices for a user.
     */
    Page<UserDeviceLogin> findByTenantIdAndUserIdOrderByLastLoginAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find active devices for a user.
     */
    Page<UserDeviceLogin> findByTenantIdAndUserIdAndStatusOrderByLastLoginAtDesc(
            String tenantId, String userId, DeviceLoginStatus status, Pageable pageable);

    /**
     * Find trusted devices for a user.
     */
    List<UserDeviceLogin> findByTenantIdAndUserIdAndIsTrustedTrueOrderByLastLoginAtDesc(
            String tenantId, String userId);

    /**
     * Find blocked devices for a user.
     */
    List<UserDeviceLogin> findByTenantIdAndUserIdAndStatusOrderByBlockedAtDesc(
            String tenantId, String userId, DeviceLoginStatus status);

    /**
     * Count devices for a user.
     */
    long countByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Count active devices for a user.
     */
    long countByTenantIdAndUserIdAndStatus(String tenantId, String userId, DeviceLoginStatus status);

    /**
     * Find all devices for a tenant.
     */
    Page<UserDeviceLogin> findByTenantIdOrderByLastLoginAtDesc(String tenantId, Pageable pageable);

    /**
     * Find devices by status for a tenant.
     */
    Page<UserDeviceLogin> findByTenantIdAndStatusOrderByLastLoginAtDesc(
            String tenantId, DeviceLoginStatus status, Pageable pageable);

    /**
     * Find by device ID.
     */
    Optional<UserDeviceLogin> findByTenantIdAndDeviceId(String tenantId, String deviceId);

    /**
     * Find by device fingerprint.
     */
    List<UserDeviceLogin> findByTenantIdAndDeviceFingerprintOrderByLastLoginAtDesc(
            String tenantId, String deviceFingerprint);

    /**
     * Get devices not seen since a given time (inactive).
     */
    @Query("SELECT u FROM UserDeviceLogin u WHERE u.tenantId = :tenantId " +
            "AND u.status = 'ACTIVE' AND u.lastLoginAt < :since")
    List<UserDeviceLogin> findInactiveDevices(@Param("tenantId") String tenantId,
                                               @Param("since") LocalDateTime since);

    /**
     * Update device status.
     */
    @Modifying
    @Query("UPDATE UserDeviceLogin u SET u.status = :status, u.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE u.tenantId = :tenantId AND u.userId = :userId AND u.deviceFingerprint = :fingerprint")
    int updateDeviceStatus(@Param("tenantId") String tenantId,
                           @Param("userId") String userId,
                           @Param("fingerprint") String fingerprint,
                           @Param("status") DeviceLoginStatus status);

    /**
     * Trust a device.
     */
    @Modifying
    @Query("UPDATE UserDeviceLogin u SET u.isTrusted = true, u.trustedAt = CURRENT_TIMESTAMP, " +
            "u.trustedBy = :trustedBy, u.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE u.tenantId = :tenantId AND u.userId = :userId AND u.deviceFingerprint = :fingerprint")
    int trustDevice(@Param("tenantId") String tenantId,
                    @Param("userId") String userId,
                    @Param("fingerprint") String fingerprint,
                    @Param("trustedBy") String trustedBy);

    /**
     * Block a device.
     */
    @Modifying
    @Query("UPDATE UserDeviceLogin u SET u.status = 'BLOCKED', u.blockedAt = CURRENT_TIMESTAMP, " +
            "u.blockedBy = :blockedBy, u.blockReason = :reason, u.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE u.tenantId = :tenantId AND u.userId = :userId AND u.deviceFingerprint = :fingerprint")
    int blockDevice(@Param("tenantId") String tenantId,
                    @Param("userId") String userId,
                    @Param("fingerprint") String fingerprint,
                    @Param("blockedBy") String blockedBy,
                    @Param("reason") String reason);

    /**
     * Revoke all devices for a user.
     */
    @Modifying
    @Query("UPDATE UserDeviceLogin u SET u.status = 'REVOKED', u.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE u.tenantId = :tenantId AND u.userId = :userId")
    int revokeAllDevicesForUser(@Param("tenantId") String tenantId,
                                 @Param("userId") String userId);

    /**
     * Count unique users by device type.
     */
    @Query("SELECT u.deviceType, COUNT(DISTINCT u.userId) FROM UserDeviceLogin u " +
            "WHERE u.tenantId = :tenantId AND u.status = 'ACTIVE' " +
            "GROUP BY u.deviceType")
    List<Object[]> countUsersByDeviceType(@Param("tenantId") String tenantId);

    /**
     * Count unique users by browser type.
     */
    @Query("SELECT u.browserType, COUNT(DISTINCT u.userId) FROM UserDeviceLogin u " +
            "WHERE u.tenantId = :tenantId AND u.status = 'ACTIVE' " +
            "GROUP BY u.browserType")
    List<Object[]> countUsersByBrowserType(@Param("tenantId") String tenantId);

    /**
     * Get devices with high failed login counts (potential security issues).
     */
    @Query("SELECT u FROM UserDeviceLogin u WHERE u.tenantId = :tenantId " +
            "AND u.failedLoginCount >= :threshold " +
            "ORDER BY u.failedLoginCount DESC")
    List<UserDeviceLogin> findDevicesWithHighFailures(@Param("tenantId") String tenantId,
                                                       @Param("threshold") int threshold);

    /**
     * Search devices by name or OS.
     */
    @Query("SELECT u FROM UserDeviceLogin u WHERE u.tenantId = :tenantId " +
            "AND (LOWER(u.deviceName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.osInfo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.browserType) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY u.lastLoginAt DESC")
    Page<UserDeviceLogin> searchDevices(@Param("tenantId") String tenantId,
                                         @Param("searchTerm") String searchTerm,
                                         Pageable pageable);

    /**
     * Get total count of active devices.
     */
    long countByTenantIdAndStatus(String tenantId, DeviceLoginStatus status);

    /**
     * Get total count of trusted devices.
     */
    long countByTenantIdAndIsTrustedTrue(String tenantId);
}
