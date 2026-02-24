package com.secufusion.iam.service;

import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.entity.EventsGroupDeviceUserMapping;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.EventsGroupDeviceUserMappingRepository;
import com.secufusion.iam.repository.EventsGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DeviceUserGroupMappingService
 *
 * Service for managing device user to events group mappings.
 * Handles assignment/removal of device users to/from groups.
 *
 * Business Rules:
 * - Device user can belong to multiple groups
 * - Group can contain multiple device users
 * - Duplicate assignments are prevented by unique constraint
 * - All operations enforce tenant security boundary
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceUserGroupMappingService {

    private final EventsGroupDeviceUserMappingRepository mappingRepository;
    private final EventsGroupRepository groupRepository;

    /**
     * Assign device user to events group
     *
     * @param tenantId Tenant ID
     * @param deviceUserId Device user ID
     * @param groupId Events group ID
     * @param assignedBy User who performed the assignment
     * @return Created mapping
     * @throws ResourceNotFoundException if group not found or doesn't belong to tenant
     * @throws IllegalStateException if mapping already exists
     */
    @Transactional
    public EventsGroupDeviceUserMapping assignDeviceUserToGroup(
            String tenantId,
            String deviceUserId,
            String groupId,
            String assignedBy) {

        log.info("Assigning device user: {} to group: {} for tenant: {}", deviceUserId, groupId, tenantId);

        // Validate group exists and belongs to tenant
        EventsGroup group = groupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Events group not found or doesn't belong to tenant"));

        // Check if mapping already exists
        if (mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(deviceUserId, groupId)) {
            log.warn("Device user: {} already assigned to group: {}", deviceUserId, groupId);
            // Return existing mapping instead of throwing exception
            return mappingRepository.findByFkDeviceUserIdAndFkEventsGroupId(deviceUserId, groupId)
                    .orElseThrow(() -> new IllegalStateException("Mapping should exist but was not found"));
        }

        // Create new mapping
        EventsGroupDeviceUserMapping mapping = new EventsGroupDeviceUserMapping();
        mapping.setPkMappingId(UUID.randomUUID().toString());
        mapping.setFkDeviceUserId(deviceUserId);
        mapping.setFkEventsGroupId(groupId);
        mapping.setAssignedBy(assignedBy);

        EventsGroupDeviceUserMapping saved = mappingRepository.save(mapping);
        log.info("Device user: {} assigned to group: {} (mapping ID: {})", deviceUserId, groupId, saved.getPkMappingId());
        return saved;
    }

    /**
     * Bulk assign multiple device users to a group
     *
     * @param tenantId Tenant ID
     * @param groupId Events group ID
     * @param deviceUserIds List of device user IDs
     * @param assignedBy User who performed the assignment
     * @return List of created mappings
     * @throws ResourceNotFoundException if group not found or doesn't belong to tenant
     */
    @Transactional
    public List<EventsGroupDeviceUserMapping> bulkAssignDeviceUsersToGroup(
            String tenantId,
            String groupId,
            List<String> deviceUserIds,
            String assignedBy) {

        log.info("Bulk assigning {} device users to group: {} for tenant: {}",
                deviceUserIds.size(), groupId, tenantId);

        // Validate group exists and belongs to tenant
        EventsGroup group = groupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Events group not found or doesn't belong to tenant"));

        List<EventsGroupDeviceUserMapping> createdMappings = new ArrayList<>();

        for (String deviceUserId : deviceUserIds) {
            try {
                // Check if mapping already exists
                if (mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(deviceUserId, groupId)) {
                    log.debug("Device user: {} already assigned to group: {}, skipping", deviceUserId, groupId);
                    continue;
                }

                // Create new mapping
                EventsGroupDeviceUserMapping mapping = new EventsGroupDeviceUserMapping();
                mapping.setPkMappingId(UUID.randomUUID().toString());
                mapping.setFkDeviceUserId(deviceUserId);
                mapping.setFkEventsGroupId(groupId);
                mapping.setAssignedBy(assignedBy);

                EventsGroupDeviceUserMapping saved = mappingRepository.save(mapping);
                createdMappings.add(saved);

            } catch (Exception e) {
                log.error("Failed to assign device user: {} to group: {}", deviceUserId, groupId, e);
                // Continue with other users instead of failing entire batch
            }
        }

        log.info("Bulk assignment completed. Created {} mappings for group: {}",
                createdMappings.size(), groupId);
        return createdMappings;
    }

    /**
     * Remove device user from events group
     *
     * @param tenantId Tenant ID
     * @param deviceUserId Device user ID
     * @param groupId Events group ID
     * @throws ResourceNotFoundException if group not found or doesn't belong to tenant
     */
    @Transactional
    public void removeDeviceUserFromGroup(String tenantId, String deviceUserId, String groupId) {
        log.info("Removing device user: {} from group: {} for tenant: {}", deviceUserId, groupId, tenantId);

        // Validate group exists and belongs to tenant
        EventsGroup group = groupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Events group not found or doesn't belong to tenant"));

        // Delete the mapping
        mappingRepository.deleteByDeviceUserAndGroup(deviceUserId, groupId);
        log.info("Device user: {} removed from group: {}", deviceUserId, groupId);
    }

    /**
     * Get all device users in a group
     *
     * @param tenantId Tenant ID
     * @param groupId Events group ID
     * @return List of mappings
     * @throws ResourceNotFoundException if group not found or doesn't belong to tenant
     */
    @Transactional(readOnly = true)
    public List<EventsGroupDeviceUserMapping> getDeviceUsersInGroup(String tenantId, String groupId) {
        log.debug("Fetching device users for group: {} in tenant: {}", groupId, tenantId);

        // Validate group exists and belongs to tenant
        groupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Events group not found or doesn't belong to tenant"));

        return mappingRepository.findByGroupIdAndTenantId(groupId, tenantId);
    }

    /**
     * Get all groups for a device user
     *
     * @param tenantId Tenant ID
     * @param deviceUserId Device user ID
     * @return List of groups
     */
    @Transactional(readOnly = true)
    public List<EventsGroup> getGroupsForDeviceUser(String tenantId, String deviceUserId) {
        log.debug("Fetching groups for device user: {} in tenant: {}", deviceUserId, tenantId);

        List<EventsGroupDeviceUserMapping> mappings = mappingRepository.findByDeviceUserIdAndTenantId(deviceUserId, tenantId);

        // Extract group IDs and fetch groups
        List<String> groupIds = mappings.stream()
                .map(EventsGroupDeviceUserMapping::getFkEventsGroupId)
                .toList();

        if (groupIds.isEmpty()) {
            return List.of();
        }

        return groupRepository.findByIdsAndTenantId(groupIds, tenantId);
    }

    /**
     * Get group IDs for a device user (for policy resolution)
     *
     * @param deviceUserId Device user ID
     * @return List of group IDs
     */
    @Transactional(readOnly = true)
    public List<String> getGroupIdsForDeviceUser(String deviceUserId) {
        log.debug("Fetching group IDs for device user: {}", deviceUserId);
        return mappingRepository.findGroupIdsByDeviceUserId(deviceUserId);
    }

    /**
     * Count device users in a group
     *
     * @param groupId Events group ID
     * @return Count of device users
     */
    @Transactional(readOnly = true)
    public long countDeviceUsersInGroup(String groupId) {
        return mappingRepository.countByFkEventsGroupId(groupId);
    }

    /**
     * Count groups for a device user
     *
     * @param deviceUserId Device user ID
     * @return Count of groups
     */
    @Transactional(readOnly = true)
    public long countGroupsForDeviceUser(String deviceUserId) {
        return mappingRepository.countByFkDeviceUserId(deviceUserId);
    }
}
