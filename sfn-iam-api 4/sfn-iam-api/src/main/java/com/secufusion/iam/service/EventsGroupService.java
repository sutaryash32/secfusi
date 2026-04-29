package com.secufusion.iam.service;

import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.EventsGroupDeviceUserMappingRepository;
import com.secufusion.iam.repository.EventsGroupRepository;
import com.secufusion.iam.repository.PolicyAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.DuplicateResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * EventsGroupService
 *
 * Service for managing events groups (both APIKEY_GROUP and AZURE_GROUP).
 * Provides CRUD operations with tenant isolation and business rule enforcement.
 *
 * Business Rules:
 * - Group names must be unique within tenant
 * - Default group cannot be deleted
 * - All operations enforce tenant security boundary
 * - Deleting group removes all user mappings and policy assignments
 * - Azure groups default to unauthorized=false (require admin approval)
 * - API key groups default to authorized=true (automatically authorized)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventsGroupService {

    private final EventsGroupRepository groupRepository;
    private final EventsGroupDeviceUserMappingRepository mappingRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;

    /**
     * Create new API key group (manually created by admin)
     *
     * @param tenantId Tenant ID
     * @param name Group name
     * @param description Group description
     * @param createdBy Creator identifier
     * @return Created group
     * @throws DuplicateResourceException if group name already exists in tenant
     */
    @Transactional
    public EventsGroup createEventsGroup(String tenantId, String name, String description, String createdBy) {
        log.info("Creating API key group '{}' for tenant: {}", name, tenantId);

        // Check for duplicate name
        if (groupRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new DuplicateResourceException("Group with name '" + name + "' already exists");
        }

        EventsGroup group = new EventsGroup();
        group.setPkEventsGroupId(UUID.randomUUID().toString());
        group.setTenantId(tenantId);
        group.setName(name);
        group.setDescription(description);
        group.setGroupType(EventsGroup.GroupType.APIKEY_GROUP);
        group.setAuthorized(true); // APIKEY_GROUP defaults to authorized
        group.setIsDefault(false);
        group.setIsActive(true);
        group.setCreatedBy(createdBy);

        EventsGroup saved = groupRepository.save(group);
        log.info("API key group created: {} with name: {}", saved.getPkEventsGroupId(), name);
        return saved;
    }

    /**
     * Create or update Azure AD group (synced from Microsoft Graph API)
     *
     * @param tenantId Tenant ID
     * @param azureGroupId Azure AD group object ID (OID)
     * @param azureGroupDisplayName Display name from Azure AD
     * @param syncedBy Sync identifier (usually "AZURE_SYNC_SERVICE")
     * @return Created or updated group
     */
    @Transactional
    public EventsGroup createOrUpdateAzureGroup(
            String tenantId,
            String azureGroupId,
            String azureGroupDisplayName,
            String syncedBy) {

        log.info("Syncing Azure group '{}' (ID: {}) for tenant: {}", azureGroupDisplayName, azureGroupId, tenantId);

        return groupRepository.findByTenantIdAndAzureGroupId(tenantId, azureGroupId)
            .map(existingGroup -> {
                // Update existing Azure group
                existingGroup.setAzureGroupDisplayName(azureGroupDisplayName);
                existingGroup.setSyncedAt(Instant.now());
                existingGroup.setUpdatedBy(syncedBy);
                existingGroup.setIsActive(true); // Reactivate if was soft-deleted

                EventsGroup updated = groupRepository.save(existingGroup);
                log.info("Azure group updated: {} ({})", azureGroupDisplayName, azureGroupId);
                return updated;
            })
            .orElseGet(() -> {
                // Create new Azure group
                EventsGroup azureGroup = new EventsGroup();
                azureGroup.setPkEventsGroupId(UUID.randomUUID().toString());
                azureGroup.setTenantId(tenantId);
                azureGroup.setName(azureGroupDisplayName); // Use display name as-is
                azureGroup.setDescription("Synced from Azure AD");
                azureGroup.setGroupType(EventsGroup.GroupType.AZURE_GROUP);
                azureGroup.setAuthorized(false); // AZURE_GROUP defaults to unauthorized
                azureGroup.setAzureGroupId(azureGroupId);
                azureGroup.setAzureGroupDisplayName(azureGroupDisplayName);
                azureGroup.setSyncedAt(Instant.now());
                azureGroup.setIsDefault(false);
                azureGroup.setIsActive(true);
                azureGroup.setCreatedBy(syncedBy);

                EventsGroup saved = groupRepository.save(azureGroup);
                log.info("Azure group created: {} ({})", azureGroupDisplayName, azureGroupId);
                return saved;
            });
    }

    /**
     * Authorize a group (allow it to appear in policy assignment dropdowns)
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID
     * @param authorizedBy Admin identifier
     * @return Updated group
     * @throws ResourceNotFoundException if group not found
     */
    @Transactional
    public EventsGroup authorizeGroup(String tenantId, String groupId, String authorizedBy) {
        log.info("Authorizing group: {} for tenant: {}", groupId, tenantId);

        EventsGroup group = getGroupById(tenantId, groupId);
        group.setAuthorized(true);
        group.setUpdatedBy(authorizedBy);

        EventsGroup updated = groupRepository.save(group);
        log.info("Group authorized: {} ({})", group.getName(), groupId);
        return updated;
    }

    /**
     * Unauthorize a group (remove from policy assignment dropdowns)
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID
     * @param unauthorizedBy Admin identifier
     * @return Updated group
     * @throws ResourceNotFoundException if group not found
     * @throws IllegalStateException if attempting to unauthorize default group
     */
    @Transactional
    public EventsGroup unauthorizeGroup(String tenantId, String groupId, String unauthorizedBy) {
        log.info("Unauthorizing group: {} for tenant: {}", groupId, tenantId);

        EventsGroup group = getGroupById(tenantId, groupId);

        // Prevent unauthorizing default group
        if (group.getIsDefault()) {
            throw new IllegalStateException("Cannot unauthorize default group");
        }

        group.setAuthorized(false);
        group.setUpdatedBy(unauthorizedBy);

        EventsGroup updated = groupRepository.save(group);
        log.info("Group unauthorized: {} ({})", group.getName(), groupId);
        return updated;
    }

    /**
     * Get all groups for tenant
     *
     * @param tenantId Tenant ID
     * @return List of groups
     */
    @Transactional(readOnly = true)
    public List<EventsGroup> getAllGroups(String tenantId) {
        log.debug("Fetching all events groups for tenant: {}", tenantId);
        return groupRepository.findByTenantIdAndIsActive(tenantId, true);
    }

    /**
     * Get active groups for tenant
     *
     * @param tenantId Tenant ID
     * @return List of active groups
     */
    @Transactional(readOnly = true)
    public List<EventsGroup> getActiveGroups(String tenantId) {
        log.debug("Fetching active events groups for tenant: {}", tenantId);
        return groupRepository.findByTenantIdAndIsActive(tenantId, true);
    }

    /**
     * Get authorized groups for tenant (for policy assignment dropdowns)
     *
     * @param tenantId Tenant ID
     * @return List of authorized groups
     */
    @Transactional(readOnly = true)
    public List<EventsGroup> getAuthorizedGroups(String tenantId) {
        log.debug("Fetching authorized events groups for tenant: {}", tenantId);
        return groupRepository.findByTenantIdAndAuthorizedAndIsActive(tenantId, true, true);
    }

    /**
     * Get authorized groups of specific type
     *
     * @param tenantId Tenant ID
     * @param groupType Group type (APIKEY_GROUP or AZURE_GROUP)
     * @return List of authorized groups
     */
    @Transactional(readOnly = true)
    public List<EventsGroup> getAuthorizedGroupsByType(String tenantId, EventsGroup.GroupType groupType) {
        log.debug("Fetching authorized {} groups for tenant: {}", groupType, tenantId);
        return groupRepository.findByTenantIdAndAuthorizedAndGroupTypeAndIsActive(tenantId, true, groupType, true);
    }

    /**
     * Get groups by type
     *
     * @param tenantId Tenant ID
     * @param groupType Group type (APIKEY_GROUP or AZURE_GROUP)
     * @return List of groups
     */
    @Transactional(readOnly = true)
    public List<EventsGroup> getGroupsByType(String tenantId, EventsGroup.GroupType groupType) {
        log.debug("Fetching {} groups for tenant: {}", groupType, tenantId);
        return groupRepository.findByTenantIdAndGroupTypeAndIsActive(tenantId, groupType, true);
    }

    /**
     * Get group by ID with tenant validation
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID
     * @return EventsGroup
     * @throws ResourceNotFoundException if group not found or doesn't belong to tenant
     */
    @Transactional(readOnly = true)
    public EventsGroup getGroupById(String tenantId, String groupId) {
        log.debug("Fetching events group: {} for tenant: {}", groupId, tenantId);
        return groupRepository.findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Events group not found"));
    }

    /**
     * Update group details
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID
     * @param name New group name (optional)
     * @param description New description (optional)
     * @param updatedBy Updater identifier
     * @return Updated group
     * @throws ResourceNotFoundException if group not found
     * @throws DuplicateResourceException if new name already exists
     * @throws IllegalStateException if attempting to update Azure group name
     */
    @Transactional
    public EventsGroup updateGroup(String tenantId, String groupId, String name, String description, String updatedBy) {
        log.info("Updating events group: {} for tenant: {}", groupId, tenantId);

        EventsGroup group = getGroupById(tenantId, groupId);

        // Prevent updating Azure group name (should only be updated via sync)
        if (group.getGroupType() == EventsGroup.GroupType.AZURE_GROUP && name != null && !name.equals(group.getName())) {
            throw new IllegalStateException("Cannot manually update Azure group name. Use sync instead.");
        }

        // Check if name is being changed and if new name already exists
        if (name != null && !name.equals(group.getName())) {
            if (groupRepository.existsByTenantIdAndName(tenantId, name)) {
                throw new DuplicateResourceException("Group with name '" + name + "' already exists");
            }
            group.setName(name);
        }

        if (description != null) {
            group.setDescription(description);
        }

        group.setUpdatedBy(updatedBy);

        EventsGroup updated = groupRepository.save(group);
        log.info("Events group updated: {}", groupId);
        return updated;
    }

    /**
     * Soft delete group (set is_active to false)
     * Cannot delete default group
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID
     * @throws ResourceNotFoundException if group not found
     * @throws IllegalStateException if attempting to delete default group
     */
    @Transactional
    public void deleteGroup(String tenantId, String groupId) {
        log.info("Deleting events group: {} for tenant: {}", groupId, tenantId);

        EventsGroup group = getGroupById(tenantId, groupId);

        // Prevent deletion of default group
        if (group.getIsDefault()) {
            throw new IllegalStateException("Cannot delete default events group");
        }

        // Soft delete the group
        group.setIsActive(false);
        groupRepository.save(group);

        // Remove all user mappings for this group
        mappingRepository.deleteByGroup(groupId);

        // Remove all policy assignments for this group
        policyAssignmentRepository.deleteByEventsGroupId(groupId);

        log.info("Events group soft-deleted: {} (mappings and policy assignments removed)", groupId);
    }

    /**
     * Check if group exists and belongs to tenant
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID
     * @return true if exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean exists(String tenantId, String groupId) {
        return groupRepository.existsByIdAndTenantId(groupId, tenantId);
    }

    /**
     * Authorize an Azure AD group and persist it to database
     * This is the ONLY way Azure groups enter the database
     *
     * @param tenantId Tenant ID
     * @param azureGroupId Azure AD group OID
     * @param azureGroupDisplayName Display name from Azure AD
     * @param authorizedBy Admin who authorized the group
     * @return Persisted EventsGroup entity
     */
    @Transactional
    public EventsGroup authorizeAndPersistAzureGroup(
            String tenantId,
            String azureGroupId,
            String azureGroupDisplayName,
            String authorizedBy) {

        log.info("Authorizing and persisting Azure group: {} for tenant: {}", azureGroupId, tenantId);

        // Check if already exists (prevent duplicates)
        Optional<EventsGroup> existingGroup = groupRepository
                .findByTenantIdAndAzureGroupId(tenantId, azureGroupId);

        if (existingGroup.isPresent()) {
            EventsGroup group = existingGroup.get();
            if (!group.getAuthorized()) {
                // Update to authorized
                group.setAuthorized(true);
                group.setExtensionAuthorized(true);
                group.setUpdatedBy(authorizedBy);
                group.setUpdatedAt(Instant.now());
                group.setSyncedAt(Instant.now());
                log.info("Updated existing Azure group {} to authorized (main login + extension)", azureGroupId);
                return groupRepository.save(group);
            }
            log.info("Azure group {} already authorized", azureGroupId);
            return group; // Already authorized
        }

        // Create new authorized group
        EventsGroup newGroup = new EventsGroup();
        newGroup.setPkEventsGroupId(UUID.randomUUID().toString());
        newGroup.setTenantId(tenantId);
        newGroup.setName(azureGroupDisplayName);
        newGroup.setDescription("Authorized Azure AD group");
        newGroup.setGroupType(EventsGroup.GroupType.AZURE_GROUP);
        newGroup.setAuthorized(true);            // ALWAYS true when created via this method
        newGroup.setExtensionAuthorized(true);   // Extension access granted by default on authorization
        newGroup.setAzureGroupId(azureGroupId);
        newGroup.setAzureGroupDisplayName(azureGroupDisplayName);
        newGroup.setSyncedAt(Instant.now());
        newGroup.setIsDefault(false);
        newGroup.setIsActive(true);
        newGroup.setCreatedBy(authorizedBy);
        newGroup.setUpdatedBy(authorizedBy);

        log.info("Created new authorized Azure group: {}", azureGroupId);
        return groupRepository.save(newGroup);
    }

    /**
     * Unauthorize and REMOVE Azure group from database
     * Since unauthorized groups are not stored, unauthorizing means deletion
     *
     * @param tenantId Tenant ID
     * @param groupId Events group ID (database ID)
     * @param unauthorizedBy Admin who unauthorized the group
     */
    @Transactional
    public void unauthorizeAndRemoveAzureGroup(
            String tenantId,
            String groupId,
            String unauthorizedBy) {

        EventsGroup group = groupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        if (group.getGroupType() != EventsGroup.GroupType.AZURE_GROUP) {
            throw new IllegalArgumentException("Only Azure groups can be unauthorized via this method");
        }

        // TODO: Re-enable when ready to enforce pre-unauthorization cleanup
        // // Check if group has assigned users or policies
        // long userCount = mappingRepository.countByFkEventsGroupId(groupId);
        // long policyCount = policyAssignmentRepository.countByEventsGroupIdAndTenantId(groupId, tenantId);
        //
        // if (userCount > 0 || policyCount > 0) {
        //     throw new IllegalStateException(
        //             String.format("Cannot unauthorize group with %d users and %d policies. " +
        //                     "Remove assignments first.", userCount, policyCount)
        //     );
        // }

        // Remove all policy assignments before deleting the group
        policyAssignmentRepository.deleteByEventsGroupId(groupId);

        // Hard delete from database (no soft delete for unauthorized Azure groups)
        groupRepository.delete(group);

        log.info("Azure group {} unauthorized and removed from database by {}",
                group.getAzureGroupId(), unauthorizedBy);
    }

    /**
     * Handle unauthorization for groups
     * For Azure groups: removes from database (hard delete)
     * For APIKEY groups: just updates authorized flag
     *
     * @param tenantId Tenant ID
     * @param groupId Group ID (database ID)
     * @param userEmail User email
     * @param isAzureTenant Whether tenant is Azure tenant
     * @return Unauthorized group (before deletion for Azure groups)
     */
    @Transactional
    public EventsGroup handleGroupUnauthorization(String tenantId, String groupId, String userEmail, boolean isAzureTenant) {
        EventsGroup group = getGroupById(tenantId, groupId);

        if (isAzureTenant && group.getGroupType() == EventsGroup.GroupType.AZURE_GROUP) {
            // For Azure groups, unauthorize means delete from database
            unauthorizeAndRemoveAzureGroup(tenantId, groupId, userEmail);
            // Return the group object before deletion for response
            return group;
        } else {
            // For APIKEY groups or non-Azure groups, just update authorization flag
            return unauthorizeGroup(tenantId, groupId, userEmail);
        }
    }

}
