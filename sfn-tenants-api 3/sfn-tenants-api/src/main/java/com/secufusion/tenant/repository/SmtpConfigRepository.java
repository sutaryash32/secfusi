package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, String> {

    /**
     * Find SMTP config by tenant ID.
     */
    Optional<SmtpConfig> findByFkTenantId(String tenantId);

    /**
     * Find active SMTP config by tenant ID.
     */
    Optional<SmtpConfig> findByFkTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Find default SMTP config (fkTenantId is NULL).
     */
    Optional<SmtpConfig> findByFkTenantIdIsNull();

    /**
     * Find active default SMTP config.
     */
    Optional<SmtpConfig> findByFkTenantIdIsNullAndIsActiveTrue();

    /**
     * Check if SMTP config exists for tenant.
     */
    boolean existsByFkTenantId(String tenantId);

    /**
     * Delete SMTP config by tenant ID.
     */
    void deleteByFkTenantId(String tenantId);
}
