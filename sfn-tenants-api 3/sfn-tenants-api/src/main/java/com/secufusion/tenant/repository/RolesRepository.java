package com.secufusion.tenant.repository;


import com.secufusion.tenant.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RolesRepository extends JpaRepository<Roles, String> {
    Optional<Roles> findByNameAndTenant_TenantID(String name, String tenantId);

    Optional<Roles> findByNameAndIsDefaultAndIsSuperRole(String roleName, char y, char y1);

    /**
     * Find a role by name, isDefault and isSuperRole with scopes AND scope tenantTypes eagerly loaded.
     * Prevents LazyInitializationException when accessing scopes/tenantTypes outside of session.
     */
    @Query("SELECT r FROM Roles r LEFT JOIN FETCH r.scopes s LEFT JOIN FETCH s.tenantTypes WHERE r.name = :name AND r.isDefault = :isDefault AND r.isSuperRole = :isSuperRole")
    Optional<Roles> findByNameAndIsDefaultAndIsSuperRoleWithScopes(
            @org.springframework.data.repository.query.Param("name") String name,
            @org.springframework.data.repository.query.Param("isDefault") char isDefault,
            @org.springframework.data.repository.query.Param("isSuperRole") char isSuperRole
    );

    void deleteByTenant_TenantID(String tenantId);

    // Dashboard statistics methods
    long countByTenant_TenantID(String tenantId);

    void deleteByTenant_TenantIDIn(List<String> targetIds);
}



