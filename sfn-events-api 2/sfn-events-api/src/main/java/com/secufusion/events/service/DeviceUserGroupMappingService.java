package com.secufusion.events.service;

import com.secufusion.events.entity.DeviceUser;
import com.secufusion.events.entity.EventsGroup;
import com.secufusion.events.entity.EventsGroupDeviceUserMapping;
import com.secufusion.events.entity.PolicyAssignment;
import com.secufusion.events.repository.DeviceUserRepository;
import com.secufusion.events.repository.EventsGroupDeviceUserMappingRepository;
import com.secufusion.events.repository.EventsGroupRepository;
import com.secufusion.events.repository.PolicyAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

    private final DeviceUserRepository deviceUserRepository;
    private final EventsGroupRepository eventsGroupRepository;
    private final EventsGroupDeviceUserMappingRepository mappingRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;


    /**
     * Auto-assign Azure user to groups based on JWT group claims
     * Creates or updates DeviceUser and assigns to ALL matching groups (authorized or not)
     *
     * NEW BEHAVIOR:
     * - Always creates mappings for all matching Azure groups (ignores authorization status)
     * - Handles both creation and updates (not just first time)
     * - Creates mappings even if no policies are assigned to the groups
     *
     * @param tenantId Tenant ID
     * @param userId User ID from JWT 'sub' claim (not stored, just for logging)
     * @param email Email from JWT (unique identifier)
     * @param displayName Display name from JWT
     * @param azureGroupIds List of Azure AD group OIDs from JWT 'groups' claim
     * @return DeviceUser entity (created or found)
     */
    @Transactional
    public DeviceUser autoAssignAzureUserToGroups(String tenantId, String userId,
                                                  String email, String displayName,
                                                  List<String> azureGroupIds) {
        log.info("Auto-assigning Azure user to groups: tenant={}, email={}, groupCount={}",
                tenantId, email, azureGroupIds != null ? azureGroupIds.size() : 0);

        // 1. Find or create DeviceUser by tenant + email (unique constraint)
        DeviceUser deviceUser = deviceUserRepository
                .findByTenantIdAndEmail(tenantId, email)
                .orElseGet(() -> {
                    log.info("Creating new DeviceUser for email: {}", email);
                    DeviceUser newUser = DeviceUser.builder()
                            .tenantId(tenantId)
                            .email(email)
                            .userName(email)                    // Use email as userName
                            .displayName(displayName)
                            .source("AZURE")                    // Set source
                            .status("ACTIVE")                   // Use status instead of isActive
                            .firstSeenAt(LocalDateTime.now())   // Track first seen
                            .lastSeenAt(LocalDateTime.now())    // Track last seen
                            .build();
                    return deviceUserRepository.save(newUser);
                });

        // 2. Always update last seen timestamp and display name for existing users
        deviceUser.setLastSeenAt(LocalDateTime.now());
        deviceUser.setDisplayName(displayName); // Update display name in case it changed
        deviceUserRepository.save(deviceUser);
        log.debug("Updated DeviceUser for email: {}", email);

        // If no Azure group IDs provided, just return the device user
        if (azureGroupIds == null || azureGroupIds.isEmpty()) {
            log.debug("No Azure group IDs provided for user: {}", email);
            return deviceUser;
        }

        // 3. Get ALL Azure groups for this tenant (IGNORE authorization status)
        List<EventsGroup> allAzureGroups = eventsGroupRepository
                .findByTenantIdAndGroupType(
                        tenantId,
                        EventsGroup.GroupType.AZURE_GROUP
                );

        if (allAzureGroups.isEmpty()) {
            log.debug("No Azure groups found for tenant: {}", tenantId);
            return deviceUser;
        }

        // 4. Filter to groups matching JWT group claims
        List<EventsGroup> matchingGroups = allAzureGroups.stream()
                .filter(group -> azureGroupIds.contains(group.getAzureGroupId()))
                .collect(Collectors.toList());

        log.info("Found {} matching Azure groups for user {} out of {} JWT groups and {} total Azure groups",
                matchingGroups.size(), email, azureGroupIds.size(), allAzureGroups.size());

        // 5. Create or update mappings for ALL matching groups (regardless of authorization or policy status)
        int newMappingsCount = 0;
        int existingMappingsCount = 0;

        for (EventsGroup group : matchingGroups) {
            boolean exists = mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    deviceUser.getPkDeviceUserId(),
                    group.getPkEventsGroupId()
            );

            if (!exists) {
                // Create new mapping
                EventsGroupDeviceUserMapping mapping = EventsGroupDeviceUserMapping.builder()
                        .pkMappingId(UUID.randomUUID().toString())
                        .fkEventsGroupId(group.getPkEventsGroupId())
                        .fkDeviceUserId(deviceUser.getPkDeviceUserId())
                        .assignedAt(LocalDateTime.now())
                        .assignedBy("SYSTEM_AUTO")
                        .build();

                mappingRepository.save(mapping);
                newMappingsCount++;

                log.info("Created mapping: user {} to group: {} (authorized={}, groupId={})",
                        email, group.getName(), group.getAuthorized(), group.getPkEventsGroupId());
            } else {
                existingMappingsCount++;
                log.debug("Mapping already exists for user {} and group {}",
                        email, group.getName());
            }
        }

        log.info("Auto-assignment complete for user {}: {} new mappings, {} existing mappings",
                email, newMappingsCount, existingMappingsCount);

        return deviceUser;
    }

    /**
     * Resolve policies for device user (one per type: browser, network, extension)
     * Returns list of policy assignments using "first wins" strategy
     *
     * @param tenantId Tenant ID
     * @param deviceUserId Device User ID
     * @return List of policy assignments (max 3: one per type)
     */
    @Transactional(readOnly = true)
    public List<PolicyAssignment> resolvePoliciesForDeviceUser(String tenantId, String deviceUserId) {
        log.debug("Resolving policies for device user: {}", deviceUserId);

        // 1. Get all active groups for this device user
        List<EventsGroupDeviceUserMapping> mappings =
                mappingRepository.findActiveGroupsForDeviceUser(deviceUserId, tenantId);

        if (mappings.isEmpty()) {
            log.debug("No group mappings found for device user: {}", deviceUserId);
            return Collections.emptyList();
        }

        // 2. Get group IDs
        List<String> groupIds = mappings.stream()
                .map(EventsGroupDeviceUserMapping::getFkEventsGroupId)
                .collect(Collectors.toList());

        log.debug("Found {} groups for device user: {}", groupIds.size(), deviceUserId);

        // 3. Get all policy assignments for these groups
        List<PolicyAssignment> allPolicyAssignments =
                policyAssignmentRepository.findByAzureResourceIdInAndTenantId(groupIds, tenantId);

        if (allPolicyAssignments.isEmpty()) {
            log.debug("No policy assignments found for device user groups");
            return Collections.emptyList();
        }

        log.debug("Found {} total policy assignments for {} groups",
                allPolicyAssignments.size(), groupIds.size());

        // 4. Deduplicate: ONE policy per type (first wins)
        List<PolicyAssignment> deduplicatedPolicies = new ArrayList<>();
        PolicyAssignment browserPolicy = null;
        PolicyAssignment networkPolicy = null;
        PolicyAssignment extensionPolicy = null;

        for (PolicyAssignment assignment : allPolicyAssignments) {
            if (browserPolicy == null && assignment.getBrowserPolicy() != null) {
                browserPolicy = assignment;
                deduplicatedPolicies.add(assignment);
                log.debug("Selected browser policy: {} from group: {}",
                        assignment.getBrowserPolicy().getName(),
                        assignment.getAzureResourceName());
            }
            if (networkPolicy == null && assignment.getNetworkPolicy() != null) {
                networkPolicy = assignment;
                deduplicatedPolicies.add(assignment);
                log.debug("Selected network policy: {} from group: {}",
                        assignment.getNetworkPolicy().getName(),
                        assignment.getAzureResourceName());
            }
            if (extensionPolicy == null && assignment.getExtensionPolicy() != null) {
                extensionPolicy = assignment;
                deduplicatedPolicies.add(assignment);
                log.debug("Selected extension policy: {} from group: {}",
                        assignment.getExtensionPolicy().getName(),
                        assignment.getAzureResourceName());
            }

            // Stop if we have all three types
            if (browserPolicy != null && networkPolicy != null && extensionPolicy != null) {
                break;
            }
        }

        log.info("Resolved {} policies for device user: {} (B:{}, N:{}, E:{})",
                deduplicatedPolicies.size(), deviceUserId,
                browserPolicy != null, networkPolicy != null, extensionPolicy != null);

        return deduplicatedPolicies;
    }
}
