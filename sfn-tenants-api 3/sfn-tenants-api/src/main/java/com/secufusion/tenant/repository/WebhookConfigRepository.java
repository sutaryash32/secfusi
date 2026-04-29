package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, String> {

    List<WebhookConfig> findByFkTenantIdAndIsActiveTrueOrderByNameAsc(String tenantId);

    List<WebhookConfig> findByFkTenantIdOrderByNameAsc(String tenantId);

    Optional<WebhookConfig> findByPkWebhookConfigIdAndFkTenantId(String configId, String tenantId);

    boolean existsByFkTenantIdAndName(String tenantId, String name);
}
