package com.secufusion.events.repository;

import com.secufusion.events.entity.EventsGroup;
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
 * Manages both APIKEY groups (manually created) and AZURE groups (synced from Azure AD).
 *
 * Security: All queries validate tenant ownership to ensure cross-tenant isolation.
 */
@Repository
public interface EventsGroupRepository extends JpaRepository<EventsGroup, String> {

    /**
     * Find all groups for a tenant
     *
     * @param tenantId Tenant ID
     * @return List of EventsGroup
     */
    List<EventsGroup> findByTenantId(String tenantId);

    /**
     * Find group by ID and tenant (with tenant validation)
     *
     * @param groupId Group ID
     * @param tenantId Tenant ID
     * @return Optional EventsGroup
     */
    Optional<EventsGroup> findByPkEventsGroupIdAndTenantId(String groupId, String tenantId);

    /**
     * Find all groups by tenant and group type
     *
     * @param tenantId Tenant ID
     * @param groupType Group type (APIKEY_GROUP or AZURE_GROUP)
     * @return List of EventsGroup
     */
    List<EventsGroup> findByTenantIdAndGroupType(String tenantId, EventsGroup.GroupType groupType);

    /**
     * Find all groups by tenant, group type, and authorization status
     * Used to find authorized Azure groups for auto-assignment
     *
     * @param tenantId Tenant ID
     * @param groupType Group type
     * @param authorized Authorization status
     * @return List of EventsGroup
     */
    List<EventsGroup> findByTenantIdAndGroupTypeAndAuthorized(String tenantId,
                                                              EventsGroup.GroupType groupType,
                                                              Boolean authorized);

    /**
     * Find EventsGroup by Azure group ID and tenant
     * Used to check if Azure group is already synced
     *
     * @param tenantId Tenant ID
     * @param azureGroupId Azure AD group OID
     * @return Optional EventsGroup
     */
    Optional<EventsGroup> findByTenantIdAndAzureGroupId(String tenantId, String azureGroupId);

    /**
     * Find all active groups for a tenant
     *
     * @param tenantId Tenant ID
     * @return List of EventsGroup
     */
    @Query("SELECT g FROM EventsGroup g WHERE g.tenantId = :tenantId AND g.isActive = true")
    List<EventsGroup> findActiveGroups(@Param("tenantId") String tenantId);

    /**
     * Find all authorized groups for a tenant (both APIKEY and AZURE)
     *
     * @param tenantId Tenant ID
     * @return List of EventsGroup
     */
    @Query("SELECT g FROM EventsGroup g WHERE g.tenantId = :tenantId " +
            "AND g.authorized = true AND g.isActive = true")
    List<EventsGroup> findAuthorizedGroups(@Param("tenantId") String tenantId);

    /**
     * Find authorized Azure groups with their Azure group IDs
     *
     * @param tenantId Tenant ID
     * @return List of EventsGroup
     */
    @Query("SELECT g FROM EventsGroup g WHERE g.tenantId = :tenantId " +
            "AND g.groupType = 'AZURE_GROUP' " +
            "AND g.authorized = true " +
            "AND g.isActive = true " +
            "AND g.azureGroupId IS NOT NULL")
    List<EventsGroup> findAuthorizedAzureGroups(@Param("tenantId") String tenantId);

    /**
     * Find groups by name (case-insensitive search)
     *
     * @param tenantId Tenant ID
     * @param name Group name pattern
     * @return List of EventsGroup
     */
    @Query("SELECT g FROM EventsGroup g WHERE g.tenantId = :tenantId " +
            "AND LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<EventsGroup> searchByName(@Param("tenantId") String tenantId,
                                   @Param("name") String name);

    /**
     * Count groups by tenant
     *
     * @param tenantId Tenant ID
     * @return Count
     */
    long countByTenantId(String tenantId);

    /**
     * Count groups by tenant and type
     *
     * @param tenantId Tenant ID
     * @param groupType Group type
     * @return Count
     */
    long countByTenantIdAndGroupType(String tenantId, EventsGroup.GroupType groupType);

    /**
     * Count authorized groups by tenant
     *
     * @param tenantId Tenant ID
     * @return Count
     */
    @Query("SELECT COUNT(g) FROM EventsGroup g WHERE g.tenantId = :tenantId AND g.authorized = true")
    long countAuthorizedGroups(@Param("tenantId") String tenantId);

    /**
     * Check if group exists by name
     *
     * @param tenantId Tenant ID
     * @param name Group name
     * @return true if exists
     */
    boolean existsByTenantIdAndName(String tenantId, String name);

    /**
     * Find default group for tenant (if any)
     *
     * @param tenantId Tenant ID
     * @return Optional EventsGroup
     */
    Optional<EventsGroup> findByTenantIdAndIsDefaultTrue(String tenantId);
}
