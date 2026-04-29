package com.secufusion.iam.service;

import com.secufusion.iam.dto.DeviceUserWithGroupsDto;
import com.secufusion.iam.dto.GroupMembershipStatsDto;
import com.secufusion.iam.dto.UserGroupMembershipStatsDto;
import com.secufusion.iam.entity.DeviceUser;
import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.entity.EventsGroupDeviceUserMapping;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.entity.PolicyAssignment;
import com.secufusion.iam.repository.DeviceUserRepository;
import com.secufusion.iam.repository.EventsGroupDeviceUserMappingRepository;
import com.secufusion.iam.repository.EventsGroupRepository;
import com.secufusion.iam.repository.PolicyAssignmentRepository;
import com.secufusion.iam.repository.projection.GroupMembershipStats;
import com.secufusion.iam.repository.projection.UserGroupMembershipStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final DeviceUserRepository deviceUserRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;

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

    /**
     * Get group membership statistics for all groups in tenant
     * Returns summary of how many users are in each group
     *
     * @param tenantId Tenant ID
     * @return List of GroupMembershipStatsDto
     */
    @Transactional(readOnly = true)
    public List<GroupMembershipStatsDto> getGroupMembershipStats(String tenantId) {
        log.debug("Fetching group membership statistics for tenant: {}", tenantId);

        List<GroupMembershipStats> stats =
                mappingRepository.getGroupMembershipStatsByTenant(tenantId);

        // Get all groups to fetch authorization status
        List<EventsGroup> groups = groupRepository.findByTenantId(tenantId);
        Map<String, EventsGroup> groupMap = groups.stream()
                .collect(Collectors.toMap(EventsGroup::getPkEventsGroupId, Function.identity()));

        return stats.stream()
                .map(stat -> {
                    EventsGroup group = groupMap.get(stat.getGroupId());
                    return GroupMembershipStatsDto.builder()
                            .groupId(stat.getGroupId())
                            .groupName(stat.getGroupName())
                            .groupType(stat.getGroupType().name())
                            .userCount(stat.getUserCount())
                            .authorized(group != null ? group.getAuthorized() : false)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get user group membership statistics for all users in tenant
     * Returns summary of how many groups each user belongs to
     *
     * @param tenantId Tenant ID
     * @return List of UserGroupMembershipStatsDto
     */
    @Transactional(readOnly = true)
    public List<UserGroupMembershipStatsDto> getUserGroupMembershipStats(String tenantId) {
        log.debug("Fetching user group membership statistics for tenant: {}", tenantId);

        List<UserGroupMembershipStats> stats =
                mappingRepository.getUserGroupMembershipStatsByTenant(tenantId);

        return stats.stream()
                .map(stat -> UserGroupMembershipStatsDto.builder()
                        .deviceUserId(stat.getDeviceUserId())
                        .email(stat.getEmail())
                        .displayName(stat.getDisplayName())
                        .userName(stat.getUserName())
                        .source(stat.getSource())
                        .groupCount(stat.getGroupCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get device user with groups by ID
     *
     * @param tenantId Tenant ID
     * @param deviceUserId Device user ID
     * @return DeviceUserWithGroupsDto
     */
    @Transactional(readOnly = true)
    public DeviceUserWithGroupsDto getDeviceUserWithGroups(String tenantId, String deviceUserId) {
        log.debug("Fetching device user with groups: {} for tenant: {}", deviceUserId, tenantId);

        // Get device user
        DeviceUser deviceUser = deviceUserRepository.findByIdAndTenantId(deviceUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device user not found"));

        // Get mappings
        List<EventsGroupDeviceUserMapping> mappings =
                mappingRepository.findByDeviceUserIdAndTenantId(deviceUserId, tenantId);

        List<DeviceUserWithGroupsDto.GroupMembershipInfo> groupInfos = mappings.stream()
                .map(m -> DeviceUserWithGroupsDto.GroupMembershipInfo.builder()
                        .groupId(m.getEventsGroup().getPkEventsGroupId())
                        .groupName(m.getEventsGroup().getName())
                        .groupType(m.getEventsGroup().getGroupType().name())
                        .authorized(m.getEventsGroup().getAuthorized())
                        .assignedAt(m.getAssignedAt())
                        .build())
                .collect(Collectors.toList());

        return DeviceUserWithGroupsDto.builder()
                .pkDeviceUserId(deviceUser.getPkDeviceUserId())
                .tenantId(deviceUser.getFkTenantId())
                .email(deviceUser.getEmail())
                .userName(deviceUser.getUserName())
                .displayName(deviceUser.getDisplayName())
                .status(deviceUser.getStatus())
                .firstSeenAt(deviceUser.getFirstSeenAt())
                .lastSeenAt(deviceUser.getLastSeenAt())
                .createdAt(deviceUser.getCreatedAt())
                .updatedAt(deviceUser.getUpdatedAt())
                .groupCount(groupInfos.size())
                .groups(groupInfos)
                .build();
    }

    /**
     * Resolve all policies for a device user based on their group memberships
     * This method returns all policies assigned to the groups that the user belongs to
     *
     * IMPORTANT: Only returns ONE policy per type (browser, network, extension)
     * If user belongs to multiple groups with conflicting policies, the FIRST policy found is returned
     *
     * @param tenantId Tenant ID
     * @param deviceUserId Device user ID
     * @return List of PolicyAssignment entities (max 3 items: 1 browser, 1 network, 1 extension)
     */
    @Transactional(readOnly = true)
    public List<PolicyAssignment> resolvePoliciesForDeviceUser(String tenantId, String deviceUserId) {
        log.debug("Resolving policies for device user: {} in tenant: {}", deviceUserId, tenantId);

        // Step 1: Get all group IDs for this device user
        List<String> groupIds = mappingRepository.findGroupIdsByDeviceUserId(deviceUserId);

        if (groupIds.isEmpty()) {
            log.debug("Device user {} has no group memberships, returning empty policy list", deviceUserId);
            return Collections.emptyList();
        }

        log.debug("Device user {} belongs to {} groups", deviceUserId, groupIds.size());

        // Step 2: Get all policy assignments for these groups
        List<PolicyAssignment> allPolicyAssignments =
                policyAssignmentRepository.findByEventsGroupIdsAndTenantId(groupIds, tenantId);

        log.debug("Found {} total policy assignments for user {} (before deduplication)",
                allPolicyAssignments.size(), deviceUserId);

        // Step 3: Deduplicate - keep only ONE policy per type (browser, network, extension)
        PolicyAssignment browserPolicy = null;
        PolicyAssignment networkPolicy = null;
        PolicyAssignment extensionPolicy = null;

        for (PolicyAssignment assignment : allPolicyAssignments) {
            // First browser policy wins
            if (browserPolicy == null && assignment.getBrowserPolicy() != null) {
                browserPolicy = assignment;
            }
            // First network policy wins
            if (networkPolicy == null && assignment.getNetworkPolicy() != null) {
                networkPolicy = assignment;
            }
            // First extension policy wins
            if (extensionPolicy == null && assignment.getExtensionPolicy() != null) {
                extensionPolicy = assignment;
            }

            // Early exit if we found all three types
            if (browserPolicy != null && networkPolicy != null && extensionPolicy != null) {
                break;
            }
        }

        // Step 4: Build final list with unique policies
        List<PolicyAssignment> uniquePolicies = new ArrayList<>();
        if (browserPolicy != null) uniquePolicies.add(browserPolicy);
        if (networkPolicy != null) uniquePolicies.add(networkPolicy);
        if (extensionPolicy != null) uniquePolicies.add(extensionPolicy);

        log.info("Resolved {} unique policies for device user {} (from {} groups): " +
                        "browser={}, network={}, extension={}",
                uniquePolicies.size(), deviceUserId, groupIds.size(),
                browserPolicy != null, networkPolicy != null, extensionPolicy != null);

        return uniquePolicies;
    }
}
