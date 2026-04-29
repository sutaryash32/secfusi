package com.secufusion.events.repository;

import com.secufusion.events.entity.EventsGroupDeviceUserMapping;
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
     * Check if mapping exists
     *
     * @param deviceUserId Device user ID
     * @param groupId Events group ID
     * @return true if exists, false otherwise
     */
    boolean existsByFkDeviceUserIdAndFkEventsGroupId(String deviceUserId, String groupId);

    /**
     * Find all groups for a device user with tenant validation
     * Used for policy resolution
     *
     * @param deviceUserId Device user ID
     * @param tenantId Tenant ID
     * @return List of mappings
     */
    @Query("SELECT m FROM EventsGroupDeviceUserMapping m " +
            "JOIN m.eventsGroup g " +
            "WHERE m.fkDeviceUserId = :deviceUserId " +
            "AND g.tenantId = :tenantId")
    List<EventsGroupDeviceUserMapping> findActiveGroupsForDeviceUser(
            @Param("deviceUserId") String deviceUserId,
            @Param("tenantId") String tenantId
    );
}
