package com.secufusion.tenant.repository;


import com.secufusion.tenant.entity.TenantApiMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantApiMappingRepository extends JpaRepository<TenantApiMappingEntity, Long> {
    Optional<TenantApiMappingEntity> findByApiIdAndTenantId(Long apiId, String tenantId);
    List<TenantApiMappingEntity> findAll();
}
