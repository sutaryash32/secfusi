package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.exception.GlobalException;
import com.secufusion.tenant.mapper.PolicyAssignmentMapper;
import com.secufusion.tenant.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PolicyMappingService Tests")
class PolicyMappingServiceTest {

    @Mock private BrowserPolicyRepository   browserPolicyRepository;
    @Mock private NetworkPolicyRepository   networkPolicyRepository;
    @Mock private PolicyAssignmentRepository assignmentRepository;
    @Mock private ExtensionPolicyRepository  extensionPolicyRepository;
    @Mock private EventsGroupRepository      eventsGroupRepository;
    @Mock private PolicyAssignmentMapper     mapper;

    @InjectMocks
    private PolicyMappingService service;

    private static final String TENANT_ID     = "tenant-001";
    private static final String POLICY_ID     = "policy-001";
    private static final String ASSIGNMENT_ID = "assign-001";
    private static final String GROUP_ID      = "group-001";

    // ── Entity builders ───────────────────────────────────────────────────────

    private BrowserPolicy browserPolicy() {
        BrowserPolicy p = new BrowserPolicy();
        p.setPkBrowserPolicyId(POLICY_ID);
        p.setFkTenantId(TENANT_ID);
        return p;
    }

    private NetworkPolicy networkPolicy() {
        NetworkPolicy p = new NetworkPolicy();
        p.setPkNetworkPolicyId(POLICY_ID);
        p.setFkTenantId(TENANT_ID);
        return p;
    }

    private ExtensionPolicy extensionPolicy() {
        ExtensionPolicy p = new ExtensionPolicy();
        p.setPkExtensionPolicyId(POLICY_ID);
        p.setFkTenantId(TENANT_ID);
        return p;
    }

    private PolicyAssignment policyAssignment() {
        PolicyAssignment a = new PolicyAssignment();
        a.setId(ASSIGNMENT_ID);
        a.setTenantId(TENANT_ID);
        a.setAzureResourceId("az-resource-001");
        a.setAzureResourceName("Resource Name");
        a.setAssignmentType("USER");
        a.setAssignedAt(LocalDateTime.now());
        return a;
    }

    private AzureAssignmentDto azureDto() {
        AzureAssignmentDto dto = new AzureAssignmentDto();
        dto.setId("az-resource-001");
        dto.setName("Resource Name");
        dto.setType("USER");
        return dto;
    }

    private PolicyMappingRequest policyMappingRequest() {
        PolicyMappingRequest req = new PolicyMappingRequest();
        req.setPolicyId(POLICY_ID);
        req.setAssignments(List.of(azureDto()));
        return req;
    }

    private EventsGroup eventsGroup() {
        EventsGroup g = new EventsGroup();
        g.setPkEventsGroupId(GROUP_ID);
        g.setName("Test Group");
        g.setTenantId(TENANT_ID);
        return g;
    }

    // =========================================================================
    // mapPolicyToAzureResources (Browser)
    // =========================================================================
    @Nested
    @DisplayName("mapPolicyToAzureResources")
    class MapPolicyToAzureResourcesTests {

        @Test
        @DisplayName("Happy Path — maps browser policy to azure resources")
        void happyPath() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            doNothing().when(assignmentRepository).deleteByBrowserPolicy_PkBrowserPolicyId(POLICY_ID);
            doNothing().when(assignmentRepository).flush();
            when(assignmentRepository.saveAll(anyList())).thenReturn(List.of(policyAssignment()));

            List<PolicyAssignment> result = service.mapPolicyToAzureResources(TENANT_ID, policyMappingRequest());

            assertEquals(1, result.size());
            verify(assignmentRepository).deleteByBrowserPolicy_PkBrowserPolicyId(POLICY_ID);
        }

        @Test
        @DisplayName("Sad Path — policy not found throws RuntimeException")
        void sadPath_policyNotFound() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class,
                    () -> service.mapPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
        }

        @Test
        @DisplayName("Sad Path — DataIntegrityViolationException rethrows RuntimeException")
        void sadPath_dataIntegrityViolation() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            doThrow(new DataIntegrityViolationException("dup")).when(assignmentRepository)
                    .deleteByBrowserPolicy_PkBrowserPolicyId(anyString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.mapPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
            assertTrue(ex.getMessage().contains("Duplicate entry") ||
                    ex.getMessage().contains("Database error"));
        }

        @Test
        @DisplayName("Sad Path — tenant mismatch throws RuntimeException")
        void sadPath_tenantMismatch() {
            BrowserPolicy p = browserPolicy();
            p.setFkTenantId("other-tenant");
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(p));

            assertThrows(RuntimeException.class,
                    () -> service.mapPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
        }
    }

    // =========================================================================
    // mapNetworkPolicyToAzureResources
    // =========================================================================
    @Nested
    @DisplayName("mapNetworkPolicyToAzureResources")
    class MapNetworkPolicyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(networkPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(networkPolicy()));
            doNothing().when(assignmentRepository).deleteByNetworkPolicy_PkNetworkPolicyId(POLICY_ID);
            doNothing().when(assignmentRepository).flush();
            when(assignmentRepository.saveAll(anyList())).thenReturn(List.of(policyAssignment()));

            List<PolicyAssignment> result = service.mapNetworkPolicyToAzureResources(TENANT_ID, policyMappingRequest());
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Sad Path — policy not found throws RuntimeException")
        void sadPath_policyNotFound() {
            when(networkPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class,
                    () -> service.mapNetworkPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
        }

        @Test
        @DisplayName("Sad Path — CannotAcquireLockException rethrows RuntimeException")
        void sadPath_lockException() {
            when(networkPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(networkPolicy()));
            doThrow(new CannotAcquireLockException("lock")).when(assignmentRepository)
                    .deleteByNetworkPolicy_PkNetworkPolicyId(anyString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.mapNetworkPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
            assertTrue(ex.getMessage().contains("busy") || ex.getMessage().contains("try again"));
        }
    }

    // =========================================================================
    // mapExtensionPolicyToAzureResources
    // =========================================================================
    @Nested
    @DisplayName("mapExtensionPolicyToAzureResources")
    class MapExtensionPolicyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(extensionPolicy()));
            doNothing().when(assignmentRepository).deleteByExtensionPolicy_PkExtensionPolicyId(POLICY_ID);
            doNothing().when(assignmentRepository).flush();
            when(assignmentRepository.saveAll(anyList())).thenReturn(List.of(policyAssignment()));

            List<PolicyAssignment> result = service.mapExtensionPolicyToAzureResources(TENANT_ID, policyMappingRequest());
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Sad Path — policy not found throws RuntimeException")
        void sadPath_policyNotFound() {
            when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class,
                    () -> service.mapExtensionPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
        }

        @Test
        @DisplayName("Sad Path — QueryTimeoutException rethrows RuntimeException")
        void sadPath_dataAccessException() {
            when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(extensionPolicy()));
            doThrow(new QueryTimeoutException("timeout")).when(assignmentRepository)
                    .deleteByExtensionPolicy_PkExtensionPolicyId(anyString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.mapExtensionPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
            assertTrue(ex.getMessage().contains("Database access error") ||
                    ex.getMessage().contains("timeout") ||
                    ex.getCause() != null);
        }
    }

    // =========================================================================
    // addSingleAssignment
    // =========================================================================
    @Nested
    @DisplayName("addSingleAssignment")
    class AddSingleAssignmentTests {

        @Test
        @DisplayName("Happy Path — browser")
        void happyPath_browser() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            when(assignmentRepository.existsByBrowserPolicy_PkBrowserPolicyIdAndAzureResourceId(anyString(), anyString())).thenReturn(false);
            when(assignmentRepository.save(any())).thenReturn(policyAssignment());

            PolicyAssignment result = service.addSingleAssignment(TENANT_ID, "browser", POLICY_ID, azureDto());
            assertNotNull(result);
        }

        @Test
        @DisplayName("Happy Path — network")
        void happyPath_network() {
            when(networkPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(networkPolicy()));
            when(assignmentRepository.existsByNetworkPolicy_PkNetworkPolicyIdAndAzureResourceId(anyString(), anyString())).thenReturn(false);
            when(assignmentRepository.save(any())).thenReturn(policyAssignment());

            assertNotNull(service.addSingleAssignment(TENANT_ID, "network", POLICY_ID, azureDto()));
        }

        @Test
        @DisplayName("Happy Path — extension")
        void happyPath_extension() {
            when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(extensionPolicy()));
            when(assignmentRepository.existsByExtensionPolicy_PkExtensionPolicyIdAndAzureResourceId(anyString(), anyString())).thenReturn(false);
            when(assignmentRepository.save(any())).thenReturn(policyAssignment());

            assertNotNull(service.addSingleAssignment(TENANT_ID, "extension", POLICY_ID, azureDto()));
        }

        @Test
        @DisplayName("Sad Path — duplicate assignment throws RuntimeException")
        void sadPath_duplicateAssignment() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            when(assignmentRepository.existsByBrowserPolicy_PkBrowserPolicyIdAndAzureResourceId(anyString(), anyString())).thenReturn(true);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.addSingleAssignment(TENANT_ID, "browser", POLICY_ID, azureDto()));
            assertTrue(ex.getMessage().contains("already assigned") || ex.getMessage().contains("Duplicate"));
        }

        @Test
        @DisplayName("Sad Path — invalid policyType throws RuntimeException")
        void sadPath_invalidPolicyType() {
            assertThrows(RuntimeException.class,
                    () -> service.addSingleAssignment(TENANT_ID, "INVALID", POLICY_ID, azureDto()));
        }

        @Test
        @DisplayName("Sad Path — policy not found throws RuntimeException")
        void sadPath_policyNotFound() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class,
                    () -> service.addSingleAssignment(TENANT_ID, "browser", POLICY_ID, azureDto()));
        }
    }

    // =========================================================================
    // updateSingleAssignment
    // =========================================================================
    @Nested
    @DisplayName("updateSingleAssignment")
    class UpdateSingleAssignmentTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            PolicyAssignment existing = policyAssignment();
            when(assignmentRepository.findByIdAndTenantId(ASSIGNMENT_ID, TENANT_ID)).thenReturn(Optional.of(existing));
            when(assignmentRepository.save(any())).thenReturn(existing);

            AzureAssignmentDto dto = new AzureAssignmentDto();
            dto.setId("new-az-id");
            dto.setName("New Name");
            dto.setType("GROUP");

            PolicyAssignment result = service.updateSingleAssignment(TENANT_ID, ASSIGNMENT_ID, dto);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Sad Path — not found throws RuntimeException")
        void sadPath_notFound() {
            when(assignmentRepository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class,
                    () -> service.updateSingleAssignment(TENANT_ID, "bad-id", azureDto()));
        }

        @Test
        @DisplayName("Sad Path — DataAccessException throws RuntimeException")
        void sadPath_dataAccessException() {
            when(assignmentRepository.findByIdAndTenantId(ASSIGNMENT_ID, TENANT_ID)).thenReturn(Optional.of(policyAssignment()));
            when(assignmentRepository.save(any())).thenThrow(new QueryTimeoutException("timeout"));

            assertThrows(RuntimeException.class,
                    () -> service.updateSingleAssignment(TENANT_ID, ASSIGNMENT_ID, azureDto()));
        }
    }

    // =========================================================================
    // deleteAssignment
    // =========================================================================
    @Nested
    @DisplayName("deleteAssignment")
    class DeleteAssignmentTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            PolicyAssignment existing = policyAssignment();
            when(assignmentRepository.findByIdAndTenantId(ASSIGNMENT_ID, TENANT_ID)).thenReturn(Optional.of(existing));
            doNothing().when(assignmentRepository).delete(existing);

            assertTrue(service.deleteAssignment(TENANT_ID, ASSIGNMENT_ID));
        }

        @Test
        @DisplayName("Sad Path — not found throws RuntimeException")
        void sadPath_notFound() {
            when(assignmentRepository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class,
                    () -> service.deleteAssignment(TENANT_ID, "bad-id"));
        }

        @Test
        @DisplayName("Sad Path — DataAccessException throws RuntimeException")
        void sadPath_dataAccessException() {
            PolicyAssignment existing = policyAssignment();
            when(assignmentRepository.findByIdAndTenantId(ASSIGNMENT_ID, TENANT_ID)).thenReturn(Optional.of(existing));
            doThrow(new QueryTimeoutException("timeout")).when(assignmentRepository).delete(existing);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.deleteAssignment(TENANT_ID, ASSIGNMENT_ID));
            assertTrue(ex.getMessage().contains("Failed to delete"));
        }
    }

    // =========================================================================
    // getMappingById
    // =========================================================================
    @Nested
    @DisplayName("getMappingById")
    class GetMappingByIdTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            PolicyAssignment existing = policyAssignment();
            when(assignmentRepository.findByIdAndTenantId(ASSIGNMENT_ID, TENANT_ID)).thenReturn(Optional.of(existing));
            PolicyAssignmentResponseDto dto = new PolicyAssignmentResponseDto();
            when(mapper.map(existing)).thenReturn(dto);

            PolicyAssignmentResponseDto result = service.getMappingById(ASSIGNMENT_ID, TENANT_ID);
            assertNotNull(result);
            verify(mapper).map(existing);
        }

        @Test
        @DisplayName("Sad Path — not found throws RuntimeException")
        void sadPath_notFound() {
            when(assignmentRepository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class,
                    () -> service.getMappingById("bad-id", TENANT_ID));
        }

        @Test
        @DisplayName("Sad Path — DataAccessException throws RuntimeException")
        void sadPath_dataAccessException() {
            when(assignmentRepository.findByIdAndTenantId(anyString(), anyString()))
                    .thenThrow(new QueryTimeoutException("timeout"));
            assertThrows(RuntimeException.class,
                    () -> service.getMappingById(ASSIGNMENT_ID, TENANT_ID));
        }
    }

    // =========================================================================
    // getAllMappingsForTenant
    // =========================================================================
    @Nested
    @DisplayName("getAllMappingsForTenant")
    class GetAllMappingsTests {

        @Test
        @DisplayName("Happy Path — returns mapped DTOs")
        void happyPath() {
            PolicyAssignment a = policyAssignment();
            when(assignmentRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(a));
            PolicyAssignmentResponseDto dto = new PolicyAssignmentResponseDto();
            when(mapper.map(a)).thenReturn(dto);

            List<PolicyAssignmentResponseDto> result = service.getAllMappingsForTenant(TENANT_ID);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Happy Path — empty list")
        void happyPath_empty() {
            when(assignmentRepository.findByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
            assertTrue(service.getAllMappingsForTenant(TENANT_ID).isEmpty());
        }
    }

    // =========================================================================
    // assignPoliciesToGroups
    // =========================================================================
    @Nested
    @DisplayName("assignPoliciesToGroups")
    class AssignPoliciesToGroupsTests {

        private GroupPolicyMappingRequest buildGroupRequest(String policyType) {
            GroupInfoDto groupInfo = new GroupInfoDto();
            groupInfo.setPkEventsGroupId(GROUP_ID);

            PolicyGroupAssignmentDto assignment = new PolicyGroupAssignmentDto();
            assignment.setPolicyType(policyType);
            assignment.setPolicyId(POLICY_ID);
            assignment.setEventGroups(List.of(groupInfo));

            GroupPolicyMappingRequest req = new GroupPolicyMappingRequest();
            req.setAssignmentType("GROUP");
            req.setAssignments(List.of(assignment));
            return req;
        }

        @Test
        @DisplayName("Happy Path — BROWSER")
        void happyPath_browser() {
            when(eventsGroupRepository.findAllByPkEventsGroupIdInAndTenantId(anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(eventsGroup()));
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            doNothing().when(assignmentRepository).deleteByAzureResourceIdAndTenantIdAndBrowserPolicyIsNotNull(anyString(), anyString());
            doNothing().when(assignmentRepository).flush();
            when(assignmentRepository.saveAll(anyList())).thenReturn(List.of(policyAssignment()));

            GroupPolicyMappingResponse result = service.assignPoliciesToGroups(TENANT_ID, buildGroupRequest("BROWSER"));
            assertEquals(1, result.getTotalAssignmentsCreated());
        }

        @Test
        @DisplayName("Happy Path — NETWORK")
        void happyPath_network() {
            when(eventsGroupRepository.findAllByPkEventsGroupIdInAndTenantId(anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(eventsGroup()));
            when(networkPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(networkPolicy()));
            doNothing().when(assignmentRepository).deleteByAzureResourceIdAndTenantIdAndNetworkPolicyIsNotNull(anyString(), anyString());
            doNothing().when(assignmentRepository).flush();
            when(assignmentRepository.saveAll(anyList())).thenReturn(List.of(policyAssignment()));

            GroupPolicyMappingResponse result = service.assignPoliciesToGroups(TENANT_ID, buildGroupRequest("NETWORK"));
            assertEquals(1, result.getTotalAssignmentsCreated());
        }

        @Test
        @DisplayName("Happy Path — EXTENSION")
        void happyPath_extension() {
            when(eventsGroupRepository.findAllByPkEventsGroupIdInAndTenantId(anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(eventsGroup()));
            when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(extensionPolicy()));
            doNothing().when(assignmentRepository).deleteByAzureResourceIdAndTenantIdAndExtensionPolicyIsNotNull(anyString(), anyString());
            doNothing().when(assignmentRepository).flush();
            when(assignmentRepository.saveAll(anyList())).thenReturn(List.of(policyAssignment()));

            GroupPolicyMappingResponse result = service.assignPoliciesToGroups(TENANT_ID, buildGroupRequest("EXTENSION"));
            assertEquals(1, result.getTotalAssignmentsCreated());
        }

        @Test
        @DisplayName("Sad Path — wrong assignmentType throws GlobalException")
        void sadPath_wrongAssignmentType() {
            GroupPolicyMappingRequest req = buildGroupRequest("BROWSER");
            req.setAssignmentType("USER");
            assertThrows(GlobalException.class,
                    () -> service.assignPoliciesToGroups(TENANT_ID, req));
        }

        @Test
        @DisplayName("Sad Path — empty assignments throws GlobalException")
        void sadPath_emptyAssignments() {
            GroupPolicyMappingRequest req = new GroupPolicyMappingRequest();
            req.setAssignmentType("GROUP");
            req.setAssignments(Collections.emptyList());
            assertThrows(GlobalException.class,
                    () -> service.assignPoliciesToGroups(TENANT_ID, req));
        }

        @Test
        @DisplayName("Sad Path — null assignments throws GlobalException")
        void sadPath_nullAssignments() {
            GroupPolicyMappingRequest req = new GroupPolicyMappingRequest();
            req.setAssignmentType("GROUP");
            req.setAssignments(null);
            assertThrows(GlobalException.class,
                    () -> service.assignPoliciesToGroups(TENANT_ID, req));
        }

        @Test
        @DisplayName("Sad Path — invalid groupId throws IllegalArgumentException")
        void sadPath_invalidGroupId() {
            when(eventsGroupRepository.findAllByPkEventsGroupIdInAndTenantId(anyList(), eq(TENANT_ID)))
                    .thenReturn(Collections.emptyList());
            assertThrows(IllegalArgumentException.class,
                    () -> service.assignPoliciesToGroups(TENANT_ID, buildGroupRequest("BROWSER")));
        }

        @Test
        @DisplayName("Sad Path — invalid policyType throws IllegalArgumentException")
        void sadPath_invalidPolicyType() {
            when(eventsGroupRepository.findAllByPkEventsGroupIdInAndTenantId(anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(eventsGroup()));
            doNothing().when(assignmentRepository).flush();

            assertThrows(IllegalArgumentException.class,
                    () -> service.assignPoliciesToGroups(TENANT_ID, buildGroupRequest("INVALID")));
        }

        @Test
        @DisplayName("Sad Path — null policyType throws IllegalArgumentException")
        void sadPath_nullPolicyType() {
            when(eventsGroupRepository.findAllByPkEventsGroupIdInAndTenantId(anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(eventsGroup()));
            doNothing().when(assignmentRepository).flush();

            GroupPolicyMappingRequest req = buildGroupRequest("BROWSER");
            req.getAssignments().get(0).setPolicyType(null);

            assertThrows(IllegalArgumentException.class,
                    () -> service.assignPoliciesToGroups(TENANT_ID, req));
        }
    }

    // =========================================================================
    // handleDbExceptions — all branches via mapPolicyToAzureResources
    // =========================================================================
    @Nested
    @DisplayName("handleDbExceptions — all branches")
    class HandleDbExceptionsTests {

        @Test
        @DisplayName("DeadlockLoserDataAccessException → System busy RuntimeException")
        void deadlockException() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            doThrow(new DeadlockLoserDataAccessException("deadlock", null))
                    .when(assignmentRepository).deleteByBrowserPolicy_PkBrowserPolicyId(anyString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.mapPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
            assertTrue(ex.getMessage().contains("busy") || ex.getMessage().contains("try again"));
        }

        @Test
        @DisplayName("Generic DataAccessException → Database access error RuntimeException")
        void genericDataAccessException() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            doThrow(new QueryTimeoutException("timeout"))
                    .when(assignmentRepository).deleteByBrowserPolicy_PkBrowserPolicyId(anyString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.mapPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
            assertTrue(ex.getMessage().contains("Database access error") ||
                    ex.getCause() instanceof QueryTimeoutException);
        }

        @Test
        @DisplayName("Unexpected non-DataAccess exception → wraps with original message")
        void unexpectedException() {
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy()));
            doThrow(new IllegalStateException("unexpected"))
                    .when(assignmentRepository).deleteByBrowserPolicy_PkBrowserPolicyId(anyString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.mapPolicyToAzureResources(TENANT_ID, policyMappingRequest()));
            assertNotNull(ex.getMessage());
        }
    }
}