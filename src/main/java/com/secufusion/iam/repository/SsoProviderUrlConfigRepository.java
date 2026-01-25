package com.secufusion.iam.repository;

import com.secufusion.iam.entity.SsoProviderUrlConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SsoProviderUrlConfigRepository extends JpaRepository<SsoProviderUrlConfig, String> {

    Optional<SsoProviderUrlConfig> findByProviderId(String providerId);

    Optional<SsoProviderUrlConfig> findByProviderIdAndEnabled(String providerId, Boolean enabled);

    List<SsoProviderUrlConfig> findByEnabled(Boolean enabled);
}
