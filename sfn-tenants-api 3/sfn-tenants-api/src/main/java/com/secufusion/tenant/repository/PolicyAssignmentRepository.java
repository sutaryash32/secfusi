package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.PolicyAssignment;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, String> {

    // Find all roles assigned to a specific policy
    List<PolicyAssignment> findByBrowserPolicy_PkBrowserPolicyId(String policyId);

    // Find specific assignment to avoid duplicates
    boolean existsByBrowserPolicy_PkBrowserPolicyIdAndAzureResourceId(String policyId, String azureResourceId);

    // Delete all assignments for a policy (useful for "replace all" updates)
    void deleteByBrowserPolicy_PkBrowserPolicyId(String policyId);

    // NEW: Find all assignments for a specific tenant
    @Query("SELECT DISTINCT p FROM PolicyAssignment p " +
            "LEFT JOIN FETCH p.browserPolicy " +
            "LEFT JOIN FETCH p.networkPolicy " +
            "LEFT JOIN FETCH p.extensionPolicy " +
            "WHERE p.tenantId = :tenantId")
    List<PolicyAssignment> findByTenantId(@Param("tenantId") String tenantId);

    // NEW: Find specific assignment by ID and Tenant (Security check)
    @Query("SELECT p FROM PolicyAssignment p " +
            "LEFT JOIN FETCH p.browserPolicy " +
            "LEFT JOIN FETCH p.networkPolicy " +
            "LEFT JOIN FETCH p.extensionPolicy " +
            "WHERE p.id = :id AND p.tenantId = :tenantId")
    Optional<PolicyAssignment> findByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

    /**
     * Finds the first matching assignment where the Azure Resource ID is in the user's list of claims.
     * Uses LEFT JOIN FETCH to eagerly load policy relationships (avoids LazyInitializationException)
     */
    @Query("SELECT DISTINCT p FROM PolicyAssignment p " +
            "LEFT JOIN FETCH p.browserPolicy " +
            "LEFT JOIN FETCH p.networkPolicy " +
            "LEFT JOIN FETCH p.extensionPolicy " +
            "WHERE p.tenantId = :tenantId " +
            "AND ( " +
            "  (p.assignmentType = 'GROUP' AND p.azureResourceId IN :groupIds) " +
            "  OR " +
            "  (p.assignmentType = 'ROLE' AND p.azureResourceName IN :roleNames) " +
            ")")
    List<PolicyAssignment> findEffectiveAssignments(
            @Param("tenantId") String tenantId,
            @Param("groupIds") List<String> groupIds,
            @Param("roleNames") List<String> roleNames
    );

    void deleteByNetworkPolicy_PkNetworkPolicyId(String pkNetworkPolicyId);

    boolean existsByNetworkPolicy_PkNetworkPolicyIdAndAzureResourceId(String pkNetworkPolicyId, String id);

    void deleteByExtensionPolicy_PkExtensionPolicyId(String pkExtensionPolicyId);

    boolean existsByExtensionPolicy_PkExtensionPolicyIdAndAzureResourceId(String pkNetworkPolicyId, String id);


    void deleteByAzureResourceIdAndTenantId(String azureResourceId, String tenantId);

    // Type-scoped deletes — used by assignPoliciesToGroups so that only the policy
    // type(s) present in the request are wiped; unrelated types are left untouched.
    void deleteByAzureResourceIdAndTenantIdAndBrowserPolicyIsNotNull(String azureResourceId, String tenantId);

    void deleteByAzureResourceIdAndTenantIdAndNetworkPolicyIsNotNull(String azureResourceId, String tenantId);

    void deleteByAzureResourceIdAndTenantIdAndExtensionPolicyIsNotNull(String azureResourceId, String tenantId);


    // Dashboard statistics methods
    long countByTenantId(String tenantId);

    @Query("SELECT COUNT(DISTINCT pa.azureResourceId) FROM PolicyAssignment pa " +
            "WHERE pa.tenantId = :tenantId AND pa.assignmentType = :type")
    long countDistinctResourcesByTenantIdAndType(@Param("tenantId") String tenantId,
                                                 @Param("type") String type);

    @Query("SELECT COUNT(DISTINCT pa.azureResourceId) FROM PolicyAssignment pa " +
            "WHERE pa.assignmentType = :type")
    long countDistinctResourcesByType(@Param("type") String type);

    @Modifying
    @Transactional
    @Query(value = "UPDATE policy_assignments SET fk_extension_policy_id = :newPolicyId WHERE fk_extension_policy_id = :oldPolicyId",
            nativeQuery = true)
    void updateExtensionPolicyReference(
            @Param("oldPolicyId") String oldPolicyId,
            @Param("newPolicyId") String newPolicyId
    );

    @Modifying
    @Transactional
    @Query(value = "UPDATE policy_assignments SET fk_policy_id = :newPolicyId WHERE fk_policy_id = :oldPolicyId",
            nativeQuery = true)
    void updateBrowserPolicyReference(
            @Param("oldPolicyId") String oldPolicyId,
            @Param("newPolicyId") String newPolicyId
    );

    @Modifying
    @Transactional
    @Query(value = "UPDATE policy_assignments SET fk_network_policy_id = :newPolicyId WHERE fk_network_policy_id = :oldPolicyId",
            nativeQuery = true)
    void updateNetworkPolicyReference(
            @Param("oldPolicyId") String oldPolicyId,
            @Param("newPolicyId") String newPolicyId
    );

}
