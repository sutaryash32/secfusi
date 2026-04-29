package com.secufusion.tenant.repository;


import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Serializable> {
    Optional<User> findByUserName(String userName);
    Optional<User> findByEmail(String email);

    List<User> findByTenant(Tenant t);

    Optional<User> findByEmailAndTenant_TenantID(String email, String tenantId);

    /**
     * Load user with all associations needed by the JWT filter in a single query.
     * Eagerly fetches: tenant, mappedGroups, mappedRoles, scopes, and scope tenantTypes.
     * Prevents LazyInitializationException when the filter runs outside a Hibernate session.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.tenant
            LEFT JOIN FETCH u.mappedGroups g
            LEFT JOIN FETCH g.mappedRoles r
            LEFT JOIN FETCH r.scopes s
            LEFT JOIN FETCH s.tenantTypes
            WHERE u.email = :email AND u.tenant.tenantID = :tenantId
            """)
    Optional<User> findByEmailAndTenantIdWithScopes(
            @Param("email") String email,
            @Param("tenantId") String tenantId
    );

    @Query("""
        select count(g) from User u join u.mappedGroups g
        where u.pkUserId = :userId and g.pkGroupId = :groupId
    """)
    long countUserGroupMapping(@Param("userId") String userId, @Param("groupId") String groupId);

    /**
     * Direct SQL insert into user_group_map, bypassing JPA collection management.
     * Prevents Hibernate duplicate insert when the session flushes dirty mappedGroups.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO user_group_map (fk_user_id, fk_group_id) VALUES (:userId, :groupId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void insertUserGroupMappingIfAbsent(@Param("userId") String userId, @Param("groupId") String groupId);

    Optional<User> findByPhoneNo(String adminPhoneNumber);

    Optional<User> findByEmailIgnoreCase(String candidate);

    Optional<User> findByUserNameIgnoreCase(String candidate);

    Optional<User> findByTenant_TenantIDAndDefaultUser(String tenantId, boolean b);

    // Dashboard statistics methods
    long countByTenant_TenantID(String tenantId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.tenantID = :tenantId AND u.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") String tenantId, @Param("status") String status);

    // Platform Admin dashboard methods (across all tenants)
    long countByStatus(String status);

    // Hierarchy user counts for MSSP / MASTER_MSSP dashboards

    /** Count users in all direct child tenants (enterprises under an MSSP). */
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.parentTenantId = :parentTenantId")
    long countByTenant_ParentTenantId(@Param("parentTenantId") String parentTenantId);

    /**
     * Count users in tenants whose parentTenantId is in the given list.
     * Used by MASTER_MSSP to sum users across enterprises under all child MSSPs.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.parentTenantId IN :parentIds")
    long countByTenantParentTenantIdIn(@Param("parentIds") List<String> parentIds);

    void deleteByTenant(Tenant tenant);

    void deleteByTenantIn(List<Tenant> targets);

    // Notification system methods
    Optional<User> findByKeycloakUserId(String keycloakUserId);

    List<User> findByKeycloakUserIdIn(List<String> keycloakUserIds);

    List<User> findByTenant_TenantID(String tenantId);

    // Role-based notification targeting: find users whose groups contain a role with the given name
    @Query("SELECT DISTINCT u FROM User u JOIN u.mappedGroups g JOIN g.mappedRoles r " +
           "WHERE u.tenant.tenantID = :tenantId AND r.name = :roleName")
    List<User> findByTenantIdAndRoleName(@Param("tenantId") String tenantId,
                                          @Param("roleName") String roleName);
    @Query("""
       SELECT u
       FROM User u
       WHERE u.tenant.tenantID = :tenantId
       AND u.defaultUser = true
       """)
    Optional<User> findDefaultAdminByTenantId(@Param("tenantId") String tenantId);
}
