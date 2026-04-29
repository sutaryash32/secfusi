package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.WebhookDeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, String> {

    Page<WebhookDeliveryLog> findByFkWebhookConfigIdOrderByCreatedAtDesc(String configId, Pageable pageable);

    Page<WebhookDeliveryLog> findByFkTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
}
