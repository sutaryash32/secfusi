package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.ExtensionApiKey;
import com.secufusion.tenant.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExtensionApiKeyRepository extends JpaRepository<ExtensionApiKey, String> {

    Optional<ExtensionApiKey> findByKeyHash(String keyHash);

    List<ExtensionApiKey> findByStatusAndExpiresAtBefore(ApiKeyStatus status, LocalDateTime time);

    boolean existsByTenantIdAndStatus(String tenant, String apiKeyStatus);

    List<ExtensionApiKey> findByTenantId(String tenantId);

}
