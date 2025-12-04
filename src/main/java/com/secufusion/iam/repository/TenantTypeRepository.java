package com.secufusion.iam.repository;

import com.secufusion.iam.entity.TenantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantTypeRepository extends JpaRepository<TenantType, Long> {
}
