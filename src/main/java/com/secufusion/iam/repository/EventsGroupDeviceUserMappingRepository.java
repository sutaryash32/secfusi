package com.secufusion.iam.repository;

import com.secufusion.iam.entity.EventsGroupDeviceUserMapping;
import com.secufusion.iam.repository.projection.GroupMembershipStats;
import com.secufusion.iam.repository.projection.UserGroupMembershipStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EventsGroupDeviceUserMappingRepository
 *
 * Repository for EventsGroupDeviceUserMapping entity.
 * Manages many-to-many relationships between DeviceUser and EventsGroup.
 *
 * Security: All queries validate tenant ownership through joins to ensure cross-tenant isolation.
 */
@Repository
public interface EventsGroupDeviceUserMappingRepository extends JpaRepository<EventsGroupDeviceUserMapping, String> {

    /**
     * Find all mappings for a device user
     *
     * @param deviceUserId Device user ID
     * @return List of mappings
     */
    List<EventsGroupDeviceUserMapping> findByFkDeviceUserId(String deviceUserId);

    /**
     * Find all mappings for an events group
     *
     * @param groupId Events group ID
     * @return List of mappings
     */
    List<EventsGroupDeviceUserMapping> findByFkEventsGroupId(String groupId);

    /**
     * Find mapping by device user ID and group ID
     *
     * @param deviceUserId Device user ID
     * @param groupId Events group ID
     * @return Optional mapping
     */
    Optional<EventsGroupDeviceUserMapping> findByFkDeviceUserIdAndFkEventsGroupId(
        String deviceUserId,
        String groupId
    );

    /**
     * Check if mapping exists
     *
     * @param deviceUserId Device user ID
     * @param groupId Events group ID
     * @return true if exists, false otherwise
     */
    boolean existsByFkDeviceUserIdAndFkEventsGroupId(String deviceUserId, String groupId);

    /**
     * Delete mapping by device user ID and group ID
     *
     * @param deviceUserId Device user ID
     * @param groupId Events group ID
     */
    @Modifying
    @Query("DELETE FROM EventsGroupDeviceUserMapping m " +
           "WHERE m.fkDeviceUserId = :deviceUserId AND m.fkEventsGroupId = :groupId")
    void deleteByDeviceUserAndGroup(
        @Param("deviceUserId") String deviceUserId,
        @Param("groupId") String groupId
    );

    /**
     * Delete all mappings for a group
     *
     * @param groupId Events group ID
     */
    @Modifying
    @Query("DELETE FROM EventsGroupDeviceUserMapping m WHERE m.fkEventsGroupId = :groupId")
    void deleteByGroup(@Param("groupId") String groupId);

    /**
     * Delete all mappings for a device user
     *
     * @param deviceUserId Device user ID
     */
    @Modifying
    @Query("DELETE FROM EventsGroupDeviceUserMapping m WHERE m.fkDeviceUserId = :deviceUserId")
    void deleteByDeviceUser(@Param("deviceUserId") String deviceUserId);

    /**
     * Find group IDs for a device user (for policy resolution)
     *
     * @param deviceUserId Device user ID
     * @return List of group IDs
     */
    @Query("SELECT m.fkEventsGroupId FROM EventsGroupDeviceUserMapping m WHERE m.fkDeviceUserId = :deviceUserId")
    List<String> findGroupIdsByDeviceUserId(@Param("deviceUserId") String deviceUserId);

    /**
     * Find device user IDs for a group (for group membership queries)
     *
     * @param groupId Events group ID
     * @return List of device user IDs
     */
    @Query("SELECT m.fkDeviceUserId FROM EventsGroupDeviceUserMapping m WHERE m.fkEventsGroupId = :groupId")
    List<String> findDeviceUserIdsByGroupId(@Param("groupId") String groupId);

    /**
     * Count mappings for a device user
     *
     * @param deviceUserId Device user ID
     * @return Count of mappings
     */
    long countByFkDeviceUserId(String deviceUserId);

    /**
     * Count mappings for a group (member count)
     *
     * @param groupId Events group ID
     * @return Count of mappings
     */
    long countByFkEventsGroupId(String groupId);

    /**
     * Find mappings for device users with tenant validation
     * Security: Ensures device user belongs to specified tenant
     *
     * @param deviceUserId Device user ID
     * @param tenantId Tenant ID
     * @return List of mappings
     */
    @Query("SELECT m FROM EventsGroupDeviceUserMapping m " +
           "JOIN m.deviceUser du " +
           "JOIN m.eventsGroup g " +
           "WHERE m.fkDeviceUserId = :deviceUserId " +
           "AND du.tenantId = :tenantId " +
           "AND g.tenantId = :tenantId")
    List<EventsGroupDeviceUserMapping> findByDeviceUserIdAndTenantId(
        @Param("deviceUserId") String deviceUserId,
        @Param("tenantId") String tenantId
    );

    /**
     * Find mappings for group with tenant validation
     * Security: Ensures group belongs to specified tenant
     *
     * @param groupId Events group ID
     * @param tenantId Tenant ID
     * @return List of mappings
     */
    @Query("SELECT m FROM EventsGroupDeviceUserMapping m " +
           "JOIN m.eventsGroup g " +
           "WHERE m.fkEventsGroupId = :groupId " +
           "AND g.tenantId = :tenantId")
    List<EventsGroupDeviceUserMapping> findByGroupIdAndTenantId(
        @Param("groupId") String groupId,
        @Param("tenantId") String tenantId
    );
    /**
     * Get group membership statistics by tenant
     * Returns aggregated data: group_id, group_name, group_type, user_count
     *
     * @param tenantId Tenant ID
     * @return List of GroupMembershipStats projections
     */
    @Query("SELECT g.pkEventsGroupId as groupId, " +
           "g.name as groupName, " +
           "g.groupType as groupType, " +
           "COUNT(m.pkMappingId) as userCount " +
           "FROM EventsGroup g " +
           "LEFT JOIN EventsGroupDeviceUserMapping m ON g.pkEventsGroupId = m.fkEventsGroupId " +
           "WHERE g.tenantId = :tenantId " +
           "AND g.isActive = true " +
           "GROUP BY g.pkEventsGroupId, g.name, g.groupType")
    List<GroupMembershipStats> getGroupMembershipStatsByTenant(@Param("tenantId") String tenantId);

    /**
     * Get user group membership statistics by tenant
     * Returns aggregated data: device_user_id, email, display_name, user_name, source, group_count
     * Only counts authorized groups
     *
     * @param tenantId Tenant ID
     * @return List of UserGroupMembershipStats projections
     */
    @Query("SELECT du.pkDeviceUserId as deviceUserId, " +
           "du.email as email, " +
           "du.displayName as displayName, " +
           "du.userName as userName, " +
           "du.source as source, " +
           "COUNT(m.pkMappingId) as groupCount " +
           "FROM DeviceUser du " +
           "LEFT JOIN EventsGroupDeviceUserMapping m ON du.pkDeviceUserId = m.fkDeviceUserId " +
           "LEFT JOIN EventsGroup g ON m.fkEventsGroupId = g.pkEventsGroupId " +
           "WHERE du.tenantId = :tenantId " +
           "AND (du.status = 'ACTIVE' OR du.status IS NULL) " +
           "AND (g.authorized = true OR g.authorized IS NULL) " +
           "GROUP BY du.pkDeviceUserId, du.email, du.displayName, du.userName, du.source")
    List<UserGroupMembershipStats> getUserGroupMembershipStatsByTenant(@Param("tenantId") String tenantId);

    List<EventsGroupDeviceUserMapping> findByFkEventsGroupIdIn(List<String> groupIds);

    /**
     * Check if a user (by email) is a member of any authorized and active group within a tenant.
     * Joins DeviceUser → Mapping → EventsGroup to verify group membership.
     *
     * @param tenantId Tenant ID
     * @param email    User email
     * @return true if user exists in at least one authorized active group
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM EventsGroupDeviceUserMapping m " +
           "JOIN m.deviceUser du " +
           "JOIN m.eventsGroup g " +
           "WHERE du.tenantId = :tenantId " +
           "AND LOWER(du.email) = LOWER(:email) " +
           "AND g.tenantId = :tenantId " +
           "AND g.authorized = true " +
           "AND g.isActive = true")
    boolean existsInAuthorizedGroup(
        @Param("tenantId") String tenantId,
        @Param("email") String email
    );
}
