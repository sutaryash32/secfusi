package com.secufusion.iam.repository;

import com.secufusion.iam.entity.ExtensionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtensionPolicyRepository extends JpaRepository<ExtensionPolicy, String> {

    List<ExtensionPolicy> findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc(String tenantId);
}
