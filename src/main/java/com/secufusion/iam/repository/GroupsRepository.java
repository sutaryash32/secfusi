package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Groups;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupsRepository extends JpaRepository<Groups, Serializable> {
    Optional<Groups> findByNameAndTenantId(String name, String tenantId);

    Optional<Groups> findByTenantIdAndIsAdminAndIsDefault(
            String tenantId,
            Character isAdmin,
            Character isDefault
    );

    Optional<Groups> existsByName(String name);

    @Query(value = """
    WITH RECURSIVE tenant_hierarchy (tenantid, parent_tenant_id) AS (
        SELECT t.tenantid, t.parent_tenant_id
        FROM tenant t
        JOIN groups g ON g.fk_tenant_id = t.tenantid
        WHERE g.pk_group_id = :groupId
        UNION ALL
        SELECT p.tenantid, p.parent_tenant_id
        FROM tenant p
        JOIN tenant_hierarchy th
          ON p.tenantid = th.parent_tenant_id
    )
    SELECT g.*
    FROM groups g
    WHERE g.pk_group_id = :groupId
      AND :requestingTenantId IN (SELECT tenantid FROM tenant_hierarchy)
    """, nativeQuery = true)
    Groups findGroupAccessibleByTenant(String groupId, String requestingTenantId);

    List<Groups> findByTenantId(String tenantId);
}

