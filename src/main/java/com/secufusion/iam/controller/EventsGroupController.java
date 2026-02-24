package com.secufusion.iam.controller;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.entity.EventsGroupDeviceUserMapping;
import com.secufusion.iam.entity.PolicyAssignment;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.repository.PolicyAssignmentRepository;
import com.secufusion.iam.service.AzureGroupSyncService;
import com.secufusion.iam.service.DeviceUserGroupMappingService;
import com.secufusion.iam.service.EventsGroupService;
import com.secufusion.iam.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/iam/events-groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Events Groups", description = "Manage events groups for API key and Azure AD users")
public class EventsGroupController {

    private final EventsGroupService eventsGroupService;
    private final AzureGroupSyncService azureGroupSyncService;
    private final DeviceUserGroupMappingService mappingService;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final JwtUtl jwtUtil;

    // ================== Group Management ==================

    /**
     * Get all events groups for tenant
     * Query params:
     * - authorizedOnly: Show only authorized groups
     * - includePolicies: Include full policy assignment details (default: false, only count returned)
     */
    @GetMapping
    @Operation(summary = "Get all events groups for tenant")
    public ResponseEntity<List<Map<String, Object>>> getAllGroups(
            HttpServletRequest request,
            @Parameter(description = "Show only authorized groups")
            @RequestParam(required = false) Boolean authorizedOnly,
            @Parameter(description = "Include full policy details")
            @RequestParam(required = false, defaultValue = "false") Boolean includePolicies
    ) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String ssoType = tenant.getAuthProviderConfig().getSsoType();

        log.info("Fetching events groups for tenant: {} (ssoType={}, authorizedOnly={}, includePolicies={})",
                tenantId, ssoType, authorizedOnly, includePolicies);

        // KEYCLOAK tenants don't use event groups
        if ("KEYCLOAK".equalsIgnoreCase(ssoType)) {
            return ResponseEntity.ok(List.of());
        }

        // Resolve group type based on SSO
        List<EventsGroup> groups = resolveGroupsBySsoType(tenantId, ssoType);

        // Filter authorized if needed
        if (Boolean.TRUE.equals(authorizedOnly)) {
            groups = groups.stream()
                    .filter(EventsGroup::getAuthorized)
                    .toList();
        }

        // Fetch all policy assignments for all groups in a single batch query
        List<String> groupIds = groups.stream()
                .map(EventsGroup::getPkEventsGroupId)
                .collect(Collectors.toList());

        Map<String, List<PolicyAssignment>> policyAssignmentsByGroup = new HashMap<>();
        if (!groupIds.isEmpty()) {
            List<PolicyAssignment> allAssignments = policyAssignmentRepository.findByEventsGroupIdsAndTenantId(
                    groupIds,
                    tenantId
            );

            // Group assignments by group ID
            policyAssignmentsByGroup = allAssignments.stream()
                    .collect(Collectors.groupingBy(PolicyAssignment::getAzureResourceId));
        }

        // Build response with policy information
        Map<String, List<PolicyAssignment>> finalPolicyMap = policyAssignmentsByGroup;
        boolean isApiKeyTenant = "APIKEY".equalsIgnoreCase(ssoType);

        List<Map<String, Object>> response = groups.stream()
                .map(group -> {
                    Map<String, Object> groupData = new HashMap<>();

                    // Add basic group info
                    EventsGroupDto dto = convertToDto(group);
                    groupData.put("pkEventsGroupId", dto.getPkEventsGroupId());
                    groupData.put("tenantId", dto.getTenantId());
                    groupData.put("name", dto.getName());
                    groupData.put("description", dto.getDescription());
                    groupData.put("groupType", dto.getGroupType());
                    groupData.put("authorized", dto.getAuthorized());

                    // Only include Azure fields if tenant is Azure SSO
                    if (!isApiKeyTenant) {
                        groupData.put("azureGroupId", dto.getAzureGroupId());
                        groupData.put("azureGroupDisplayName", dto.getAzureGroupDisplayName());
                        groupData.put("syncedAt", dto.getSyncedAt());
                    }

                    groupData.put("isDefault", dto.getIsDefault());
                    groupData.put("isActive", dto.getIsActive());
                    groupData.put("createdAt", dto.getCreatedAt());
                    groupData.put("updatedAt", dto.getUpdatedAt());
                    groupData.put("createdBy", dto.getCreatedBy());
                    groupData.put("updatedBy", dto.getUpdatedBy());

                    // Get policy assignments for this group
                    List<PolicyAssignment> assignments = finalPolicyMap.getOrDefault(
                            group.getPkEventsGroupId(),
                            List.of()
                    );

                    // Add distinct policy names (resource names)
                    List<String> distinctPolicyNames = assignments.stream()
                            .map(PolicyAssignment::getAzureResourceName)
                            .filter(name -> name != null && !name.isBlank())
                            .distinct()
                            .collect(Collectors.toList());

                    groupData.put("policyCount", distinctPolicyNames.size());
                    groupData.put("policies", distinctPolicyNames);

                    // Add full policy details if requested
                    if (Boolean.TRUE.equals(includePolicies) && !assignments.isEmpty()) {
                        List<PolicyAssignmentDto> policyDtos = assignments.stream()
                                .map(this::convertPolicyAssignmentToDto)
                                .collect(Collectors.toList());
                        groupData.put("policyDetails", policyDtos);
                    }

                    return groupData;
                })
                .toList();

        return ResponseEntity.ok(response);
    }


    /**
     * Get single events group by ID
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "Get events group by ID")
    public ResponseEntity<Map<String, Object>> getGroupById(
            HttpServletRequest request,
            @PathVariable String groupId
    ) {
        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String ssoType = tenant.getAuthProviderConfig().getSsoType();

        log.info("Fetching events group: {} for tenant: {}", groupId, tenantId);

        EventsGroup group = eventsGroupService.getGroupById(tenantId, groupId);
        EventsGroupDto dto = convertToDto(group);

        // Build response conditionally based on SSO type
        Map<String, Object> response = new HashMap<>();
        boolean isApiKeyTenant = "APIKEY".equalsIgnoreCase(ssoType);

        response.put("pkEventsGroupId", dto.getPkEventsGroupId());
        response.put("tenantId", dto.getTenantId());
        response.put("name", dto.getName());
        response.put("description", dto.getDescription());
        response.put("groupType", dto.getGroupType());
        response.put("authorized", dto.getAuthorized());

        // Only include Azure fields if tenant is not APIKEY
        if (!isApiKeyTenant) {
            response.put("azureGroupId", dto.getAzureGroupId());
            response.put("azureGroupDisplayName", dto.getAzureGroupDisplayName());
            response.put("syncedAt", dto.getSyncedAt());
        }

        response.put("isDefault", dto.getIsDefault());
        response.put("isActive", dto.getIsActive());
        response.put("createdAt", dto.getCreatedAt());
        response.put("updatedAt", dto.getUpdatedAt());
        response.put("createdBy", dto.getCreatedBy());
        response.put("updatedBy", dto.getUpdatedBy());

        return ResponseEntity.ok(response);
    }

    /**
     * Create new API key group
     */
    @PostMapping
    @Operation(summary = "Create new API key group")
    public ResponseEntity<EventsGroupDto> createGroup(
            HttpServletRequest request,
            @Valid @RequestBody CreateEventsGroupRequest dto
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        String userEmail = jwtUtil.getUserFromRequest(request).getEmail();
        log.info("Creating events group '{}' for tenant: {} by user: {}", dto.getName(), tenantId, userEmail);

        EventsGroup created = eventsGroupService.createEventsGroup(
                tenantId,
                dto.getName(),
                dto.getDescription(),
                userEmail
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(created));
    }

    /**
     * Update events group
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "Update events group")
    public ResponseEntity<EventsGroupDto> updateGroup(
            HttpServletRequest request,
            @PathVariable String groupId,
            @Valid @RequestBody UpdateEventsGroupRequest dto
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        String userEmail = jwtUtil.getUserFromRequest(request).getEmail();
        log.info("Updating events group: {} for tenant: {} by user: {}", groupId, tenantId, userEmail);

        EventsGroup updated = eventsGroupService.updateGroup(
                tenantId,
                groupId,
                dto.getName(),
                dto.getDescription(),
                userEmail
        );

        return ResponseEntity.ok(convertToDto(updated));
    }

    /**
     * Set authorization status for a group (admin operation)
     */
    @PutMapping("/groups/{groupId}/{action}")
    @Operation(summary = "Authorize or unauthorize a group")
    public ResponseEntity<EventsGroupDto> setAuthorization(
            HttpServletRequest request,
            @PathVariable String groupId,
            @PathVariable String action
    ) {

        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        String userEmail = jwtUtil.getUserFromRequest(request).getEmail();

        log.info("Setting authorization for group: {} with action: {} for tenant: {} by user: {}",
                groupId, action, tenantId, userEmail);

        EventsGroup updated = switch (action.toLowerCase()) {
            case "authorize" -> eventsGroupService.authorizeGroup(tenantId, groupId, userEmail);
            case "unauthorize" -> eventsGroupService.unauthorizeGroup(tenantId, groupId, userEmail);
            default -> throw new IllegalArgumentException("Invalid action. Allowed values: authorize, unauthorize");
        };

        return ResponseEntity.ok(convertToDto(updated));
    }

    /**
     * Delete events group (soft delete)
     */
    @DeleteMapping("/{groupId}")
    @Operation(summary = "Delete events group")
    public ResponseEntity<Void> deleteGroup(
            HttpServletRequest request,
            @PathVariable String groupId
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        String userEmail = jwtUtil.getUserFromRequest(request).getEmail();
        log.info("Deleting events group: {} for tenant: {} by user: {}", groupId, tenantId, userEmail);

        eventsGroupService.deleteGroup(tenantId, groupId);
        return ResponseEntity.noContent().build();
    }

    // ================== Azure Group Sync ==================

    /**
     * Initiate Azure AD group sync for tenant (async)
     * Returns immediately with status "in progress"
     */
    @PostMapping("/sync-azure")
    @Operation(summary = "Initiate Azure AD group sync (async)")
    public ResponseEntity<Map<String, Object>> syncAzureGroups(
            HttpServletRequest request
    ) {
        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();

        log.info("Received Azure group sync request for tenant: {}", tenantId);

        // Validate tenant has Azure SSO
        if (tenant.getAuthProviderConfig() == null ||
            !"AZURE".equalsIgnoreCase(tenant.getAuthProviderConfig().getSsoType())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", "Azure group sync is only available for tenants with Azure SSO configured"
                    ));
        }

        // Check if sync already in progress
        if (azureGroupSyncService.isSyncInProgress(tenantId)) {
            log.info("Sync already in progress for tenant: {}", tenantId);
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "in_progress",
                            "message", "Azure group sync is already in progress. Please wait for it to complete."
                    ));
        }

        // Check if sync was recently completed and show results
        AzureSyncStatus existingStatus = azureGroupSyncService.getSyncStatus(tenantId);
        if (existingStatus != null && existingStatus.getStatus() == AzureSyncStatus.Status.COMPLETED) {
            long remainingSeconds = azureGroupSyncService.getRemainingCooldownSeconds(tenantId);

            if (remainingSeconds > 0) {
                long minutes = remainingSeconds / 60;
                long seconds = remainingSeconds % 60;

                log.info("Sync already completed for tenant: {}. Cooldown: {}m {}s", tenantId, minutes, seconds);

                Map<String, Object> response = new HashMap<>();
                response.put("status", "already_synced");
                response.put("message", String.format("Azure groups were already synced. Please wait %d minute(s) and %d second(s) before syncing again.", minutes, seconds));
                response.put("totalFetched", existingStatus.getTotalFetched());
                response.put("newGroups", existingStatus.getNewGroups());
                response.put("updatedGroups", existingStatus.getUpdatedGroups());
                response.put("completedAt", existingStatus.getCompletedAt());
                response.put("remainingSeconds", remainingSeconds);
                response.put("remainingMinutes", minutes);

                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(response);
            }
        }

        // Check cooldown period (should not reach here if already completed, but as safety)
        if (!azureGroupSyncService.canSync(tenantId)) {
            long remainingSeconds = azureGroupSyncService.getRemainingCooldownSeconds(tenantId);
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;

            log.info("Sync cooldown active for tenant: {}. Remaining: {}m {}s", tenantId, minutes, seconds);
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "status", "cooldown",
                            "message", String.format("Please wait %d minute(s) and %d second(s) before syncing again.", minutes, seconds),
                            "remainingSeconds", remainingSeconds,
                            "remainingMinutes", minutes
                    ));
        }

        // Initiate async sync
        try {
            AzureSyncStatus status = azureGroupSyncService.initiateSync(tenantId);

            return ResponseEntity
                    .accepted()
                    .body(Map.of(
                            "status", "accepted",
                            "message", "Azure group sync has been initiated. It will take some time to complete. Please try again after 10 minutes to check if sync is completed.",
                            "startedAt", status.getStartedAt()
                    ));
        } catch (IllegalStateException e) {
            log.error("Failed to initiate sync for tenant: {}", tenantId, e);
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage()
                    ));
        }
    }

    // ================== Device User Assignment ==================

    /**
     * Assign device users to group (handles both single and bulk)
     * Accepts either a single deviceUserId or a list of deviceUserIds
     */
    @PostMapping("/{groupId}/device-users")
    @Operation(summary = "Assign device users to events group (single or bulk)")
    public ResponseEntity<Map<String, Object>> assignDeviceUsers(
            HttpServletRequest request,
            @PathVariable String groupId,
            @RequestBody Map<String, Object> body
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        String userEmail = jwtUtil.getUserFromRequest(request).getEmail();

        List<String> deviceUserIds = new ArrayList<>();

        // Handle both single and bulk assignment
        if (body.containsKey("deviceUserIds")) {
            // Bulk assignment
            Object userIdsObj = body.get("deviceUserIds");
            if (userIdsObj instanceof List) {
                deviceUserIds = (List<String>) userIdsObj;
            } else {
                throw new IllegalArgumentException("'deviceUserIds' must be an array");
            }
        } else if (body.containsKey("deviceUserId")) {
            // Single assignment
            String singleUserId = (String) body.get("deviceUserId");
            deviceUserIds.add(singleUserId);
        } else {
            throw new IllegalArgumentException("Either 'deviceUserId' or 'deviceUserIds' is required");
        }

        if (deviceUserIds.isEmpty()) {
            throw new IllegalArgumentException("At least one device user ID is required");
        }

        log.info("Assigning {} device user(s) to group: {} for tenant: {} by user: {}",
                deviceUserIds.size(), groupId, tenantId, userEmail);

        // Use bulk assignment service for both cases
        List<EventsGroupDeviceUserMapping> mappings = mappingService.bulkAssignDeviceUsersToGroup(
                tenantId,
                groupId,
                deviceUserIds,
                userEmail
        );

        List<DeviceUserGroupMappingDto> dtos = mappings.stream()
                .map(this::convertMappingToDto)
                .collect(Collectors.toList());

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("assigned", dtos.size());
        response.put("requested", deviceUserIds.size());
        response.put("mappings", dtos);

        if (dtos.size() < deviceUserIds.size()) {
            response.put("message", "Some users were already assigned to this group");
        } else {
            response.put("message", "Successfully assigned " + dtos.size() + " user(s) to group");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Remove device user from group
     */
    @DeleteMapping("/{groupId}/device-users/{deviceUserId}")
    @Operation(summary = "Remove device user from events group")
    public ResponseEntity<Void> removeDeviceUser(
            HttpServletRequest request,
            @PathVariable String groupId,
            @PathVariable String deviceUserId
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        log.info("Removing device user: {} from group: {} for tenant: {}", deviceUserId, groupId, tenantId);

        mappingService.removeDeviceUserFromGroup(tenantId, deviceUserId, groupId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all device users in a group
     */
    @GetMapping("/{groupId}/device-users")
    @Operation(summary = "Get all device users in events group")
    public ResponseEntity<List<DeviceUserGroupMappingDto>> getDeviceUsersInGroup(
            HttpServletRequest request,
            @PathVariable String groupId
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        log.info("Fetching device users for group: {} in tenant: {}", groupId, tenantId);

        List<EventsGroupDeviceUserMapping> mappings = mappingService.getDeviceUsersInGroup(tenantId, groupId);

        List<DeviceUserGroupMappingDto> dtos = mappings.stream()
                .map(this::convertMappingToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get all groups for a device user
     */
    @GetMapping("/device-users/{deviceUserId}/groups")
    @Operation(summary = "Get all groups for a device user")
    public ResponseEntity<List<EventsGroupDto>> getGroupsForDeviceUser(
            HttpServletRequest request,
            @PathVariable String deviceUserId
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        log.info("Fetching groups for device user: {} in tenant: {}", deviceUserId, tenantId);

        List<EventsGroup> groups = mappingService.getGroupsForDeviceUser(tenantId, deviceUserId);

        List<EventsGroupDto> dtos = groups.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // ================== Policy Assignments ==================

    /**
     * Get all policy assignments for an events group
     */
    @GetMapping("/{groupId}/policies")
    @Operation(summary = "Get all policy assignments for events group")
    public ResponseEntity<Map<String, Object>> getGroupPolicies(
            HttpServletRequest request,
            @PathVariable String groupId
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        log.info("Fetching policy assignments for group: {} in tenant: {}", groupId, tenantId);

        // Validate group exists and belongs to tenant
        EventsGroup group = eventsGroupService.getGroupById(tenantId, groupId);

        // Get all policy assignments for this group
        List<PolicyAssignment> assignments = policyAssignmentRepository.findByEventsGroupIdAndTenantId(
                groupId,
                tenantId
        );

        // Convert to DTOs
        List<PolicyAssignmentDto> assignmentDtos = assignments.stream()
                .map(this::convertPolicyAssignmentToDto)
                .collect(Collectors.toList());

        // Build response with group info and assignments
        Map<String, Object> response = new HashMap<>();
        response.put("groupId", group.getPkEventsGroupId());
        response.put("groupName", group.getName());
        response.put("groupType", group.getGroupType().name());
        response.put("authorized", group.getAuthorized());
        response.put("policyCount", assignmentDtos.size());
        response.put("policies", assignmentDtos);

        return ResponseEntity.ok(response);
    }

    // ================== Helper Methods ==================

    private EventsGroupDto convertToDto(EventsGroup group) {
        return EventsGroupDto.builder()
                .pkEventsGroupId(group.getPkEventsGroupId())
                .tenantId(group.getTenantId())
                .name(group.getName())
                .description(group.getDescription())
                .groupType(group.getGroupType().name())
                .authorized(group.getAuthorized())
                .azureGroupId(group.getAzureGroupId())
                .azureGroupDisplayName(group.getAzureGroupDisplayName())
                .syncedAt(group.getSyncedAt())
                .isDefault(group.getIsDefault())
                .isActive(group.getIsActive())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .createdBy(group.getCreatedBy())
                .updatedBy(group.getUpdatedBy())
                .build();
    }

    private DeviceUserGroupMappingDto convertMappingToDto(EventsGroupDeviceUserMapping mapping) {
        DeviceUserGroupMappingDto dto = new DeviceUserGroupMappingDto();
        dto.setMappingId(mapping.getPkMappingId());
        dto.setDeviceUserId(mapping.getFkDeviceUserId());
        dto.setGroupId(mapping.getFkEventsGroupId());
        dto.setAssignedAt(mapping.getAssignedAt());
        dto.setAssignedBy(mapping.getAssignedBy());

        // Set additional details if available from lazy-loaded relationships
        if (mapping.getEventsGroup() != null) {
            dto.setGroupName(mapping.getEventsGroup().getName());
        }

        return dto;
    }

    private PolicyAssignmentDto convertPolicyAssignmentToDto(PolicyAssignment assignment) {
        return PolicyAssignmentDto.builder()
                .assignmentId(assignment.getId())
                .tenantId(assignment.getTenantId())
                .groupId(assignment.getAzureResourceId())
                .assignmentType(assignment.getAssignmentType())
                .resourceId(assignment.getAzureResourceId())
                .resourceName(assignment.getAzureResourceName())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }

    private List<EventsGroup> resolveGroupsBySsoType(String tenantId, String ssoType) {

        return switch (ssoType.toUpperCase()) {
            case "APIKEY" ->
                    eventsGroupService.getGroupsByType(tenantId, EventsGroup.GroupType.APIKEY_GROUP);

            case "AZURE" ->
                    eventsGroupService.getGroupsByType(tenantId, EventsGroup.GroupType.AZURE_GROUP);

            default ->
                    eventsGroupService.getAllGroups(tenantId);
        };
    }

}
