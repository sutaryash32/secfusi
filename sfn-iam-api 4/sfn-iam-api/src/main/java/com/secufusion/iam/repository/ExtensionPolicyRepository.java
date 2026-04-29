package com.secufusion.iam.repository;

import com.secufusion.iam.entity.ExtensionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtensionPolicyRepository extends JpaRepository<ExtensionPolicy, String> {

    List<ExtensionPolicy> findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc(String tenantId);

    List<ExtensionPolicy> findAllByFkTenantIdIsNullAndIsActiveTrueOrderByCreatedAtAsc();

    Optional<ExtensionPolicy> findFirstByFkTenantIdAndIsActiveTrueAndIsTenantDefaultTrue(String tenantId);
}
