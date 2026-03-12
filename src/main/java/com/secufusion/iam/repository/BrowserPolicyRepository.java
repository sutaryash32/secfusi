package com.secufusion.iam.repository;

import com.secufusion.iam.entity.BrowserPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrowserPolicyRepository extends JpaRepository<BrowserPolicy, String> {

    List<BrowserPolicy> findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc(String tenantId);

    List<BrowserPolicy> findAllByFkTenantIdIsNullAndIsActiveTrueOrderByCreatedAtAsc();
}
