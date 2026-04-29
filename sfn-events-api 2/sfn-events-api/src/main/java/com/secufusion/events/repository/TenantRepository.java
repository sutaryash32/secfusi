package com.secufusion.events.repository;

import com.secufusion.events.entity.Tenant;
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

    Optional<Tenant> findByTenantName(String tenantName);

    Optional<Tenant> findByDomain(String host);

    /**
     * Find tenant by tenant code (for MSI deployments)
     */
    Optional<Tenant> findByTenantCode(String tenantCode);

    /**
     * Find tenant by domain (case-insensitive) for email-based auth
     */
    @Query("SELECT t FROM Tenant t WHERE LOWER(t.domain) = LOWER(:domain)")
    Optional<Tenant> findByDomainIgnoreCase(@Param("domain") String domain);

    /**
     * Find tenant by tenant code (case-insensitive)
     */
    @Query("SELECT t FROM Tenant t WHERE LOWER(t.tenantCode) = LOWER(:tenantCode)")
    Optional<Tenant> findByTenantCodeIgnoreCase(@Param("tenantCode") String tenantCode);
}
