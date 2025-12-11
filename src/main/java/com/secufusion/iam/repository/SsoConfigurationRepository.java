package com.secufusion.iam.repository;

import com.secufusion.iam.entity.SsoConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface SsoConfigurationRepository extends JpaRepository<SsoConfiguration, Serializable> {
    List<SsoConfiguration> findByFkTenantId(String tenantID);

    Optional<SsoConfiguration> findByFkTenantIdAndAlias(String tenantId, String alias);

    Optional<SsoConfiguration> findByFkTenantIdAndActive(String tenantId, String active);
}
