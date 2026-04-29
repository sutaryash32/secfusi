package com.secufusion.events.repository;

import com.secufusion.events.entity.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, String> {

    Optional<SmtpConfig> findByFkTenantIdAndIsActiveTrue(String tenantId);

    Optional<SmtpConfig> findByFkTenantIdIsNullAndIsActiveTrue();
}
