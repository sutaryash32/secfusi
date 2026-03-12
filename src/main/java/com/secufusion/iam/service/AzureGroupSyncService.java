package com.secufusion.iam.service;

import com.secufusion.iam.dto.AzureResourceDto;
import com.secufusion.iam.dto.AzureSyncStatus;
import com.secufusion.iam.dto.EventsGroupDto;
import com.secufusion.iam.dto.GroupMemberSyncResult;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AzureGroupSyncService
 *
 * Asynchronous service for synchronizing Azure AD groups from Microsoft Graph API.
 * Tracks sync status per tenant to prevent duplicate syncs and provide progress updates.
 *
 * Flow:
 * 1. Check if sync already in progress for tenant
 * 2. Start async sync in background
 * 3. Use AzureGraphService to fetch Azure AD groups
 * 4. Create or update EventsGroup records for each Azure group
 * 5. Set authorized=false by default (admin must authorize)
 *
 * Integration: Uses IAM module's AzureGraphService for Microsoft Graph API access
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AzureGroupSyncService {

    private final EventsGroupService eventsGroupService;
    private final TenantRepository tenantRepository;
    private final AzureGraphService azureGraphService;
    private final EventsGroupRepository eventsGroupRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final BrowserPolicyRepository browserPolicyRepository;
    private final NetworkPolicyRepository networkPolicyRepository;
    private final ExtensionPolicyRepository extensionPolicyRepository;
    private final DeviceUserRepository deviceUserRepository;
    private final EventsGroupDeviceUserMappingRepository mappingRepository;
    private final EventsGroupHistoryRepository historyRepository;

    private static final String SYNC_USER = "AZURE_SYNC_SERVICE";

    // Cooldown period: 10 minutes between syncs
    private static final long SYNC_COOLDOWN_MINUTES = 10;

    // In-memory cache of sync status per tenant
    private final Map<String, AzureSyncStatus> syncStatusCache = new ConcurrentHashMap<>();

    /**
     * Get current sync status for a tenant
     *
     * @param tenantId Tenant ID
     * @return Current sync status or null if no sync has been performed
     */
    public AzureSyncStatus getSyncStatus(String tenantId) {
        return syncStatusCache.get(tenantId);
    }

    /**
     * Check if sync is currently in progress for a tenant
     *
     * @param tenantId Tenant ID
     * @return true if sync in progress, false otherwise
     */
    public boolean isSyncInProgress(String tenantId) {
        AzureSyncStatus status = syncStatusCache.get(tenantId);
        return status != null && status.getStatus() == AzureSyncStatus.Status.IN_PROGRESS;
    }

    /**
     * Check if tenant can sync (cooldown period check)
     * Returns true if:
     * - No previous sync exists, OR
     * - Previous sync was more than 10 minutes ago
     *
     * @param tenantId Tenant ID
     * @return true if can sync, false if in cooldown
     */
    public boolean canSync(String tenantId) {
        AzureSyncStatus status = syncStatusCache.get(tenantId);

        // No previous sync - can sync
        if (status == null) {
            return true;
        }

        // Currently in progress - cannot sync
        if (status.getStatus() == AzureSyncStatus.Status.IN_PROGRESS) {
            return false;
        }

        // Failed sync - can retry immediately (no cooldown for failures)
        if (status.getStatus() == AzureSyncStatus.Status.FAILED) {
            log.debug("Previous sync failed for tenant: {}. Can retry immediately.", tenantId);
            return true;
        }

        // Successful sync - check cooldown period
        if (status.getStatus() == AzureSyncStatus.Status.COMPLETED && status.getCompletedAt() != null) {
            Instant cooldownExpiry = status.getCompletedAt().plusSeconds(SYNC_COOLDOWN_MINUTES * 60);
            boolean isAfterCooldown = Instant.now().isAfter(cooldownExpiry);

            if (!isAfterCooldown) {
                log.debug("Tenant {} is in cooldown period. Last sync at: {}, can sync again at: {}",
                        tenantId, status.getCompletedAt(), cooldownExpiry);
            }

            return isAfterCooldown;
        }

        // Default: allow sync
        return true;
    }

    /**
     * Get remaining cooldown time in seconds
     *
     * @param tenantId Tenant ID
     * @return Remaining seconds in cooldown, or 0 if can sync
     */
    public long getRemainingCooldownSeconds(String tenantId) {
        AzureSyncStatus status = syncStatusCache.get(tenantId);

        // No status or failed sync - no cooldown
        if (status == null || status.getStatus() == AzureSyncStatus.Status.FAILED) {
            return 0;
        }

        // Only COMPLETED syncs have cooldown
        if (status.getStatus() == AzureSyncStatus.Status.COMPLETED && status.getCompletedAt() != null) {
            Instant cooldownExpiry = status.getCompletedAt().plusSeconds(SYNC_COOLDOWN_MINUTES * 60);
            long remainingSeconds = cooldownExpiry.getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(0, remainingSeconds);
        }

        return 0;
    }

    /**
     * Initiate async Azure group sync for a tenant
     *
     * @param tenantId Tenant ID
     * @return Sync status
     * @throws IllegalStateException if sync already in progress, in cooldown, or tenant not configured for Azure
     */
    public AzureSyncStatus initiateSync(String tenantId) {
        log.info("Initiating Azure group sync for tenant: {}", tenantId);

        // Check if sync already in progress
        if (isSyncInProgress(tenantId)) {
            log.warn("Sync already in progress for tenant: {}", tenantId);
            return syncStatusCache.get(tenantId);
        }

        // Check cooldown period
        if (!canSync(tenantId)) {
            long remainingSeconds = getRemainingCooldownSeconds(tenantId);
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;
            throw new IllegalStateException(
                String.format("Sync cooldown period active. Please wait %d minute(s) and %d second(s) before syncing again.",
                    minutes, seconds)
            );
        }

        // Fetch tenant details
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // Validate tenant has Azure SSO configured
        if (tenant.getAuthProviderConfig() == null ||
            !"AZURE".equalsIgnoreCase(tenant.getAuthProviderConfig().getSsoType())) {
            throw new IllegalStateException("Tenant does not have Azure SSO configured");
        }

        if (tenant.getAzureTenantId() == null || tenant.getAzureTenantId().isBlank()) {
            throw new IllegalStateException("Azure Tenant ID is missing");
        }

        // Create initial status
        AzureSyncStatus status = AzureSyncStatus.builder()
                .tenantId(tenantId)
                .status(AzureSyncStatus.Status.IN_PROGRESS)
                .startedAt(Instant.now())
                .message("Sync in progress. Please wait...")
                .build();

        syncStatusCache.put(tenantId, status);

        // Start async sync
        performAsyncSync(tenant);

        return status;
    }

    /**
     * Perform async sync in background thread
     *
     * @param tenant Tenant entity
     */
    @Async
    @Transactional
    public void performAsyncSync(Tenant tenant) {
        String tenantId = tenant.getTenantID();
        log.info("Starting async Azure group sync for tenant: {}", tenantId);

        int newGroups = 0;
        int updatedGroups = 0;

        try {
            // Fetch groups from Microsoft Graph API
            List<AzureResourceDto> azureGroups = azureGraphService.searchTenantGroups(
                    tenant,
                    null, // No search term - fetch all groups
                    tenant.getAzureTenantId()
            );

            log.info("Fetched {} Azure groups for tenant: {}", azureGroups.size(), tenantId);

            // Get existing groups before sync
            List<EventsGroup> existingGroups = eventsGroupService.getGroupsByType(
                    tenantId,
                    EventsGroup.GroupType.AZURE_GROUP
            );
            Set<String> existingAzureIds = new HashSet<>();
            for (EventsGroup group : existingGroups) {
                if (group.getAzureGroupId() != null) {
                    existingAzureIds.add(group.getAzureGroupId());
                }
            }

            // Sync groups to events_groups table
            for (AzureResourceDto azureGroup : azureGroups) {
                boolean isNew = !existingAzureIds.contains(azureGroup.getId());

                eventsGroupService.createOrUpdateAzureGroup(
                        tenantId,
                        azureGroup.getId(), // Azure group OID
                        azureGroup.getName(), // Display name
                        SYNC_USER
                );

                if (isNew) {
                    newGroups++;
                } else {
                    updatedGroups++;
                }
            }

            // Sync members for all authorized groups
            syncAllAuthorizedGroupMembers(tenant);

            // Update status to completed
            AzureSyncStatus completedStatus = AzureSyncStatus.builder()
                    .tenantId(tenantId)
                    .status(AzureSyncStatus.Status.COMPLETED)
                    .totalFetched(azureGroups.size())
                    .newGroups(newGroups)
                    .updatedGroups(updatedGroups)
                    .startedAt(syncStatusCache.get(tenantId).getStartedAt())
                    .completedAt(Instant.now())
                    .message("Successfully synced " + azureGroups.size() + " Azure groups with members")
                    .build();

            syncStatusCache.put(tenantId, completedStatus);
            log.info("Azure group sync completed for tenant: {}. New: {}, Updated: {}",
                    tenantId, newGroups, updatedGroups);

        } catch (Exception e) {
            log.error("Failed to sync Azure groups for tenant: {}", tenantId, e);

            // Update status to failed
            AzureSyncStatus failedStatus = AzureSyncStatus.builder()
                    .tenantId(tenantId)
                    .status(AzureSyncStatus.Status.FAILED)
                    .startedAt(syncStatusCache.get(tenantId).getStartedAt())
                    .completedAt(Instant.now())
                    .message("Sync failed")
                    .errorMessage(e.getMessage())
                    .build();

            syncStatusCache.put(tenantId, failedStatus);
        }
    }

    /**
     * Get Azure groups from Azure AD API and combine with authorization status from DB
     * This replaces the sync approach - groups are fetched on-demand
     *
     * @param tenantId Tenant ID
     * @param authorizedOnly If true, only return authorized groups; if false, return all from Azure AD
     * @return List of EventsGroupDto with authorization status
     */
    @Transactional(readOnly = true)
    public List<EventsGroupDto> getAvailableAzureGroupsForTenant(String tenantId, Boolean authorizedOnly) {
        log.info("Fetching available Azure groups for tenant: {}, authorizedOnly: {}", tenantId, authorizedOnly);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // Validate Azure tenant configuration
        if (tenant.getAzureTenantId() == null || tenant.getAzureTenantId().isEmpty()) {
            throw new IllegalStateException("Azure tenant ID not configured for tenant: " + tenantId);
        }

        // Fetch all groups from Azure AD API (real-time)
        List<AzureResourceDto> azureGroups = azureGraphService.searchTenantGroups(
                tenant, null, tenant.getAzureTenantId()
        );

        // Get authorized groups from database
        List<EventsGroup> authorizedGroupsInDb =
                eventsGroupRepository.findByTenantIdAndGroupTypeAndAuthorized(
                        tenantId, EventsGroup.GroupType.AZURE_GROUP, true
                );

        Map<String, EventsGroup> authorizedGroupMap = authorizedGroupsInDb.stream()
                .collect(Collectors.toMap(EventsGroup::getAzureGroupId, Function.identity()));

        // Combine Azure API data with authorization status
        List<EventsGroupDto> result = azureGroups.stream()
                .map(azureGroup -> {
                    EventsGroup dbGroup = authorizedGroupMap.get(azureGroup.getId());
                    boolean isAuthorized = dbGroup != null;

                    // For unauthorized groups, create a DTO with Azure data only
                    if (!isAuthorized) {
                        return EventsGroupDto.builder()
                                .pkEventsGroupId(null) // Not in DB yet
                                .tenantId(tenantId)
                                .name(azureGroup.getName())
                                .description("Azure AD group (not authorized)")
                                .groupType("AZURE_GROUP")
                                .authorized(false)
                                .azureGroupId(azureGroup.getId())
                                .azureGroupDisplayName(azureGroup.getName())
                                .syncedAt(null)
                                .isDefault(false)
                                .isActive(true)
                                .build();
                    } else {
                        // For authorized groups, return full DB data
                        return EventsGroupDto.builder()
                                .pkEventsGroupId(dbGroup.getPkEventsGroupId())
                                .tenantId(dbGroup.getTenantId())
                                .name(dbGroup.getName())
                                .description(dbGroup.getDescription())
                                .groupType(dbGroup.getGroupType().name())
                                .authorized(dbGroup.getAuthorized())
                                .azureGroupId(dbGroup.getAzureGroupId())
                                .azureGroupDisplayName(dbGroup.getAzureGroupDisplayName())
                                .syncedAt(dbGroup.getSyncedAt())
                                .isDefault(dbGroup.getIsDefault())
                                .isActive(dbGroup.getIsActive())
                                .createdAt(dbGroup.getCreatedAt())
                                .updatedAt(dbGroup.getUpdatedAt())
                                .createdBy(dbGroup.getCreatedBy())
                                .updatedBy(dbGroup.getUpdatedBy())
                                .build();
                    }
                })
                .filter(group -> !authorizedOnly || group.getAuthorized())
                .collect(Collectors.toList());

        log.info("Found {} Azure groups (authorized: {}, total: {})",
                result.size(),
                result.stream().filter(EventsGroupDto::getAuthorized).count(),
                azureGroups.size());

        return result;
    }

    /**
     * Authorize Azure group by fetching details from Azure AD API
     * Handles both database ID and Azure Group ID
     *
     * @param tenant Tenant entity
     * @param groupId Group ID (database ID or Azure Group ID)
     * @param userEmail User email
     * @return Authorized EventsGroup
     */
    @Transactional
    public EventsGroup authorizeAzureGroupById(Tenant tenant, String groupId, String userEmail) {
        String tenantId = tenant.getTenantID();

        // Try to find by database ID first
        Optional<EventsGroup> existingGroup = eventsGroupRepository.findByIdAndTenantId(groupId, tenantId);

        if (existingGroup.isPresent()) {
            // Already in database - just update authorization status
            EventsGroup group = existingGroup.get();
            if (!group.getAuthorized()) {
                group.setAuthorized(true);
                group.setUpdatedBy(userEmail);
                group.setUpdatedAt(Instant.now());
                group = eventsGroupRepository.save(group);

                recordHistory(tenantId, group, "GROUP_AUTHORIZED",
                        "Group authorized by admin", userEmail);
            }
            // Assign default policies and sync members after authorization
            assignDefaultPolicies(group, tenantId, userEmail);
            syncGroupMembers(tenant, group, userEmail);
            return group;
        }

        // Not in database - treat as Azure Group ID, fetch directly from Azure AD
        log.info("Group ID {} not found in database. Treating as Azure Group ID and fetching from Azure AD", groupId);

        AzureResourceDto azureGroup = azureGraphService.getGroupById(
                tenant, groupId, tenant.getAzureTenantId());

        if (azureGroup == null) {
            throw new ResourceNotFoundException("Azure group not found in Azure AD: " + groupId);
        }

        // Authorize and persist with details from Azure AD
        EventsGroup authorizedGroup = eventsGroupService.authorizeAndPersistAzureGroup(
                tenantId,
                azureGroup.getId(),
                azureGroup.getName(),
                userEmail
        );

        // Record authorization history
        recordHistory(tenantId, authorizedGroup, "GROUP_AUTHORIZED",
                "New Azure group authorized and persisted from Azure AD", userEmail);

        // Assign default policies and sync members after authorization
        assignDefaultPolicies(authorizedGroup, tenantId, userEmail);
        syncGroupMembers(tenant, authorizedGroup, userEmail);

        return authorizedGroup;
    }

    /**
     * Get Azure groups with policy information
     * Combines Azure AD API data with database authorization status and policy counts
     *
     * @param tenantId Tenant ID
     * @param authorizedOnly Filter to only authorized groups
     * @param includePolicies Include detailed policy information
     * @return List of group data maps
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAzureGroupsWithPolicyInfo(
            String tenantId,
            Boolean authorizedOnly,
            Boolean includePolicies) {

        // Fetch Azure groups from Azure AD API with authorization status
        List<EventsGroupDto> azureGroupDtos = getAvailableAzureGroupsForTenant(
                tenantId,
                authorizedOnly != null ? authorizedOnly : false
        );

        // Build response with policy information
        return azureGroupDtos.stream()
                .map(dto -> buildGroupDataMap(dto, tenantId, includePolicies))
                .collect(Collectors.toList());
    }

    /**
     * Build group data map with policy information
     */
    private Map<String, Object> buildGroupDataMap(EventsGroupDto dto, String tenantId, Boolean includePolicies) {
        Map<String, Object> groupData = new HashMap<>();

        // Basic group info
        groupData.put("pkEventsGroupId", dto.getPkEventsGroupId());
        groupData.put("tenantId", dto.getTenantId());
        groupData.put("name", dto.getName());
        groupData.put("description", dto.getDescription());
        groupData.put("groupType", dto.getGroupType());
        groupData.put("authorized", dto.getAuthorized());
        groupData.put("azureGroupId", dto.getAzureGroupId());
        groupData.put("azureGroupDisplayName", dto.getAzureGroupDisplayName());
        groupData.put("syncedAt", dto.getSyncedAt());
        groupData.put("isDefault", dto.getIsDefault());
        groupData.put("isActive", dto.getIsActive());
        groupData.put("createdAt", dto.getCreatedAt());
        groupData.put("updatedAt", dto.getUpdatedAt());
        groupData.put("createdBy", dto.getCreatedBy());
        groupData.put("updatedBy", dto.getUpdatedBy());

        // For unauthorized groups, policy and device user count is always 0
        if (!dto.getAuthorized() || dto.getPkEventsGroupId() == null) {
            groupData.put("deviceUsersCount", 0L);
            groupData.put("policyCount", 0);
            groupData.put("policyCountsByType", Map.of(
                    "browserPolicies", 0L,
                    "networkPolicies", 0L,
                    "extensionPolicies", 0L,
                    "total", 0L
            ));
            return groupData;
        }

        // For authorized groups, add device user count and policy information
        long deviceUserCount = mappingRepository.countByFkEventsGroupId(dto.getPkEventsGroupId());
        groupData.put("deviceUsersCount", deviceUserCount);
        addPolicyInformation(groupData, dto.getPkEventsGroupId(), tenantId, includePolicies);

        return groupData;
    }

    /**
     * Assign default policies (Browser, Network, Extension) to a newly authorized group.
     * Picks the first active policy of each type for the tenant.
     * Skips if the group already has policy assignments.
     */
    @Transactional
    public void assignDefaultPolicies(EventsGroup group, String tenantId, String performedBy) {
        String groupId = group.getPkEventsGroupId();

        // Skip if group already has policy assignments
        if (policyAssignmentRepository.existsByGroupIdAndTenantId(groupId, tenantId)) {
            log.debug("Group '{}' already has policy assignments, skipping default assignment", group.getName());
            return;
        }

        // Find first active policy of each type: prefer tenant-specific, fall back to global defaults (fkTenantId = null)
        BrowserPolicy browserPolicy = browserPolicyRepository
                .findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc(tenantId)
                .stream().findFirst()
                .orElseGet(() -> browserPolicyRepository
                        .findAllByFkTenantIdIsNullAndIsActiveTrueOrderByCreatedAtAsc()
                        .stream().findFirst().orElse(null));

        NetworkPolicy networkPolicy = networkPolicyRepository
                .findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc(tenantId)
                .stream().findFirst()
                .orElseGet(() -> networkPolicyRepository
                        .findAllByFkTenantIdIsNullAndIsActiveTrueOrderByCreatedAtAsc()
                        .stream().findFirst().orElse(null));

        ExtensionPolicy extensionPolicy = extensionPolicyRepository
                .findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtAsc(tenantId)
                .stream().findFirst()
                .orElseGet(() -> extensionPolicyRepository
                        .findAllByFkTenantIdIsNullAndIsActiveTrueOrderByCreatedAtAsc()
                        .stream().findFirst().orElse(null));

        if (browserPolicy == null && networkPolicy == null && extensionPolicy == null) {
            log.info("No active policies found for tenant '{}', skipping default assignment for group '{}'",
                    tenantId, group.getName());
            return;
        }

        // Create separate assignment rows for each policy type (one row per policy)
        if (browserPolicy != null) {
            PolicyAssignment browserAssignment = PolicyAssignment.builder()
                    .browserPolicy(browserPolicy)
                    .azureResourceId(groupId)
                    .azureResourceName(group.getName())
                    .assignmentType("GROUP")
                    .fkTenantId(tenantId)
                    .build();
            PolicyAssignment saved = policyAssignmentRepository.save(browserAssignment);
            recordPolicyHistory(tenantId, group, "POLICY_ASSIGNED", saved.getId(),
                    "BROWSER", browserPolicy.getName(), "Default browser policy assigned", performedBy);
        }

        if (networkPolicy != null) {
            PolicyAssignment networkAssignment = PolicyAssignment.builder()
                    .networkPolicy(networkPolicy)
                    .azureResourceId(groupId)
                    .azureResourceName(group.getName())
                    .assignmentType("GROUP")
                    .fkTenantId(tenantId)
                    .build();
            PolicyAssignment saved = policyAssignmentRepository.save(networkAssignment);
            recordPolicyHistory(tenantId, group, "POLICY_ASSIGNED", saved.getId(),
                    "NETWORK", networkPolicy.getName(), "Default network policy assigned", performedBy);
        }

        if (extensionPolicy != null) {
            PolicyAssignment extensionAssignment = PolicyAssignment.builder()
                    .extensionPolicy(extensionPolicy)
                    .azureResourceId(groupId)
                    .azureResourceName(group.getName())
                    .assignmentType("GROUP")
                    .fkTenantId(tenantId)
                    .build();
            PolicyAssignment saved = policyAssignmentRepository.save(extensionAssignment);
            recordPolicyHistory(tenantId, group, "POLICY_ASSIGNED", saved.getId(),
                    "EXTENSION", extensionPolicy.getName(), "Default extension policy assigned", performedBy);
        }

        log.info("Assigned default policies to group '{}': browser={}, network={}, extension={}",
                group.getName(),
                browserPolicy != null ? browserPolicy.getName() : "none",
                networkPolicy != null ? networkPolicy.getName() : "none",
                extensionPolicy != null ? extensionPolicy.getName() : "none");
    }

    /**
     * Sync Azure AD group members to local device user mappings.
     * Fetches members from Microsoft Graph API, matches them to existing DeviceUser records by email,
     * and creates EventsGroupDeviceUserMapping entries for matched users.
     *
     * @param tenant   Tenant entity
     * @param group    Authorized EventsGroup with azureGroupId
     * @param assignedBy User who triggered the sync
     * @return Number of new mappings created
     */
    @Transactional
    public GroupMemberSyncResult syncGroupMembers(Tenant tenant, EventsGroup group, String assignedBy) {
        if (group.getAzureGroupId() == null || !group.getAuthorized()) {
            log.debug("Skipping member sync for group {} - not authorized or no Azure group ID", group.getName());
            return GroupMemberSyncResult.builder()
                    .groupId(group.getPkEventsGroupId())
                    .groupName(group.getName())
                    .azureMembersCount(0).matchedDeviceUsers(0).newMappingsCreated(0)
                    .unmatchedEmails(Collections.emptyList())
                    .build();
        }

        String tenantId = tenant.getTenantID();
        String azureGroupId = group.getAzureGroupId();
        String groupId = group.getPkEventsGroupId();

        try {
            // Fetch member emails from Azure AD
            List<String> memberEmails = azureGraphService.getGroupMemberEmails(
                    tenant, azureGroupId, tenant.getAzureTenantId()
            );

            if (memberEmails.isEmpty()) {
                log.info("No members found in Azure group: {} for tenant: {}", group.getName(), tenantId);
                return GroupMemberSyncResult.builder()
                        .groupId(groupId).groupName(group.getName())
                        .azureMembersCount(0).matchedDeviceUsers(0).newMappingsCreated(0)
                        .unmatchedEmails(Collections.emptyList())
                        .build();
            }

            // Match to existing device users by email
            List<DeviceUser> matchedUsers = deviceUserRepository.findByFkTenantIdAndEmailIn(tenantId, memberEmails);

            // Find unmatched emails (Azure members with no DeviceUser record)
            Set<String> matchedEmails = matchedUsers.stream()
                    .map(u -> u.getEmail().toLowerCase())
                    .collect(java.util.stream.Collectors.toSet());
            List<String> unmatchedEmails = memberEmails.stream()
                    .filter(email -> !matchedEmails.contains(email))
                    .toList();

            // Create mappings for users not already assigned
            int created = 0;
            for (DeviceUser user : matchedUsers) {
                if (!mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                        user.getPkDeviceUserId(), groupId)) {

                    EventsGroupDeviceUserMapping mapping = new EventsGroupDeviceUserMapping();
                    mapping.setPkMappingId(UUID.randomUUID().toString());
                    mapping.setFkDeviceUserId(user.getPkDeviceUserId());
                    mapping.setFkEventsGroupId(groupId);
                    mapping.setAssignedBy(assignedBy);
                    mappingRepository.save(mapping);
                    created++;
                }
            }

            log.info("Synced members for group '{}': {} Azure members, {} matched, {} unmatched, {} new mappings",
                    group.getName(), memberEmails.size(), matchedUsers.size(), unmatchedEmails.size(), created);

            // Record member sync history
            String syncDetails = String.format(
                    "Azure members: %d, matched: %d, unmatched: %d, new mappings: %d",
                    memberEmails.size(), matchedUsers.size(), unmatchedEmails.size(), created);
            recordHistory(tenantId, group, "MEMBERS_SYNCED", syncDetails, assignedBy);

            return GroupMemberSyncResult.builder()
                    .groupId(groupId)
                    .groupName(group.getName())
                    .azureMembersCount(memberEmails.size())
                    .matchedDeviceUsers(matchedUsers.size())
                    .newMappingsCreated(created)
                    .unmatchedEmails(unmatchedEmails)
                    .build();

        } catch (Exception e) {
            log.error("Failed to sync members for group '{}' (azureGroupId={}): {}",
                    group.getName(), azureGroupId, e.getMessage(), e);
            return GroupMemberSyncResult.builder()
                    .groupId(groupId).groupName(group.getName())
                    .azureMembersCount(0).matchedDeviceUsers(0).newMappingsCreated(0)
                    .unmatchedEmails(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Sync members for all authorized Azure groups in a tenant
     */
    @Transactional
    public List<GroupMemberSyncResult> syncAllAuthorizedGroupMembers(Tenant tenant) {
        String tenantId = tenant.getTenantID();
        List<EventsGroup> authorizedGroups = eventsGroupRepository.findByTenantIdAndGroupTypeAndAuthorized(
                tenantId, EventsGroup.GroupType.AZURE_GROUP, true
        );

        log.info("Syncing members for {} authorized Azure groups in tenant: {}", authorizedGroups.size(), tenantId);

        List<GroupMemberSyncResult> results = new ArrayList<>();
        for (EventsGroup group : authorizedGroups) {
            results.add(syncGroupMembers(tenant, group, SYNC_USER));
        }

        int totalCreated = results.stream().mapToInt(GroupMemberSyncResult::getNewMappingsCreated).sum();
        log.info("Member sync complete for tenant: {}. Total new mappings: {}", tenantId, totalCreated);
        return results;
    }

    /**
     * Add policy information to group data map
     */
    private void addPolicyInformation(Map<String, Object> groupData, String groupId,
                                     String tenantId, Boolean includePolicies) {

        List<PolicyAssignment> assignments = policyAssignmentRepository.findByEventsGroupIdAndTenantId(groupId, tenantId);

        long browserPolicyCount = assignments.stream().filter(a -> a.getBrowserPolicy() != null).count();
        long networkPolicyCount = assignments.stream().filter(a -> a.getNetworkPolicy() != null).count();
        long extensionPolicyCount = assignments.stream().filter(a -> a.getExtensionPolicy() != null).count();
        long total = browserPolicyCount + networkPolicyCount + extensionPolicyCount;

        groupData.put("policyCount", total);
        groupData.put("policyCountsByType", Map.of(
                "browserPolicies", browserPolicyCount,
                "networkPolicies", networkPolicyCount,
                "extensionPolicies", extensionPolicyCount,
                "total", total
        ));
    }

    // =================================================================================
    // HISTORY HELPERS
    // =================================================================================

    private void recordHistory(String tenantId, EventsGroup group, String action,
                               String details, String performedBy) {
        try {
            historyRepository.save(EventsGroupHistory.builder()
                    .fkTenantId(tenantId)
                    .eventsGroupId(group.getPkEventsGroupId())
                    .groupName(group.getName())
                    .action(action)
                    .details(details)
                    .performedBy(performedBy)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record history for group '{}': {}", group.getName(), e.getMessage());
        }
    }

    private void recordPolicyHistory(String tenantId, EventsGroup group, String action,
                                     String assignmentId, String policyType, String policyName,
                                     String details, String performedBy) {
        try {
            historyRepository.save(EventsGroupHistory.builder()
                    .fkTenantId(tenantId)
                    .eventsGroupId(group.getPkEventsGroupId())
                    .groupName(group.getName())
                    .action(action)
                    .policyAssignmentId(assignmentId)
                    .policyType(policyType)
                    .policyName(policyName)
                    .details(details)
                    .performedBy(performedBy)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record policy history for group '{}': {}", group.getName(), e.getMessage());
        }
    }

    /**
     * Get history for a specific group
     */
    public List<EventsGroupHistory> getGroupHistory(String eventsGroupId, String tenantId) {
        return historyRepository.findByEventsGroupIdAndFkTenantIdOrderByPerformedAtDesc(eventsGroupId, tenantId);
    }

    /**
     * Get all history for a tenant
     */
    public List<EventsGroupHistory> getTenantHistory(String tenantId) {
        return historyRepository.findByFkTenantIdOrderByPerformedAtDesc(tenantId);
    }
}
