package com.secufusion.iam.controller;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.dto.GroupMemberSyncResult;
import com.secufusion.iam.entity.DeviceUser;
import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.entity.EventsGroupHistory;
import com.secufusion.iam.entity.EventsGroupDeviceUserMapping;
import com.secufusion.iam.entity.PolicyAssignment;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.DeviceUserRepository;
import com.secufusion.iam.repository.EventsGroupDeviceUserMappingRepository;
import com.secufusion.iam.repository.PolicyAssignmentRepository;
import com.secufusion.iam.service.AzureGroupSyncService;
import com.secufusion.iam.service.DeviceUserGroupMappingService;
import com.secufusion.iam.service.EventsGroupService;
import com.secufusion.iam.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/events-groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Events Groups", description = "Manage events groups for API key and Azure AD users")
public class EventsGroupController {

    private final EventsGroupService eventsGroupService;
    private final AzureGroupSyncService azureGroupSyncService;
    private final DeviceUserGroupMappingService mappingService;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final DeviceUserRepository deviceUserRepository;
    private final JwtUtl jwtUtil;
    private final EventsGroupDeviceUserMappingRepository mappingRepository;

    @GetMapping
    @Operation(
        summary = "Get all events groups for tenant",
        description = "Retrieves all events groups for the authenticated tenant with different behavior based on tenant type.\n\n" +
                     "**For APIKEY Tenants:**\n" +
                     "- Returns all APIKEY_GROUP groups from database\n" +
                     "- All groups are authorized by default\n" +
                     "- Includes: group details, policy counts, device user assignments\n" +
                     "- Azure-specific fields are excluded\n\n" +
                     "**For AZURE Tenants:**\n" +
                     "- **authorizedOnly=false** (default): Fetches ALL groups directly from Azure AD API (Microsoft Graph)\n" +
                     "  - Shows both authorized (stored in DB) and unauthorized groups (from Azure AD only)\n" +
                     "  - Unauthorized groups have pkEventsGroupId=null, authorized=false\n" +
                     "  - Unauthorized groups cannot have policies or device users assigned\n" +
                     "- **authorizedOnly=true**: Returns only authorized groups from database\n" +
                     "  - Shows only groups that have been explicitly authorized and persisted\n" +
                     "  - These groups can have policies and device users assigned\n\n" +
                     "**Optional Parameters:**\n" +
                     "- includePolicies=true: Include detailed policy assignment information\n" +
                     "- Includes policy counts by type (browser, network, extension) for all groups"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved groups"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have access to this tenant")
    })
    public ResponseEntity<List<Map<String, Object>>> getAllGroups(
            HttpServletRequest request,
            @Parameter(description = "Filter to show only authorized groups. For Azure tenants: true = only authorized from DB, false/null = all groups from Azure AD API (default: false)")
            @RequestParam(required = false) Boolean authorizedOnly,
            @Parameter(description = "Include full policy assignment details in response (default: false, only returns count and policy names)")
            @RequestParam(required = false, defaultValue = "false") Boolean includePolicies
    ) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String ssoType = tenant.getAuthProviderConfig().getSsoType();
        boolean isAzureTenant = "AZURE".equalsIgnoreCase(ssoType);

        log.info("Fetching events groups for tenant: {} (ssoType={}, authorizedOnly={}, includePolicies={})",
                tenantId, ssoType, authorizedOnly, includePolicies);

        // KEYCLOAK tenants don't use event groups
        if ("KEYCLOAK".equalsIgnoreCase(ssoType)) {
            return ResponseEntity.ok(List.of());
        }

        // For Azure tenants when authorizedOnly is not explicitly true, fetch from Azure AD API
        if (isAzureTenant && !Boolean.TRUE.equals(authorizedOnly)) {
            return handleAzureGroupsWithUnauthorized(tenantId, false, includePolicies);
        }

        // Standard flow: fetch from database only (for APIKEY tenants or Azure with authorizedOnly=true)
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

        Map<String, Long> deviceUserCountByGroup;

        if (!groupIds.isEmpty()) {
            List<EventsGroupDeviceUserMapping> allMappings =
                    mappingRepository.findByFkEventsGroupIdIn(groupIds);

            deviceUserCountByGroup = allMappings.stream()
                    .collect(Collectors.groupingBy(
                            EventsGroupDeviceUserMapping::getFkEventsGroupId,
                            Collectors.counting()
                    ));
        } else {
            deviceUserCountByGroup = new HashMap<>();
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

                    Long deviceUsersCount = deviceUserCountByGroup
                            .getOrDefault(group.getPkEventsGroupId(), 0L);

                    groupData.put("deviceUsersCount", deviceUsersCount);

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

                    // Count policies by type
                    long browserPolicyCount = assignments.stream()
                            .filter(a -> a.getBrowserPolicy() != null)
                            .count();
                    long networkPolicyCount = assignments.stream()
                            .filter(a -> a.getNetworkPolicy() != null)
                            .count();
                    long extensionPolicyCount = assignments.stream()
                            .filter(a -> a.getExtensionPolicy() != null)
                            .count();

                    Map<String, Object> policyCountsByType = new HashMap<>();
                    policyCountsByType.put("browserPolicies", browserPolicyCount);
                    policyCountsByType.put("networkPolicies", networkPolicyCount);
                    policyCountsByType.put("extensionPolicies", extensionPolicyCount);
                    policyCountsByType.put("total", assignments.size());

                    groupData.put("policyCount", assignments.size());
                    groupData.put("policyCountsByType", policyCountsByType);

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


    @GetMapping("/{groupId}")
    @Operation(
        summary = "Get single events group by ID",
        description = "Retrieves a specific events group by its ID. " +
                     "Returns full group details including: " +
                     "- List of mapped device users with assignment details " +
                     "- Policy assignment counts by type (browser, network, extension) " +
                     "- Full policy assignment details with policy information " +
                     "Azure-specific fields are excluded for APIKEY tenants."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved group"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have access to this tenant"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<Map<String, Object>> getGroupById(
            HttpServletRequest request,
            @Parameter(description = "Events group ID (UUID)", required = true)
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

        // Get mapped device users
        List<EventsGroupDeviceUserMapping> mappings = mappingService.getDeviceUsersInGroup(tenantId, groupId);
        List<DeviceUserGroupMappingDto> deviceUserMappings = mappings.stream()
                .map(this::convertMappingToDto)
                .collect(Collectors.toList());

        response.put("deviceUserCount", deviceUserMappings.size());
        response.put("deviceUsers", Map.of(
                "count", deviceUserMappings.size(),
                "items", deviceUserMappings
        ));
        // Get policy assignments for this group
        List<PolicyAssignment> policyAssignments = policyAssignmentRepository.findByEventsGroupIdAndTenantId(
                groupId,
                tenantId
        );

        // Count policies by type
        long browserPolicyCount = policyAssignments.stream()
                .filter(a -> a.getBrowserPolicy() != null)
                .count();
        long networkPolicyCount = policyAssignments.stream()
                .filter(a -> a.getNetworkPolicy() != null)
                .count();
        long extensionPolicyCount = policyAssignments.stream()
                .filter(a -> a.getExtensionPolicy() != null)
                .count();

        Map<String, Object> policyCountsByType = new HashMap<>();
        policyCountsByType.put("browserPolicies", browserPolicyCount);
        policyCountsByType.put("networkPolicies", networkPolicyCount);
        policyCountsByType.put("extensionPolicies", extensionPolicyCount);
        policyCountsByType.put("total", policyAssignments.size());

        response.put("policyCount", policyAssignments.size());
        response.put("policyCountsByType", policyCountsByType);

        // Add policy assignment details
        List<PolicyAssignmentDto> policyDtos = policyAssignments.stream()
                .map(this::convertPolicyAssignmentToDto)
                .collect(Collectors.toList());
        response.put("policyAssignments", policyDtos);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(
        summary = "Create new API key group (APIKEY tenants only)",
        description = "Creates a new APIKEY_GROUP for the tenant. " +
                     "\n\n**For APIKEY Tenants:**\n" +
                     "- Groups are manually created by admins\n" +
                     "- Automatically set to authorized=true upon creation\n" +
                     "- Group name must be unique within the tenant\n" +
                     "- Stored in database with groupType=APIKEY_GROUP\n" +
                     "\n**For AZURE Tenants:**\n" +
                     "- This endpoint is NOT used\n" +
                     "- Azure groups are fetched from Azure AD API\n" +
                     "- Use GET /events-groups to see all Azure groups\n" +
                     "- Use PUT /events-groups/groups/{azureGroupId}/authorize to authorize and persist"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Group created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request - validation errors or duplicate name"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have permission"),
        @ApiResponse(responseCode = "409", description = "Conflict - Group name already exists")
    })
    public ResponseEntity<EventsGroupDto> createGroup(
            HttpServletRequest request,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Group creation details (name and description)",
                required = true
            )
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

        // Assign default policies to newly created APIKEY group
        azureGroupSyncService.assignDefaultPolicies(created, tenantId, userEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(created));
    }

    @PutMapping("/{groupId}")
    @Operation(
        summary = "Update events group",
        description = "Updates name and/or description of an existing events group. " +
                     "Can update both APIKEY_GROUP and AZURE_GROUP types."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Group updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have permission"),
        @ApiResponse(responseCode = "404", description = "Group not found"),
        @ApiResponse(responseCode = "409", description = "Conflict - New name already exists")
    })
    public ResponseEntity<EventsGroupDto> updateGroup(
            HttpServletRequest request,
            @Parameter(description = "Events group ID to update", required = true)
            @PathVariable String groupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Updated group details (name and/or description)",
                required = true
            )
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

    @PutMapping("/groups/{groupId}/{action}")
    @Operation(
        summary = "Authorize or unauthorize a group (admin operation)",
        description = "Changes the authorization status of a group with different behavior based on tenant type and action.\n\n" +
                     "**For APIKEY Tenants:**\n" +
                     "- **Authorize**: Sets authorized=true (group already exists in database)\n" +
                     "- **Unauthorize**: Sets authorized=false (group remains in database)\n" +
                     "- groupId is always the database ID (pkEventsGroupId)\n\n" +
                     "**For AZURE Tenants - Authorize:**\n" +
                     "- groupId can be either:\n" +
                     "  1. **Azure Group ID** (from Azure AD): Fetches group details from Microsoft Graph API and persists to database\n" +
                     "  2. **Database ID** (pkEventsGroupId): Updates existing authorized group\n" +
                     "- Smart detection: automatically determines if groupId is Azure Group ID or database ID\n" +
                     "- If Azure Group ID: validates group exists in Azure AD, then creates database record with authorized=true\n" +
                     "- If database ID: updates authorized flag to true\n\n" +
                     "**For AZURE Tenants - Unauthorize:**\n" +
                     "- groupId must be database ID (pkEventsGroupId)\n" +
                     "- **HARD DELETE**: Completely removes group from database\n" +
                     "- Cannot unauthorize groups with assigned device users or policies\n" +
                     "- After unauthorization, group still exists in Azure AD but won't be stored locally\n\n" +
                     "**Actions:**\n" +
                     "- 'authorize': Make group available for policy assignments and device user mappings\n" +
                     "- 'unauthorize': Remove group from policy assignments (APIKEY) or delete from database (AZURE)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authorization status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid action - must be 'authorize' or 'unauthorize'"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have admin permission"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<EventsGroupDto> setAuthorization(
            HttpServletRequest request,
            @Parameter(description = "Events group ID (database ID) or Azure Group ID", required = true)
            @PathVariable String groupId,
            @Parameter(description = "Action to perform: 'authorize' or 'unauthorize'", required = true, example = "authorize")
            @PathVariable String action
    ) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String userEmail = jwtUtil.getUserFromRequest(request).getEmail();
        boolean isAzureTenant = "AZURE".equalsIgnoreCase(tenant.getAuthProviderConfig().getSsoType());

        log.info("Setting authorization for group: {} with action: {} for tenant: {} by user: {}",
                groupId, action, tenantId, userEmail);

        EventsGroup updated = switch (action.toLowerCase()) {
            case "authorize" -> isAzureTenant
                    ? azureGroupSyncService.authorizeAzureGroupById(tenant, groupId, userEmail)
                    : eventsGroupService.authorizeGroup(tenantId, groupId, userEmail);
            case "unauthorize" -> eventsGroupService.handleGroupUnauthorization(tenantId, groupId, userEmail, isAzureTenant);
            default -> throw new IllegalArgumentException("Invalid action. Allowed values: authorize, unauthorize");
        };

        return ResponseEntity.ok(convertToDto(updated));
    }

    @DeleteMapping("/{groupId}")
    @Operation(
        summary = "Delete events group (soft delete)",
        description = "Soft deletes an events group by setting isActive=false. " +
                     "Also removes all device user mappings and policy assignments for this group. " +
                     "Default groups cannot be deleted."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Group deleted successfully (No Content)"),
        @ApiResponse(responseCode = "400", description = "Cannot delete default group"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have permission"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<Void> deleteGroup(
            HttpServletRequest request,
            @Parameter(description = "Events group ID to delete", required = true)
            @PathVariable String groupId
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        String userEmail = jwtUtil.getUserFromRequest(request).getEmail();
        log.info("Deleting events group: {} for tenant: {} by user: {}", groupId, tenantId, userEmail);

        eventsGroupService.deleteGroup(tenantId, groupId);
        return ResponseEntity.noContent().build();
    }

    // ================== 2. Device User Assignment (POST, GET, DELETE) ==================

    @PostMapping("/{groupId}/device-users")
    @Operation(
        summary = "Assign device users to events group (single or bulk)",
        description = "Assigns one or more device users to an events group. " +
                     "Supports both single and bulk assignment in one endpoint. " +
                     "Request body must contain either 'deviceUserId' (string) for single assignment " +
                     "or 'deviceUserIds' (array) for bulk assignment. " +
                     "Skips users already assigned to the group. " +
                     "Returns count of newly assigned users and list of mapping details."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Users assigned successfully (Created)"),
        @ApiResponse(responseCode = "400", description = "Invalid request - missing or invalid deviceUserId/deviceUserIds"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have permission"),
        @ApiResponse(responseCode = "404", description = "Group or device user not found")
    })
    public ResponseEntity<Map<String, Object>> assignDeviceUsers(
            HttpServletRequest request,
            @Parameter(description = "Events group ID", required = true)
            @PathVariable String groupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Device user assignment payload. " +
                             "For single: {\"deviceUserId\": \"user-id\"}, " +
                             "For bulk: {\"deviceUserIds\": [\"user-id-1\", \"user-id-2\"]}",
                required = true
            )
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

    @DeleteMapping("/{groupId}/device-users/{deviceUserId}")
    @Operation(
        summary = "Remove device user from events group",
        description = "Removes the mapping between a device user and an events group. " +
                     "The device user itself is not deleted, only the group membership."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User removed from group successfully (No Content)"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have permission"),
        @ApiResponse(responseCode = "404", description = "Group, device user, or mapping not found")
    })
    public ResponseEntity<Void> removeDeviceUser(
            HttpServletRequest request,
            @Parameter(description = "Events group ID", required = true)
            @PathVariable String groupId,
            @Parameter(description = "Device user ID to remove", required = true)
            @PathVariable String deviceUserId
    ) {
        String tenantId = jwtUtil.getTenantFromRequest(request).getTenantID();
        log.info("Removing device user: {} from group: {} for tenant: {}", deviceUserId, groupId, tenantId);

        mappingService.removeDeviceUserFromGroup(tenantId, deviceUserId, groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/device-users")
    @Operation(
        summary = "Get all device users in events group",
        description = "Retrieves all device users (API key or Azure AD users) assigned to a specific events group. " +
                     "Returns mapping details including assignment timestamp and assigned by user."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved device users"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have access to this tenant"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<List<DeviceUserGroupMappingDto>> getDeviceUsersInGroup(
            HttpServletRequest request,
            @Parameter(description = "Events group ID", required = true)
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

    // ================== 3. Policy Assignments (GET) ==================

    @GetMapping("/{groupId}/policies")
    @Operation(
        summary = "Get all policy assignments for events group",
        description = "Retrieves all policy assignments for a specific events group. " +
                     "Returns group details along with count and list of all assigned policies. " +
                     "Policies are assigned externally and tracked in the policy_assignments table."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved policy assignments"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have access to this tenant"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<Map<String, Object>> getGroupPolicies(
            HttpServletRequest request,
            @Parameter(description = "Events group ID", required = true)
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

        // Count policies by type
        long browserPolicyCount = assignments.stream()
                .filter(a -> a.getBrowserPolicy() != null)
                .count();
        long networkPolicyCount = assignments.stream()
                .filter(a -> a.getNetworkPolicy() != null)
                .count();
        long extensionPolicyCount = assignments.stream()
                .filter(a -> a.getExtensionPolicy() != null)
                .count();

        Map<String, Object> policyCountsByType = new HashMap<>();
        policyCountsByType.put("browserPolicies", browserPolicyCount);
        policyCountsByType.put("networkPolicies", networkPolicyCount);
        policyCountsByType.put("extensionPolicies", extensionPolicyCount);
        policyCountsByType.put("total", assignments.size());

        // Convert to DTOs with full policy details
        List<PolicyAssignmentDto> assignmentDtos = assignments.stream()
                .map(this::convertPolicyAssignmentToDto)
                .collect(Collectors.toList());

        // Build response with group info, policy counts, and full assignment details
        Map<String, Object> response = new HashMap<>();
        response.put("groupId", group.getPkEventsGroupId());
        response.put("groupName", group.getName());
        response.put("groupType", group.getGroupType().name());
        response.put("authorized", group.getAuthorized());
        response.put("policyCount", assignments.size());
        response.put("policyCountsByType", policyCountsByType);
        response.put("policyAssignments", assignmentDtos);

        return ResponseEntity.ok(response);
    }

    // ================== 4. Statistics (GET) ==================

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

        // Set device user email if available
        if (mapping.getDeviceUser() != null) {
            dto.setDeviceUserEmail(mapping.getDeviceUser().getEmail());
        }

        return dto;
    }

    private PolicyAssignmentDto convertPolicyAssignmentToDto(PolicyAssignment assignment) {
        PolicyAssignmentDto.PolicyAssignmentDtoBuilder builder = PolicyAssignmentDto.builder()
                .assignmentId(assignment.getId())
                .tenantId(assignment.getTenantId())
                .groupId(assignment.getAzureResourceId())
                .assignmentType(assignment.getAssignmentType())
                .resourceId(assignment.getAzureResourceId())
                .resourceName(assignment.getAzureResourceName())
                .assignedAt(assignment.getAssignedAt());

        // Add BrowserPolicy details if present
        if (assignment.getBrowserPolicy() != null) {
            builder.browserPolicy(PolicyAssignmentDto.PolicyDetailsDto.builder()
                    .policyId(assignment.getBrowserPolicy().getPkBrowserPolicyId())
                    .name(assignment.getBrowserPolicy().getName())
                    .description(assignment.getBrowserPolicy().getDescription())
                    .policyType(assignment.getBrowserPolicy().getPolicyType())
                    .policyKey(assignment.getBrowserPolicy().getPolicyKey())
                    .version(assignment.getBrowserPolicy().getVersion())
                    .isActive(assignment.getBrowserPolicy().isActive())
                    .createdAt(assignment.getBrowserPolicy().getCreatedAt())
                    .updatedAt(assignment.getBrowserPolicy().getUpdatedAt())
                    .build());
        }

        // Add NetworkPolicy details if present
        if (assignment.getNetworkPolicy() != null) {
            builder.networkPolicy(PolicyAssignmentDto.PolicyDetailsDto.builder()
                    .policyId(assignment.getNetworkPolicy().getPkNetworkPolicyId())
                    .name(assignment.getNetworkPolicy().getName())
                    .description(assignment.getNetworkPolicy().getDescription())
                    .policyKey(assignment.getNetworkPolicy().getPolicyKey())
                    .version(assignment.getNetworkPolicy().getVersion())
                    .isActive(assignment.getNetworkPolicy().isActive())
                    .createdAt(assignment.getNetworkPolicy().getCreatedAt())
                    .updatedAt(assignment.getNetworkPolicy().getUpdatedAt())
                    .build());
        }

        // Add ExtensionPolicy details if present
        if (assignment.getExtensionPolicy() != null) {
            builder.extensionPolicy(PolicyAssignmentDto.PolicyDetailsDto.builder()
                    .policyId(assignment.getExtensionPolicy().getPkExtensionPolicyId())
                    .name(assignment.getExtensionPolicy().getName())
                    .description(assignment.getExtensionPolicy().getDescription())
                    .policyKey(assignment.getExtensionPolicy().getPolicyKey())
                    .version(assignment.getExtensionPolicy().getVersion())
                    .isActive(assignment.getExtensionPolicy().getIsActive())
                    .createdAt(assignment.getExtensionPolicy().getCreatedAt())
                    .updatedAt(assignment.getExtensionPolicy().getUpdatedAt())
                    .build());
        }

        return builder.build();
    }

    /**
     * Handle Azure groups with unauthorized groups from Azure AD API
     */
    private ResponseEntity<List<Map<String, Object>>> handleAzureGroupsWithUnauthorized(
            String tenantId,
            Boolean authorizedOnly,
            Boolean includePolicies) {

        List<Map<String, Object>> response = azureGroupSyncService.getAzureGroupsWithPolicyInfo(
                tenantId,
                authorizedOnly,
                includePolicies
        );

        return ResponseEntity.ok(response);
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

    // ================== 5. Azure Member Sync ==================

    @PostMapping("/sync-members")
    @Operation(
            summary = "Sync Azure AD group members for all authorized groups",
            description = "Fetches members from Azure AD for all authorized Azure groups and maps them to existing device users. " +
                    "Only works for AZURE tenants. Members are matched by email address to existing device users in the system. " +
                    "Duplicate mappings are skipped. Returns the total number of new mappings created."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Member sync completed successfully"),
            @ApiResponse(responseCode = "400", description = "Tenant is not Azure-enabled"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<Map<String, Object>> syncAzureGroupMembers(HttpServletRequest request) {
        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String ssoType = tenant.getAuthProviderConfig().getSsoType();

        if (!"AZURE".equalsIgnoreCase(ssoType)) {
            throw new IllegalArgumentException("Member sync is only available for Azure tenants");
        }

        log.info("Manual Azure group member sync triggered for tenant: {} by user: {}",
                tenant.getTenantID(), jwtUtil.getUserFromRequest(request).getEmail());

        List<GroupMemberSyncResult> syncResults = azureGroupSyncService.syncAllAuthorizedGroupMembers(tenant);

        int totalMatched = syncResults.stream().mapToInt(GroupMemberSyncResult::getMatchedDeviceUsers).sum();
        int totalUnmatched = syncResults.stream().mapToInt(r -> r.getUnmatchedEmails().size()).sum();
        int totalNewMappings = syncResults.stream().mapToInt(GroupMemberSyncResult::getNewMappingsCreated).sum();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Member sync completed successfully");
        response.put("groupsSynced", syncResults.size());
        response.put("totalMatchedDeviceUsers", totalMatched);
        response.put("totalUnmatchedMembers", totalUnmatched);
        response.put("totalNewMappings", totalNewMappings);
        response.put("groups", syncResults);

        return ResponseEntity.ok(response);
    }

    // ================== 6. Statistics ==================

    /**
     * Get group membership statistics
     */
    @GetMapping("/stats/memberships")
    @Operation(
            summary = "Get group membership statistics",
            description = "Returns statistics showing how many users belong to each group"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved group membership statistics"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<GroupMembershipStatsDto>> getGroupMembershipStats(
            HttpServletRequest request) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();

        log.info("Fetching group membership statistics for tenant: {}", tenantId);

        List<GroupMembershipStatsDto> stats =
                mappingService.getGroupMembershipStats(tenantId);

        return ResponseEntity.ok(stats);
    }

    /**
     * Get user group membership statistics
     */
    @GetMapping("/device-users/stats/memberships")
    @Operation(
            summary = "Get device user group membership statistics",
            description = "Returns statistics showing how many authorized groups each device user belongs to. " +
                    "Only authorized groups are counted in the statistics. " +
                    "For APIKEY tenants: All manually created groups are authorized by default. " +
                    "For AZURE tenants: Only groups that have been explicitly authorized by an admin are counted."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user membership statistics"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<UserGroupMembershipStatsDto>> getUserMembershipStats(
            HttpServletRequest request) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();

        log.info("Fetching user group membership statistics for tenant: {}", tenantId);

        List<UserGroupMembershipStatsDto> stats =
                mappingService.getUserGroupMembershipStats(tenantId);

        return ResponseEntity.ok(stats);
    }

    /**
     * Get all policies mapped to a device user through their group memberships
     */
    @GetMapping("/device-users/{deviceUserId}/policies")
    @Operation(
            summary = "Get policies for a device user (one per type)",
            description = "Returns the effective policies for a device user through their group memberships. " +
                    "IMPORTANT: Returns maximum ONE policy per type (browser, network, extension). " +
                    "If user belongs to multiple groups with policies of the same type, the first policy found is returned. " +
                    "For APIKEY tenants: Returns policies from manually assigned groups. " +
                    "For AZURE tenants: Returns policies from auto-assigned authorized groups based on Azure AD membership."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user policies"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Device user not found")
    })
    public ResponseEntity<Map<String, Object>> getPoliciesForDeviceUser(
            HttpServletRequest request,
            @PathVariable String deviceUserId) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();

        log.info("Fetching policies for device user: {} in tenant: {}", deviceUserId, tenantId);

        // Validate device user exists and belongs to tenant
        DeviceUser deviceUser = deviceUserRepository.findByIdAndTenantId(deviceUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device user not found"));

        // Get all policy assignments for the user
        List<PolicyAssignment> policyAssignments = mappingService.resolvePoliciesForDeviceUser(tenantId, deviceUserId);

        // Build response with organized policy information
        Map<String, Object> response = new HashMap<>();
        response.put("deviceUserId", deviceUser.getPkDeviceUserId());
        response.put("email", deviceUser.getEmail());
        response.put("displayName", deviceUser.getDisplayName());
        response.put("source", deviceUser.getSource());
        response.put("totalPolicies", policyAssignments.size());

        // Group policies by type
        List<Map<String, Object>> browserPolicies = new ArrayList<>();
        List<Map<String, Object>> networkPolicies = new ArrayList<>();
        List<Map<String, Object>> extensionPolicies = new ArrayList<>();

        for (PolicyAssignment assignment : policyAssignments) {
            if (assignment.getBrowserPolicy() != null) {
                Map<String, Object> policyInfo = new HashMap<>();
                policyInfo.put("assignmentId", assignment.getId());
                policyInfo.put("policyId", assignment.getBrowserPolicy().getPkBrowserPolicyId());
                policyInfo.put("policyName", assignment.getBrowserPolicy().getName());
                policyInfo.put("assignedTo", assignment.getAzureResourceName());
                policyInfo.put("assignedAt", assignment.getAssignedAt());
                browserPolicies.add(policyInfo);
            }

            if (assignment.getNetworkPolicy() != null) {
                Map<String, Object> policyInfo = new HashMap<>();
                policyInfo.put("assignmentId", assignment.getId());
                policyInfo.put("policyId", assignment.getNetworkPolicy().getPkNetworkPolicyId());
                policyInfo.put("policyName", assignment.getNetworkPolicy().getName());
                policyInfo.put("assignedTo", assignment.getAzureResourceName());
                policyInfo.put("assignedAt", assignment.getAssignedAt());
                networkPolicies.add(policyInfo);
            }

            if (assignment.getExtensionPolicy() != null) {
                Map<String, Object> policyInfo = new HashMap<>();
                policyInfo.put("assignmentId", assignment.getId());
                policyInfo.put("policyId", assignment.getExtensionPolicy().getPkExtensionPolicyId());
                policyInfo.put("policyName", assignment.getExtensionPolicy().getName());
                policyInfo.put("assignedTo", assignment.getAzureResourceName());
                policyInfo.put("assignedAt", assignment.getAssignedAt());
                extensionPolicies.add(policyInfo);
            }
        }

        Map<String, Object> policiesByType = new HashMap<>();
        policiesByType.put("browserPolicies", browserPolicies);
        policiesByType.put("networkPolicies", networkPolicies);
        policiesByType.put("extensionPolicies", extensionPolicies);

        response.put("policies", policiesByType);

        log.info("Found {} total policies for device user: {}", policyAssignments.size(), deviceUserId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get all groups for a device user with complete details
     */
    @GetMapping("/device-users/{deviceUserId}/groups")
    @Operation(
            summary = "Get all groups for a device user with details",
            description = "Returns complete group membership information for a device user including assignment metadata"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved device user groups"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Device user not found")
    })
    public ResponseEntity<DeviceUserWithGroupsDto> getGroupsForDeviceUser(
            HttpServletRequest request,
            @PathVariable String deviceUserId) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();

        log.info("Fetching groups for device user: {} in tenant: {}", deviceUserId, tenantId);

        DeviceUserWithGroupsDto userWithGroups =
                mappingService.getDeviceUserWithGroups(tenantId, deviceUserId);

        return ResponseEntity.ok(userWithGroups);
    }

    // ================== 7. History ==================

    @GetMapping("/{groupId}/history")
    @Operation(
            summary = "Get history for a specific group",
            description = "Returns the full audit trail for a group including authorization, " +
                    "policy assignments, and member sync events."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved group history"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<EventsGroupHistory>> getGroupHistory(
            HttpServletRequest request,
            @PathVariable String groupId) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        List<EventsGroupHistory> history = azureGroupSyncService.getGroupHistory(
                groupId, tenant.getTenantID());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history")
    @Operation(
            summary = "Get all group history for the tenant",
            description = "Returns the full audit trail for all groups in the tenant."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved tenant history"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<EventsGroupHistory>> getTenantHistory(HttpServletRequest request) {

        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        List<EventsGroupHistory> history = azureGroupSyncService.getTenantHistory(
                tenant.getTenantID());
        return ResponseEntity.ok(history);
    }
}
