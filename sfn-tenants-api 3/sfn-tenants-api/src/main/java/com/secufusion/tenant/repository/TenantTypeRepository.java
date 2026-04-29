package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.TenantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantTypeRepository extends JpaRepository<TenantType, Long> {
}
