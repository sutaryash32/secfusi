package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.AzureAssignmentDto;
import com.secufusion.tenant.dto.GroupInfoDto;
import com.secufusion.tenant.dto.GroupPolicyMappingRequest;
import com.secufusion.tenant.dto.GroupPolicyMappingResponse;
import com.secufusion.tenant.dto.PolicyGroupAssignmentDto;
import com.secufusion.tenant.dto.PolicyMappingRequest;
import com.secufusion.tenant.entity.BrowserPolicy;
import com.secufusion.tenant.entity.EventsGroup;
import com.secufusion.tenant.entity.ExtensionPolicy;
import com.secufusion.tenant.entity.NetworkPolicy;
import com.secufusion.tenant.entity.PolicyAssignment;
import com.secufusion.tenant.exception.GlobalException;
import com.secufusion.tenant.repository.BrowserPolicyRepository;
import com.secufusion.tenant.repository.EventsGroupRepository;
import com.secufusion.tenant.repository.ExtensionPolicyRepository;
import com.secufusion.tenant.repository.NetworkPolicyRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.secufusion.tenant.dto.PolicyAssignmentResponseDto;
import com.secufusion.tenant.mapper.PolicyAssignmentMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyMappingService {

    private final BrowserPolicyRepository browserPolicyRepository;
    private final NetworkPolicyRepository networkPolicyRepository;
    private final PolicyAssignmentRepository assignmentRepository;
    private final ExtensionPolicyRepository extensionPolicyRepository;
    private final EventsGroupRepository eventsGroupRepository;
    private final PolicyAssignmentMapper mapper;

    // =================================================================================
    // BULK OPERATIONS (REPLACE ALL)
    // =================================================================================

    @Transactional(rollbackFor = Exception.class)
    public List<PolicyAssignment> mapPolicyToAzureResources(String tenantId, PolicyMappingRequest request) {
        log.info("Mapping Browser Policy {} to {} Azure resources for tenant {}",
                request.getPolicyId(), request.getAssignments().size(), tenantId);

        try {
            BrowserPolicy policy = getBrowserPolicySecurely(request.getPolicyId(), tenantId);

            // Clear existing and flush
            assignmentRepository.deleteByBrowserPolicy_PkBrowserPolicyId(policy.getPkBrowserPolicyId());
            assignmentRepository.flush();

            return saveNewAssignments(request.getAssignments(), policy, null, null, tenantId);

        } catch (Exception e) {
            handleDbExceptions(e, "mapping browser policy");
            return Collections.emptyList();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public List<PolicyAssignment> mapNetworkPolicyToAzureResources(String tenantId, PolicyMappingRequest request) {
        log.info("Mapping Network Policy {} to {} Azure resources for tenant {}",
                request.getPolicyId(), request.getAssignments().size(), tenantId);

        try {
            NetworkPolicy policy = getNetworkPolicySecurely(request.getPolicyId(), tenantId);

            // Clear existing and flush
            assignmentRepository.deleteByNetworkPolicy_PkNetworkPolicyId(policy.getPkNetworkPolicyId());
            assignmentRepository.flush();

            return saveNewAssignments(request.getAssignments(), null, policy, null, tenantId);

        } catch (Exception e) {
            handleDbExceptions(e, "mapping network policy");
            return Collections.emptyList();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public List<PolicyAssignment> mapExtensionPolicyToAzureResources(String tenantId, PolicyMappingRequest request) {
        log.info("Mapping Extension Policy {} to {} Azure resources for tenant {}", request.getPolicyId(), request.getAssignments().size(), tenantId);

        try {

            ExtensionPolicy policy = getExtensionPolicySecurely(request.getPolicyId(), tenantId);

            assignmentRepository.deleteByExtensionPolicy_PkExtensionPolicyId(policy.getPkExtensionPolicyId());

            assignmentRepository.flush();

            return saveNewAssignments(request.getAssignments(), null, null, policy, tenantId);

        } catch (Exception e) {
            handleDbExceptions(e, "mapping extension policy");
            return Collections.emptyList();
        }
    }

    // =================================================================================
    // SINGLE ITEM OPERATIONS (ADD / UPDATE / DELETE)
    // =================================================================================

    /**
     * Adds ONE assignment to a policy (Browser or Network).
     * Now supports 'policyType' to distinguish between repositories.
     */
    @Transactional(rollbackFor = Exception.class)
    public PolicyAssignment addSingleAssignment(String tenantId, String policyType, String policyId, AzureAssignmentDto dto) {
        log.info("Adding single assignment to {} policy {} for tenant {}", policyType, policyId, tenantId);

        try {
            boolean exists = false;
            PolicyAssignment assignment = null;

            if ("browser".equalsIgnoreCase(policyType)) {
                BrowserPolicy policy = getBrowserPolicySecurely(policyId, tenantId);
                exists = assignmentRepository.existsByBrowserPolicy_PkBrowserPolicyIdAndAzureResourceId(
                        policy.getPkBrowserPolicyId(), dto.getId());

                if (!exists) {
                    assignment = buildAssignmentEntity(policy, null, null, dto, tenantId);
                }

            } else if ("network".equalsIgnoreCase(policyType)) {
                NetworkPolicy policy = getNetworkPolicySecurely(policyId, tenantId);
                exists = assignmentRepository.existsByNetworkPolicy_PkNetworkPolicyIdAndAzureResourceId(
                        policy.getPkNetworkPolicyId(), dto.getId());

                if (!exists) {
                    assignment = buildAssignmentEntity(null, policy, null, dto, tenantId);
                }

            } else if ("extension".equalsIgnoreCase(policyType)) {
                ExtensionPolicy policy = getExtensionPolicySecurely(policyId, tenantId);
                exists = assignmentRepository.existsByExtensionPolicy_PkExtensionPolicyIdAndAzureResourceId(
                        policy.getPkExtensionPolicyId(), dto.getId());

                if (!exists) {
                    assignment = buildAssignmentEntity(null, null, policy, dto, tenantId);
                }

            }
            else {
                throw new IllegalArgumentException("Invalid policy type: " + policyType);
            }

            if (exists) {
                throw new DataIntegrityViolationException("This Azure resource is already assigned to this policy.");
            }

            return assignmentRepository.save(assignment);

        } catch (Exception e) {
            handleDbExceptions(e, "adding single assignment");
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PolicyAssignment updateSingleAssignment(String tenantId, String assignmentId, AzureAssignmentDto dto) {
        log.info("Updating assignment {} for tenant {}", assignmentId, tenantId);
        try {
            PolicyAssignment assignment = assignmentRepository.findByIdAndTenantId(assignmentId, tenantId)
                    .orElseThrow(() -> new RuntimeException("Assignment not found or access denied"));

            assignment.setAzureResourceId(dto.getId());
            assignment.setAzureResourceName(dto.getName());
            assignment.setAssignmentType(dto.getType());
            // We do NOT update the policy reference (FK) itself, just the target resource.

            return assignmentRepository.save(assignment);
        } catch (Exception e) {
            handleDbExceptions(e, "updating assignment");
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAssignment(String tenantId, String id) {
        try {
            PolicyAssignment assignment = assignmentRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new RuntimeException("Assignment not found or access denied"));
            assignmentRepository.delete(assignment);
            return true;
        } catch (DataAccessException e) {
            log.error("Database error deleting assignment ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete the assignment.", e);
        }
    }

    // =================================================================================
    // READ OPERATIONS
    // =================================================================================

    public PolicyAssignmentResponseDto getMappingById(String assignmentId, String tenantId) {

        try {

            PolicyAssignment assignment =
                    assignmentRepository.findByIdAndTenantId(assignmentId, tenantId)
                            .orElseThrow(() -> new RuntimeException("Assignment not found or access denied"));

            return mapper.map(assignment);

        } catch (DataAccessException e) {
            throw new RuntimeException("Failed to retrieve the specific assignment.", e);
        }
    }

    public List<PolicyAssignmentResponseDto> getAllMappingsForTenant(String tenantId) {

        List<PolicyAssignment> assignments =
                assignmentRepository.findByTenantId(tenantId);

        return assignments.stream()
                .map(mapper::map)
                .toList();
    }
    // =================================================================================
    // GROUP POLICY MAPPING (MULTI-POLICY, MULTI-GROUP)
    // =================================================================================

    @Transactional(rollbackFor = Exception.class)
    public GroupPolicyMappingResponse assignPoliciesToGroups(String tenantId, GroupPolicyMappingRequest request) {
        log.info("Assigning policies to groups for tenant {}", tenantId);

        if (!"GROUP".equalsIgnoreCase(request.getAssignmentType())) {
            throw new GlobalException("assignmentType must be 'GROUP'");
        }

        if (request.getAssignments() == null || request.getAssignments().isEmpty()) {
            throw new GlobalException("assignments list must not be empty");
        }

        // Collect all unique group IDs from the request for batch validation
        Set<String> allGroupIds = request.getAssignments().stream()
                .filter(a -> a.getEventGroups() != null)
                .flatMap(a -> a.getEventGroups().stream())
                .map(GroupInfoDto::getPkEventsGroupId)
                .collect(Collectors.toSet());

        // Batch validate all groups exist in EventsGroup for this tenant
        List<EventsGroup> validGroups = eventsGroupRepository
                .findAllByPkEventsGroupIdInAndTenantId(new ArrayList<>(allGroupIds), tenantId);

        Map<String, EventsGroup> groupMap = validGroups.stream()
                .collect(Collectors.toMap(EventsGroup::getPkEventsGroupId, g -> g));

        Set<String> invalidGroupIds = allGroupIds.stream()
                .filter(id -> !groupMap.containsKey(id))
                .collect(Collectors.toSet());

        if (!invalidGroupIds.isEmpty()) {
            throw new IllegalArgumentException("Groups not found for this tenant: " + invalidGroupIds);
        }

        List<PolicyAssignment> allCreated = new ArrayList<>();

        // Collect which policy types are present in this request so we only
        // delete assignments for those types. Unmentioned types are left intact.
        Set<String> policyTypesInRequest = request.getAssignments().stream()
                .filter(a -> a.getPolicyType() != null)
                .map(a -> a.getPolicyType().toUpperCase())
                .collect(Collectors.toSet());

        for (String groupId : allGroupIds) {
            if (policyTypesInRequest.contains("BROWSER"))
                assignmentRepository.deleteByAzureResourceIdAndTenantIdAndBrowserPolicyIsNotNull(groupId, tenantId);
            if (policyTypesInRequest.contains("NETWORK"))
                assignmentRepository.deleteByAzureResourceIdAndTenantIdAndNetworkPolicyIsNotNull(groupId, tenantId);
            if (policyTypesInRequest.contains("EXTENSION"))
                assignmentRepository.deleteByAzureResourceIdAndTenantIdAndExtensionPolicyIsNotNull(groupId, tenantId);
        }
        assignmentRepository.flush();

        for (PolicyGroupAssignmentDto assignment : request.getAssignments()) {
            String policyType = assignment.getPolicyType();
            String policyId = assignment.getPolicyId();

            if (policyType == null || policyId == null) {
                throw new IllegalArgumentException("policyType and policyId are required for each assignment");
            }

            BrowserPolicy bPolicy = null;
            NetworkPolicy nPolicy = null;
            ExtensionPolicy ePolicy = null;

            switch (policyType.toUpperCase()) {
                case "BROWSER":
                    bPolicy = getBrowserPolicySecurely(policyId, tenantId);
                    break;

                case "NETWORK":
                    nPolicy = getNetworkPolicySecurely(policyId, tenantId);
                    break;

                case "EXTENSION":
                    ePolicy = getExtensionPolicySecurely(policyId, tenantId);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid policyType: " + policyType + ". Must be BROWSER, NETWORK, or EXTENSION");
            }

            if (assignment.getEventGroups() != null) {
                for (GroupInfoDto groupInfo : assignment.getEventGroups()) {
                    EventsGroup group = groupMap.get(groupInfo.getPkEventsGroupId());

                    if (group == null) {
                        throw new GlobalException("Group not found for this tenant: " + groupInfo.getPkEventsGroupId());
                    }

                    PolicyAssignment pa = PolicyAssignment.builder()
                            .browserPolicy(bPolicy)
                            .networkPolicy(nPolicy)
                            .extensionPolicy(ePolicy)
                            .azureResourceId(group.getPkEventsGroupId())
                            .azureResourceName(group.getName())
                            .assignmentType("GROUP")
                            .tenantId(tenantId)
                            .assignedAt(LocalDateTime.now())
                            .build();

                    allCreated.add(pa);
                }
            }
        }

        List<PolicyAssignment> saved = assignmentRepository.saveAll(allCreated);

        log.info("Created {} group policy assignments for tenant {}", saved.size(), tenantId);

        return GroupPolicyMappingResponse.builder()
                .assignmentType("GROUP")
                .assignments(request.getAssignments())
                .totalAssignmentsCreated(saved.size())
                .build();
    }

    // =================================================================================
    // HELPER METHODS
    // =================================================================================

    /**
     * Unified helper to build and save a list of assignments.
     * Prevents code duplication between Browser and Network bulk methods.
     */
    private List<PolicyAssignment> saveNewAssignments(
            List<AzureAssignmentDto> dtos,
            BrowserPolicy bPolicy,
            NetworkPolicy nPolicy,
            ExtensionPolicy ePolicy,
            String tenantId) {

        List<PolicyAssignment> newAssignments = new ArrayList<>();
        if (dtos != null) {
            for (AzureAssignmentDto dto : dtos) {
                newAssignments.add(buildAssignmentEntity(bPolicy, nPolicy, ePolicy, dto, tenantId));
            }
        }
        return assignmentRepository.saveAll(newAssignments);
    }

    private PolicyAssignment buildAssignmentEntity(
            BrowserPolicy bPolicy,
            NetworkPolicy nPolicy,
            ExtensionPolicy ePolicy,
            AzureAssignmentDto dto,
            String tenantId) {

        return PolicyAssignment.builder()
                .browserPolicy(bPolicy)
                .networkPolicy(nPolicy)
                .extensionPolicy(ePolicy)
                .azureResourceId(dto.getId())
                .azureResourceName(dto.getName())
                .assignmentType(dto.getType())
                .tenantId(tenantId)
                .assignedAt(LocalDateTime.now())
                .build();
    }

    private BrowserPolicy getBrowserPolicySecurely(String policyId, String tenantId) {
        return browserPolicyRepository.findById(policyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()) || p.getFkTenantId() == null)
                .orElseThrow(() -> new RuntimeException("Browser Policy not found or access denied"));
    }

    private NetworkPolicy getNetworkPolicySecurely(String policyId, String tenantId) {
        return networkPolicyRepository.findById(policyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()) || p.getFkTenantId() == null)
                .orElseThrow(() -> new RuntimeException("Network Policy not found or access denied"));
    }
    private ExtensionPolicy getExtensionPolicySecurely(String policyId, String tenantId) {
        return extensionPolicyRepository.findByIdWithRelations(policyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()) || p.getFkTenantId() == null)
                .orElseThrow(() -> new RuntimeException("Extension Policy not found or access denied"));
    }

    private void handleDbExceptions(Exception e, String action) {
        if (e instanceof DataIntegrityViolationException) {
            log.error("Data integrity violation while {}: {}", action, e.getMessage());
            throw new RuntimeException("Database error: Duplicate entry or constraint violation.", e);
        } else if (e instanceof CannotAcquireLockException || e instanceof DeadlockLoserDataAccessException) {
            log.error("Lock error while {}: {}", action, e.getMessage());
            throw new RuntimeException("System busy, please try again.", e);
        } else if (e instanceof DataAccessException) {
            log.error("Data access error while {}: {}", action, e.getMessage());
            throw new RuntimeException("Database access error.", e);
        } else {
            log.error("Unexpected error while {}: {}", action, e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}