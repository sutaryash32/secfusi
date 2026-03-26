package com.secufusion.iam.repository;

import com.secufusion.iam.entity.NetworkPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkPolicyRepository extends JpaRepository<NetworkPolicy, String> {

    List<NetworkPolicy> findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc(String tenantId);

    List<NetworkPolicy> findAllByFkTenantIdIsNullAndIsActiveTrueOrderByCreatedAtAsc();

    Optional<NetworkPolicy> findFirstByFkTenantIdAndIsActiveTrueAndIsTenantDefaultTrue(String tenantId);
}
