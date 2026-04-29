package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Serializable> {
    Optional<User> findByUserName(String userName);
    Optional<User> findByEmail(String email);

    List<User> findByTenant(Tenant t);

    Optional<User> findByUserNameAndTenant_TenantID(String userName, String tenantId);

    Optional<User> findByPhoneNoAndTenant_TenantID(String phoneNumber, String tenantId);

    Optional<User> findByEmailAndTenant_TenantID(String email, String tenantId);

    Optional<User> findByTenant_TenantIDAndDefaultUser(String tenantId, boolean defaultUser);

    boolean existsByUserName(String userName);

    boolean existsByEmail(String email);

    boolean existsByPhoneNo(String mobileNumber);

    Optional<User> findByPhoneNo(String adminPhoneNumber);
        List<User> findAllByPhoneNo(String phoneNo);

        @Query("SELECT new com.secufusion.iam.dto.UserPhoneCheckDto(" +
          "u.pkUserId, u.userName, u.email, u.phoneNo, u.firstName, u.lastName, u.status, u.createdAt) " +
          "FROM User u WHERE u.phoneNo = :phoneNumber AND (u.defaultUser = false OR u.defaultUser IS NULL)")
        List<com.secufusion.iam.dto.UserPhoneCheckDto> findPhoneCheckByPhoneNo(@Param("phoneNumber") String phoneNumber);

        @Query("SELECT new com.secufusion.iam.dto.UserPhoneCheckDto(" +
          "u.pkUserId, u.userName, u.email, u.phoneNo, u.firstName, u.lastName, u.status, u.createdAt) " +
          "FROM User u WHERE u.phoneNo = :phoneNumber AND u.tenant.tenantID = :tenantId " +
          "AND (u.defaultUser = false OR u.defaultUser IS NULL)")
        List<com.secufusion.iam.dto.UserPhoneCheckDto> findPhoneCheckByPhoneNoAndTenantId(
          @Param("phoneNumber") String phoneNumber,
          @Param("tenantId") String tenantId);

    @Query(
            value = """
        WITH RECURSIVE tenant_hierarchy AS (
            SELECT tenantid
            FROM tenant
            WHERE tenantid = :tenantId

            UNION ALL

            SELECT t.tenantid
            FROM tenant t
            JOIN tenant_hierarchy th
              ON t.parent_tenant_id = th.tenantid
        )
        SELECT u.*
        FROM users u
        JOIN tenant_hierarchy th
          ON u.fk_tenant_id = th.tenantid
        WHERE NOT (u.default_user = TRUE AND u.fk_tenant_id = :tenantId)
        """,
            nativeQuery = true
    )
    List<User> findAllUsersByTenantHierarchy(@Param("tenantId") String tenantId);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserNameIgnoreCase(String preferred);
}
