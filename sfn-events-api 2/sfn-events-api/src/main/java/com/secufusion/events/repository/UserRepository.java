package com.secufusion.events.repository;

import com.secufusion.events.entity.Tenant;
import com.secufusion.events.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Serializable> {

    Optional<User> findByEmailAndTenant_TenantID(String email, String tenantId);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserNameIgnoreCase(String preferred);

    // ===== User List Queries =====

    /**
     * Find all users for a tenant with pagination
     */
    Page<User> findByTenant_TenantIDOrderByUserNameAsc(String tenantId, Pageable pageable);

    /**
     * Find all users for a tenant
     */
    List<User> findByTenant_TenantIDOrderByUserNameAsc(String tenantId);

    /**
     * Find user by ID and tenant
     */
    Optional<User> findByPkUserIdAndTenant_TenantID(String userId, String tenantId);

    /**
     * Find user by keycloak user ID
     */
    Optional<User> findByKeycloakUserIdAndTenant_TenantID(String keycloakUserId, String tenantId);

    /**
     * Search users by name or email
     */
    @Query("SELECT u FROM User u WHERE u.tenant.tenantID = :tenantId " +
            "AND (LOWER(u.userName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY u.userName ASC")
    Page<User> searchUsers(@Param("tenantId") String tenantId,
                           @Param("searchTerm") String searchTerm,
                           Pageable pageable);

    /**
     * Count users by tenant
     */
    long countByTenant_TenantID(String tenantId);

    /**
     * Count active users by tenant
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.tenantID = :tenantId AND u.status = 'ACTIVE'")
    long countActiveUsers(@Param("tenantId") String tenantId);

    /**
     * Count distinct users that have at least one device (matched by userId or userName).
     * Replaces the all-users N+1 loop in getUserStats.
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u WHERE u.tenant.tenantID = :tenantId " +
            "AND EXISTS (SELECT d FROM Device d WHERE d.tenantId = :tenantId " +
            "AND (d.linkedUserId = u.pkUserId OR d.userName = u.userName))")
    long countUsersWithDevices(@Param("tenantId") String tenantId);

    /**
     * Count distinct users that have at least one HIGH risk extension.
     * Replaces the all-users N+1 loop in getUserStats.
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u WHERE u.tenant.tenantID = :tenantId " +
            "AND EXISTS (SELECT ie FROM InstalledExtension ie WHERE ie.tenantId = :tenantId " +
            "AND ie.userId = u.pkUserId AND ie.riskLevel = 'HIGH')")
    long countUsersWithHighRiskExtensions(@Param("tenantId") String tenantId);
}
