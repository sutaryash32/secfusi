package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.NetworkPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface NetworkPolicyRepository
        extends JpaRepository<NetworkPolicy, String> {

    List<NetworkPolicy> findAllByFkTenantId(String tenantId);

    List<NetworkPolicy> findAllByFkTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Fetch currently active version of a network policy
     */
    Optional<NetworkPolicy> findByPolicyKeyAndIsActiveTrue(String policyKey);

    /**
     * Fetch all versions of a network policy (history)
     */
    List<NetworkPolicy> findByPolicyKeyOrderByCreatedAtAsc(String policyKey);

    Optional<NetworkPolicy> findTopByFkTenantIdAndIsActiveTrueOrderByVersionDesc(String tenantId);

    Optional<NetworkPolicy> findTopByFkTenantIdIsNull();

    Optional<NetworkPolicy> findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(String tenantId);

    /**
     * Fetch NetworkPolicy with all relations eagerly to avoid LazyInitializationException.
     */
    @Query("SELECT n FROM NetworkPolicy n " +
            "LEFT JOIN FETCH n.urlFilters " +
            "LEFT JOIN FETCH n.networkConfiguration " +
            "WHERE n.pkNetworkPolicyId = :id")
    Optional<NetworkPolicy> findByIdWithRelations(@Param("id") String id);

    /**
     * Fetch default/active NetworkPolicy for tenant with all relations eagerly loaded.
     */
    @Query("SELECT n FROM NetworkPolicy n " +
            "LEFT JOIN FETCH n.urlFilters " +
            "LEFT JOIN FETCH n.networkConfiguration " +
            "WHERE n.fkTenantId = :tenantId AND n.isActive = true " +
            "ORDER BY n.version DESC")
    List<NetworkPolicy> findActiveByTenantIdWithRelations(@Param("tenantId") String tenantId);

    // Dashboard statistics methods
    long countByFkTenantId(String tenantId);
    long countByFkTenantIdAndIsActiveTrue(String tenantId);
    long countByIsActiveTrue();
}