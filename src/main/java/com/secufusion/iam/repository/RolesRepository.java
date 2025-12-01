package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.Optional;

@Repository
public interface RolesRepository extends JpaRepository<Roles, String> {
    Optional<Roles> findByNameAndTenant_TenantID(String name, String tenantId);

    Optional<Roles> existsByNameAndTenant_TenantID(String name, String tenantID);
}

