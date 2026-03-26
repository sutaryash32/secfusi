package com.secufusion.iam.repository;

import com.secufusion.iam.entity.EventsGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EventsGroupRepository
 *
 * Repository for EventsGroup entity.
 * Provides CRUD operations and queries for events groups (both APIKEY_GROUP and AZURE_GROUP) with tenant isolation.
 *
 * Security: All queries enforce tenant_id filtering to prevent cross-tenant access.
 */
@Repository
public interface EventsGroupRepository extends JpaRepository<EventsGroup, String> {

    /**
     * Find group by ID and tenant ID (security boundary enforcement)
     *
     * @param groupId Group ID
     * @param tenantId Tenant ID
     * @return Optional EventsGroup
     */
    @Query("SELECT g FROM EventsGroup g WHERE g.pkEventsGroupId = :groupId AND g.tenantId = :tenantId")
    Optional<EventsGroup> findByIdAndTenantId(
        @Param("groupId") String groupId,
        @Param("tenantId") String tenantId
    );

    /**
     * Find all groups for a tenant
     *
     * @param tenantId Tenant ID
     * @return List of EventsGroups
     */
    List<EventsGroup> findByTenantId(String tenantId);

    /**
     * Find all active groups for a tenant
     *
     * @param tenantId Tenant ID
     * @param isActive Active status
     * @return List of active EventsGroups
     */
    List<EventsGroup> findByTenantIdAndIsActive(String tenantId, Boolean isActive);

    /**
     * Find groups by tenant and group type
     *
     * @param tenantId Tenant ID
     * @param groupType Group type (APIKEY_GROUP or AZURE_GROUP)
     * @return List of EventsGroups
     */
    List<EventsGroup> findByTenantIdAndGroupType(String tenantId, EventsGroup.GroupType groupType);

    List<EventsGroup> findByTenantIdAndGroupTypeAndIsActive(String tenantId, EventsGroup.GroupType groupType, Boolean isActive);

    /**
     * Find authorized groups for a tenant (for policy assignment dropdowns)
     *
     * @param tenantId Tenant ID
     * @param authorized Authorization status
     * @return List of authorized EventsGroups
     */
    List<EventsGroup> findByTenantIdAndAuthorized(String tenantId, Boolean authorized);

    List<EventsGroup> findByTenantIdAndAuthorizedAndIsActive(String tenantId, Boolean authorized, Boolean isActive);

    /**
     * Find authorized groups of specific type
     *
     * @param tenantId Tenant ID
     * @param authorized Authorization status
     * @param groupType Group type
     * @return List of authorized EventsGroups
     */
    List<EventsGroup> findByTenantIdAndAuthorizedAndGroupType(
        String tenantId,
        Boolean authorized,
        EventsGroup.GroupType groupType
    );

    List<EventsGroup> findByTenantIdAndAuthorizedAndGroupTypeAndIsActive(
        String tenantId,
        Boolean authorized,
        EventsGroup.GroupType groupType,
        Boolean isActive
    );

    /**
     * Find group by name and tenant ID
     *
     * @param tenantId Tenant ID
     * @param name Group name
     * @return Optional EventsGroup
     */
    Optional<EventsGroup> findByTenantIdAndName(String tenantId, String name);

    /**
     * Find group by Azure group ID and tenant ID
     *
     * @param tenantId Tenant ID
     * @param azureGroupId Azure AD group object ID
     * @return Optional EventsGroup
     */
    Optional<EventsGroup> findByTenantIdAndAzureGroupId(String tenantId, String azureGroupId);

    /**
     * Check if group exists by name and tenant ID
     *
     * @param tenantId Tenant ID
     * @param name Group name
     * @return true if exists, false otherwise
     */
    boolean existsByTenantIdAndName(String tenantId, String name);

    /**
     * Check if Azure group exists by Azure group ID and tenant ID
     *
     * @param tenantId Tenant ID
     * @param azureGroupId Azure AD group object ID
     * @return true if exists, false otherwise
     */
    boolean existsByTenantIdAndAzureGroupId(String tenantId, String azureGroupId);

    /**
     * Check if group exists by ID and tenant ID
     *
     * @param groupId Group ID
     * @param tenantId Tenant ID
     * @return true if exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM EventsGroup g " +
           "WHERE g.pkEventsGroupId = :groupId AND g.tenantId = :tenantId")
    boolean existsByIdAndTenantId(
        @Param("groupId") String groupId,
        @Param("tenantId") String tenantId
    );

    /**
     * Find groups by IDs and tenant ID (for bulk operations with security boundary)
     *
     * @param groupIds List of group IDs
     * @param tenantId Tenant ID
     * @return List of EventsGroups
     */
    @Query("SELECT g FROM EventsGroup g WHERE g.pkEventsGroupId IN :groupIds AND g.tenantId = :tenantId")
    List<EventsGroup> findByIdsAndTenantId(
        @Param("groupIds") List<String> groupIds,
        @Param("tenantId") String tenantId
    );

    /**
     * Count groups for a tenant
     *
     * @param tenantId Tenant ID
     * @return Count of groups
     */
    long countByTenantId(String tenantId);

    /**
     * Count active groups for a tenant
     *
     * @param tenantId Tenant ID
     * @param isActive Active status
     * @return Count of active groups
     */
    long countByTenantIdAndIsActive(String tenantId, Boolean isActive);

    /**
     * Count authorized groups for a tenant
     *
     * @param tenantId Tenant ID
     * @param authorized Authorization status
     * @return Count of authorized groups
     */
    long countByTenantIdAndAuthorized(String tenantId, Boolean authorized);

    /**
     * Get the last sync time for Azure groups in a tenant
     * Returns the most recent syncedAt timestamp
     *
     * @param tenantId Tenant ID
     * @param groupType Group type (AZURE_GROUP)
     * @return Last sync timestamp or null if never synced
     */
    @Query("SELECT MAX(g.syncedAt) FROM EventsGroup g " +
           "WHERE g.tenantId = :tenantId " +
           "AND g.groupType = :groupType " +
           "AND g.syncedAt IS NOT NULL")
    java.time.Instant findLastSyncTimeByTenantIdAndGroupType(
        @Param("tenantId") String tenantId,
        @Param("groupType") EventsGroup.GroupType groupType
    );

    /**
     * Find all groups by tenant, type, and authorization status
     * Used for Azure group on-demand fetch to find already authorized groups
     *
     * @param tenantId Tenant ID
     * @param groupType Group type (APIKEY_GROUP or AZURE_GROUP)
     * @param authorized Authorization status
     * @return List of EventsGroups matching criteria
     */
    List<EventsGroup> findByTenantIdAndGroupTypeAndAuthorized(
        String tenantId,
        EventsGroup.GroupType groupType,
        Boolean authorized
    );

    /**
     * Check if any of the given Azure group IDs are authorized and active for a tenant.
     * Used during extension login to verify Azure AD group membership.
     *
     * @param tenantId      Tenant ID
     * @param azureGroupIds List of Azure AD group object IDs from JWT token
     * @return true if at least one matching authorized active group exists
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM EventsGroup g " +
           "WHERE g.tenantId = :tenantId " +
           "AND g.azureGroupId IN :azureGroupIds " +
           "AND g.authorized = true " +
           "AND g.isActive = true")
    boolean existsAuthorizedAzureGroup(
        @Param("tenantId") String tenantId,
        @Param("azureGroupIds") List<String> azureGroupIds
    );
}
