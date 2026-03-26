package com.secufusion.iam.repository;

import com.secufusion.iam.entity.PolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, String> {

    @Modifying
    @Query("DELETE FROM PolicyAssignment p " +
           "WHERE p.azureResourceId = :groupId")
    void deleteByEventsGroupId(@Param("groupId") String groupId);

    /**
     * Find all policy assignments for a specific events group
     *
     * @param groupId Events group ID
     * @param tenantId Tenant ID for security
     * @return List of policy assignments
     */
    @Query("SELECT p FROM PolicyAssignment p " +
           "WHERE p.azureResourceId = :groupId " +
           "AND p.fkTenantId = :tenantId")
    List<PolicyAssignment> findByEventsGroupIdAndTenantId(
        @Param("groupId") String groupId,
        @Param("tenantId") String tenantId
    );

    /**
     * Count policy assignments for a group
     *
     * @param groupId Events group ID
     * @param tenantId Tenant ID for security
     * @return Count of assignments
     */
    @Query("SELECT COUNT(p) FROM PolicyAssignment p " +
           "WHERE p.azureResourceId = :groupId " +
           "AND p.fkTenantId = :tenantId")
    long countByEventsGroupIdAndTenantId(
        @Param("groupId") String groupId,
        @Param("tenantId") String tenantId
    );

    /**
     * Check if any policy assignment exists for a group
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PolicyAssignment p " +
           "WHERE p.azureResourceId = :groupId AND p.fkTenantId = :tenantId")
    boolean existsByGroupIdAndTenantId(
        @Param("groupId") String groupId,
        @Param("tenantId") String tenantId
    );

    /**
     * Find all policy assignments for multiple groups in a single query (batch fetch)
     *
     * @param groupIds List of events group IDs
     * @param tenantId Tenant ID for security
     * @return List of policy assignments for all groups
     */
    @Query("SELECT p FROM PolicyAssignment p " +
           "WHERE p.azureResourceId IN :groupIds " +
           "AND p.fkTenantId = :tenantId")
    List<PolicyAssignment> findByEventsGroupIdsAndTenantId(
        @Param("groupIds") List<String> groupIds,
        @Param("tenantId") String tenantId
    );

}