package com.secufusion.iam.repository;

import com.secufusion.iam.entity.AuthProviderConfig;
import com.secufusion.iam.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.Optional;

@Repository
public interface AuthProviderConfigRepository extends JpaRepository<AuthProviderConfig, Serializable> {
    Optional<AuthProviderConfig> findByTenant(Tenant tenant);
}
