package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.BrowserPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BrowserPolicyRepository extends JpaRepository<BrowserPolicy, String> {

    Optional<BrowserPolicy> findTopByFkTenantIdAndIsActiveTrueOrderByVersionDesc(String tenantId);

    Optional<BrowserPolicy> findTopByFkTenantIdIsNullAndNameIgnoreCase(String name);

    Optional<BrowserPolicy> findTopByFkTenantIdIsNull();

    List<BrowserPolicy> findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(String tenantId);

    Optional<BrowserPolicy> findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(String tenantId);

    // Dashboard statistics methods
    long countByFkTenantId(String tenantId);
    long countByFkTenantIdAndIsActive(String tenantId, boolean isActive);
    long countByIsActive(boolean isActive);
    long countByFkTenantIdAndCreatedAtAfter(String tenantId, LocalDateTime since);
    long countByFkTenantIdAndUpdatedAtAfterAndCreatedAtBefore(String tenantId, LocalDateTime updatedAfter, LocalDateTime createdBefore);
    long countByCreatedAtAfter(LocalDateTime since);
    long countByUpdatedAtAfterAndCreatedAtBefore(LocalDateTime updatedAfter, LocalDateTime createdBefore);
}
