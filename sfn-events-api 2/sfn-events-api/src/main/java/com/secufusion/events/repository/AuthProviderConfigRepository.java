package com.secufusion.events.repository;

import com.secufusion.events.entity.AuthProviderConfig;
import com.secufusion.events.entity.Tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.Optional;

@Repository
public interface AuthProviderConfigRepository extends JpaRepository<AuthProviderConfig, Serializable> {
    Optional<AuthProviderConfig> findByTenant(Tenant tenant);
}
