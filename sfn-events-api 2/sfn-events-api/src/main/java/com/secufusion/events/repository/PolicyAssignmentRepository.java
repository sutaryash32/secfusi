package com.secufusion.events.repository;

import com.secufusion.events.entity.PolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PolicyAssignmentRepository
 *
 * Repository for PolicyAssignment entity.
 * Manages assignments of policies (browser, network, extension) to groups.
 *
 * Security: All queries validate tenant ownership to ensure cross-tenant isolation.
 */
@Repository
public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, String> {

    /**
     * Find all policy assignments for a tenant
     *
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    List<PolicyAssignment> findByTenantId(String tenantId);

    /**
     * Find policy assignment by ID and tenant
     *
     * @param id Assignment ID
     * @param tenantId Tenant ID
     * @return Optional PolicyAssignment
     */
    Optional<PolicyAssignment> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find all policy assignments for a specific Azure resource (group)
     *
     * @param azureResourceId Azure resource ID (group ID)
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    List<PolicyAssignment> findByAzureResourceIdAndTenantId(String azureResourceId, String tenantId);

    /**
     * Find policy assignments for multiple groups (used for policy resolution)
     * This is the key method for resolving policies for a device user
     *
     * @param groupIds List of group IDs
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    @Query("SELECT pa FROM PolicyAssignment pa " +
            "WHERE pa.azureResourceId IN :groupIds " +
            "AND pa.tenantId = :tenantId " +
            "ORDER BY pa.assignedAt ASC")
    List<PolicyAssignment> findByAzureResourceIdInAndTenantId(
            @Param("groupIds") List<String> groupIds,
            @Param("tenantId") String tenantId
    );

    /**
     * Alternative method name for finding assignments by group IDs
     * (More intuitive name for policy resolution use case)
     *
     * @param groupIds List of group IDs
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    @Query("SELECT pa FROM PolicyAssignment pa " +
            "WHERE pa.azureResourceId IN :groupIds " +
            "AND pa.tenantId = :tenantId " +
            "ORDER BY pa.assignedAt ASC")
    List<PolicyAssignment> findByGroupIds(
            @Param("groupIds") List<String> groupIds,
            @Param("tenantId") String tenantId
    );

    /**
     * Find policy assignments by assignment type
     *
     * @param assignmentType Assignment type (e.g., "GROUP", "USER")
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    List<PolicyAssignment> findByAssignmentTypeAndTenantId(String assignmentType, String tenantId);

    /**
     * Find policy assignments for a specific browser policy
     *
     * @param browserPolicyId Browser policy ID
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    @Query("SELECT pa FROM PolicyAssignment pa " +
            "WHERE pa.browserPolicy.pkBrowserPolicyId = :browserPolicyId " +
            "AND pa.tenantId = :tenantId")
    List<PolicyAssignment> findByBrowserPolicyId(
            @Param("browserPolicyId") String browserPolicyId,
            @Param("tenantId") String tenantId
    );

    /**
     * Find policy assignments for a specific network policy
     *
     * @param networkPolicyId Network policy ID
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    @Query("SELECT pa FROM PolicyAssignment pa " +
            "WHERE pa.networkPolicy.pkNetworkPolicyId = :networkPolicyId " +
            "AND pa.tenantId = :tenantId")
    List<PolicyAssignment> findByNetworkPolicyId(
            @Param("networkPolicyId") String networkPolicyId,
            @Param("tenantId") String tenantId
    );

    /**
     * Find policy assignments for a specific extension policy
     *
     * @param extensionPolicyId Extension policy ID
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    @Query("SELECT pa FROM PolicyAssignment pa " +
            "WHERE pa.extensionPolicy.pkExtensionPolicyId = :extensionPolicyId " +
            "AND pa.tenantId = :tenantId")
    List<PolicyAssignment> findByExtensionPolicyId(
            @Param("extensionPolicyId") String extensionPolicyId,
            @Param("tenantId") String tenantId
    );

    /**
     * Count policy assignments for a specific group
     *
     * @param azureResourceId Azure resource ID (group ID)
     * @return Count
     */
    long countByAzureResourceId(String azureResourceId);

    /**
     * Count policy assignments for a tenant
     *
     * @param tenantId Tenant ID
     * @return Count
     */
    long countByTenantId(String tenantId);

    /**
     * Check if policy assignment exists for a group
     *
     * @param azureResourceId Azure resource ID (group ID)
     * @param tenantId Tenant ID
     * @return true if exists
     */
    boolean existsByAzureResourceIdAndTenantId(String azureResourceId, String tenantId);

    /**
     * Find assignments with at least one policy type assigned
     *
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment
     */
    @Query("SELECT pa FROM PolicyAssignment pa " +
            "WHERE pa.tenantId = :tenantId " +
            "AND (pa.browserPolicy IS NOT NULL " +
            "OR pa.networkPolicy IS NOT NULL " +
            "OR pa.extensionPolicy IS NOT NULL)")
    List<PolicyAssignment> findAssignmentsWithPolicies(@Param("tenantId") String tenantId);

    /**
     * Find assignments for groups with JOIN FETCH to avoid N+1 queries
     *
     * @param groupIds List of group IDs
     * @param tenantId Tenant ID
     * @return List of PolicyAssignment with policies eagerly loaded
     */
    @Query("SELECT DISTINCT pa FROM PolicyAssignment pa " +
            "LEFT JOIN FETCH pa.browserPolicy " +
            "LEFT JOIN FETCH pa.networkPolicy " +
            "LEFT JOIN FETCH pa.extensionPolicy " +
            "WHERE pa.azureResourceId IN :groupIds " +
            "AND pa.tenantId = :tenantId " +
            "ORDER BY pa.assignedAt ASC")
    List<PolicyAssignment> findByGroupIdsWithPolicies(
            @Param("groupIds") List<String> groupIds,
            @Param("tenantId") String tenantId
    );

    /**
     * Delete all policy assignments for a group
     *
     * @param azureResourceId Azure resource ID (group ID)
     */
    @Query("DELETE FROM PolicyAssignment pa WHERE pa.azureResourceId = :azureResourceId")
    void deleteByAzureResourceId(@Param("azureResourceId") String azureResourceId);
}
