package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Serializable> {


    Optional<Tenant> findByTenantID(String id);
    Optional<Tenant> findByRealmName(String realmName);

    Optional<Tenant> findByTenantName(String tenantName);

    Optional<Tenant> findByDomain(String host);

    boolean existsByTenantName(String tenantName);

    boolean existsByDomain(String domainName);

    boolean existsByPhoneNo(String domainName);

    boolean existsByEmail(String email);


    Optional<Tenant> findByEmail(String email);

    List<Tenant> findByParentTenantId(String parentTenantId);


    @Query(value = """
        WITH RECURSIVE tenant_hierarchy (tenantid, parent_tenant_id) AS (
            SELECT t.tenantid, t.parent_tenant_id
            FROM tenant t
            WHERE t.tenantid = :targetTenantId
            UNION ALL
            SELECT p.tenantid, p.parent_tenant_id
            FROM tenant p
            JOIN tenant_hierarchy th
              ON p.tenantid = th.parent_tenant_id
        )
        SELECT CASE WHEN EXISTS (
          SELECT 1 FROM tenant_hierarchy th WHERE th.tenantid = :requestingTenantId
        ) THEN true ELSE false END
        """, nativeQuery = true)
    boolean isTenantAccessibleByAnother(@Param("requestingTenantId") String requestingTenantId,
                                        @Param("targetTenantId") String targetTenantId);
}
