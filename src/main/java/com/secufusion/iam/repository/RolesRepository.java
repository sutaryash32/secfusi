package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RolesRepository extends JpaRepository<Roles, String> {
    Optional<Roles> findByNameAndTenant_TenantID(String name, String tenantId);

    Optional<Roles> existsByNameAndTenant_TenantID(String name, String tenantID);

    List<Roles> findByTenant_TenantID(String tenantID);

    @Query(value = """
            WITH RECURSIVE tenant_hierarchy (tenantid, parent_tenant_id) AS (
                        SELECT t.tenantid, t.parent_tenant_id
                        FROM tenant t
                        JOIN roles r ON r.fk_tenant_id = t.tenantid
                        WHERE r.pk_role_id = :roleId
                        UNION ALL
                        SELECT p.tenantid, p.parent_tenant_id
                        FROM tenant p
                        JOIN tenant_hierarchy th
                          ON p.tenantid = th.parent_tenant_id
                    )
                    SELECT r.*
                    FROM roles r
                    WHERE r.pk_role_id = :roleId
                      AND :requestingTenantId IN (SELECT tenantid FROM tenant_hierarchy); """,nativeQuery = true)
    Roles findRoleAccessibleByTenant(String roleId, String requestingTenantId
    );

    Optional<Roles> findByNameAndIsDefaultAndIsSuperRole(String roleName, char y, char y1);
}

