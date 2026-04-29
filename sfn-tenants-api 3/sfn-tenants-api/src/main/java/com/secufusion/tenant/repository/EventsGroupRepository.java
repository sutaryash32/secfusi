package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.EventsGroup;
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
     * Find default group for a tenant
     *
     * @param tenantId Tenant ID
     * @param isDefault Default flag
     * @return Optional EventsGroup
     */
    Optional<EventsGroup> findByTenantIdAndIsDefault(String tenantId, Boolean isDefault);

    List<EventsGroup> findAllByPkEventsGroupIdInAndTenantId(List<String> ids, String tenantId);

    /**
     * Find EventsGroup records by matching either azureGroupId or pkEventsGroupId.
     * This handles both Azure AD users (JWT groups = Azure OIDs) and API key users (JWT groups = EventsGroup IDs).
     */
    @Query("SELECT g FROM EventsGroup g WHERE g.tenantId = :tenantId " +
            "AND g.isActive = true AND g.authorized = true " +
            "AND (g.azureGroupId IN :identifiers OR g.pkEventsGroupId IN :identifiers)")
    List<EventsGroup> findByTenantIdAndIdentifiers(
            @Param("tenantId") String tenantId,
            @Param("identifiers") List<String> identifiers
    );

    void deleteByTenantId(String tenantId);

    /** Count all active groups for a tenant (used by selfManaged dashboard). */
    long countByTenantIdAndIsActive(String tenantId, Boolean isActive);

    /** Count active groups that are authorized for login (used by selfManaged dashboard). */
    long countByTenantIdAndIsActiveAndAuthorized(String tenantId, Boolean isActive, Boolean authorized);
}
