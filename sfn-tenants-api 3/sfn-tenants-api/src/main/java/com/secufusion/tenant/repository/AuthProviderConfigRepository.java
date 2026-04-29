package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.AuthProviderConfig;
import com.secufusion.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuthProviderConfigRepository extends JpaRepository<AuthProviderConfig, Serializable> {
    Optional<AuthProviderConfig> findByTenant(Tenant tenant);

    void deleteByTenant(Tenant tenant);

    void deleteByTenantIn(List<Tenant> targets);

    Optional<AuthProviderConfig> findByTenant_TenantID(String tenantID);
}
