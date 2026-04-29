package com.secufusion.events.repository;

import com.secufusion.events.entity.DeviceUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.net.ContentHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceUserRepository extends JpaRepository<DeviceUser, String> {

    /**
     * Find device user by tenant and email.
     */
    Optional<DeviceUser> findByTenantIdAndEmail(String tenantId, String email);

    /**
     * Find device user by tenant and username.
     */
    Optional<DeviceUser> findByTenantIdAndUserName(String tenantId, String userName);

    /**
     * Find device user by tenant and email or username.
     */
    @Query("SELECT du FROM DeviceUser du WHERE du.tenantId = :tenantId " +
           "AND (du.email = :identifier OR du.userName = :identifier)")
    Optional<DeviceUser> findByTenantIdAndEmailOrUserName(
            @Param("tenantId") String tenantId,
            @Param("identifier") String identifier);

    /**
     * Find all device users for a tenant.
     */
    Page<DeviceUser> findByTenantIdOrderByLastSeenAtDesc(String tenantId, Pageable pageable);

    /**
     * Find device users by status.
     */
    Page<DeviceUser> findByTenantIdAndStatusOrderByLastSeenAtDesc(
            String tenantId, String status, Pageable pageable);

    /**
     * Count device users by tenant.
     */
    long countByTenantId(String tenantId);

    /**
     * Count device users by tenant and status.
     */
    long countByTenantIdAndStatus(String tenantId, String status);

    /**
     * Count device users linked to portal users.
     */
    @Query("SELECT COUNT(du) FROM DeviceUser du WHERE du.tenantId = :tenantId " +
           "AND du.portalUserId IS NOT NULL")
    long countLinkedToPortal(@Param("tenantId") String tenantId);

    /**
     * Count device users NOT linked to portal users (extension-only users).
     */
    @Query("SELECT COUNT(du) FROM DeviceUser du WHERE du.tenantId = :tenantId " +
           "AND du.portalUserId IS NULL")
    long countExtensionOnlyUsers(@Param("tenantId") String tenantId);

    /**
     * Find recent device users.
     */
    @Query("SELECT du FROM DeviceUser du WHERE du.tenantId = :tenantId " +
           "ORDER BY du.lastSeenAt DESC LIMIT :limit")
    List<DeviceUser> findRecentUsers(@Param("tenantId") String tenantId, @Param("limit") int limit);

    /**
     * Find device users active since a given time.
     */
    @Query("SELECT du FROM DeviceUser du WHERE du.tenantId = :tenantId " +
           "AND du.lastSeenAt >= :since ORDER BY du.lastSeenAt DESC")
    List<DeviceUser> findActiveUsersSince(
            @Param("tenantId") String tenantId,
            @Param("since") LocalDateTime since);

    /**
     * Search device users by email or name.
     */
    @Query("SELECT du FROM DeviceUser du WHERE du.tenantId = :tenantId " +
           "AND (LOWER(du.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(du.userName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(du.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<DeviceUser> searchUsers(
            @Param("tenantId") String tenantId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    List<DeviceUser> findByTenantIdAndStatus(String tenantId, String active);
}
