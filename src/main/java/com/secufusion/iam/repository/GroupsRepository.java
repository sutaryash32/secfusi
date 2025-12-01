package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Groups;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.Optional;

@Repository
public interface GroupsRepository extends JpaRepository<Groups, Serializable> {
    Optional<Groups> findByNameAndTenant_TenantID(String name, String tenantId);

    Optional<Groups> findByTenant_TenantIDAndIsAdminAndIsDefault(
            String tenantId,
            Character isAdmin,
            Character isDefault
    );

    Optional<Groups> existsByName(String name);
}

