package com.secufusion.events.service;

import com.secufusion.events.entity.BrowserPolicy;
import com.secufusion.events.entity.DeviceUser;
import com.secufusion.events.entity.EventsGroup;
import com.secufusion.events.entity.EventsGroupDeviceUserMapping;
import com.secufusion.events.entity.ExtensionPolicy;
import com.secufusion.events.entity.NetworkPolicy;
import com.secufusion.events.entity.PolicyAssignment;
import com.secufusion.events.repository.DeviceUserRepository;
import com.secufusion.events.repository.EventsGroupDeviceUserMappingRepository;
import com.secufusion.events.repository.EventsGroupRepository;
import com.secufusion.events.repository.PolicyAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceUserGroupMappingService Tests")
class DeviceUserGroupMappingServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────
    @Mock private DeviceUserRepository                      deviceUserRepository;
    @Mock private EventsGroupRepository                     eventsGroupRepository;
    @Mock private EventsGroupDeviceUserMappingRepository    mappingRepository;
    @Mock private PolicyAssignmentRepository                policyAssignmentRepository;

    @InjectMocks
    private DeviceUserGroupMappingService service;

    // ── Common test data ─────────────────────────────────────────────────────
    private static final String TENANT_ID      = "tenant-001";
    private static final String USER_ID        = "user-sub-001";
    private static final String EMAIL          = "jane.doe@example.com";
    private static final String DISPLAY_NAME   = "Jane Doe";
    private static final String DEVICE_USER_ID = "du-001";
    private static final String GROUP_ID_A     = "pg-aaa";
    private static final String GROUP_ID_B     = "pg-bbb";
    private static final String AZURE_OID_A    = "oid-aaa";
    private static final String AZURE_OID_B    = "oid-bbb";

    private DeviceUser  mockDeviceUser;
    private EventsGroup groupA;
    private EventsGroup groupB;

    @BeforeEach
    void setUp() {
        mockDeviceUser = DeviceUser.builder()
                .pkDeviceUserId(DEVICE_USER_ID)
                .tenantId(TENANT_ID)
                .email(EMAIL)
                .userName(EMAIL)
                .displayName(DISPLAY_NAME)
                .source("AZURE")
                .status("ACTIVE")
                .firstSeenAt(LocalDateTime.now().minusDays(1))
                .lastSeenAt(LocalDateTime.now().minusHours(1))
                .build();

        groupA = EventsGroup.builder()
                .pkEventsGroupId(GROUP_ID_A)
                .tenantId(TENANT_ID)
                .name("GroupA")
                .groupType(EventsGroup.GroupType.AZURE_GROUP)
                .azureGroupId(AZURE_OID_A)
                .authorized(true)
                .build();

        groupB = EventsGroup.builder()
                .pkEventsGroupId(GROUP_ID_B)
                .tenantId(TENANT_ID)
                .name("GroupB")
                .groupType(EventsGroup.GroupType.AZURE_GROUP)
                .azureGroupId(AZURE_OID_B)
                .authorized(false)
                .build();
    }

    // =========================================================================
    // autoAssignAzureUserToGroups
    // =========================================================================

    @Nested
    @DisplayName("autoAssignAzureUserToGroups")
    class AutoAssignAzureUserToGroups {

        @Test
        @DisplayName("Happy Path — existing user gets lastSeenAt updated and new mappings created")
        void happyPath_existingUser_newMappingsCreated() {
            List<String> azureGroupIds = List.of(AZURE_OID_A, AZURE_OID_B);

            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(List.of(groupA, groupB));
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_A)).thenReturn(false);
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_B)).thenReturn(false);
            when(mappingRepository.save(any(EventsGroupDeviceUserMapping.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, azureGroupIds);

            assertNotNull(result);
            assertEquals(DEVICE_USER_ID, result.getPkDeviceUserId());
            assertEquals(EMAIL, result.getEmail());
            verify(deviceUserRepository, times(1)).save(mockDeviceUser);
            verify(mappingRepository, times(2)).save(any(EventsGroupDeviceUserMapping.class));
            verify(eventsGroupRepository).findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP);
        }

        @Test
        @DisplayName("Happy Path — displayName updated on existing user")
        void happyPath_existingUser_displayNameUpdated() {
            String updatedName = "Jane Updated";

            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(any(), any()))
                    .thenReturn(Collections.emptyList());

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, updatedName, List.of(AZURE_OID_A));

            assertNotNull(result);
            assertEquals(updatedName, mockDeviceUser.getDisplayName());
            verify(deviceUserRepository).save(mockDeviceUser);
        }

        @Test
        @DisplayName("Happy Path — new DeviceUser created when not found by email")
        void happyPath_newUserCreated() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.empty());
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(List.of(groupA));
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_A)).thenReturn(false);
            when(mappingRepository.save(any(EventsGroupDeviceUserMapping.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of(AZURE_OID_A));

            assertNotNull(result);
            verify(deviceUserRepository, times(2)).save(any(DeviceUser.class));

            ArgumentCaptor<DeviceUser> captor = ArgumentCaptor.forClass(DeviceUser.class);
            verify(deviceUserRepository, atLeastOnce()).save(captor.capture());
            DeviceUser created = captor.getAllValues().get(0);
            assertEquals(TENANT_ID,    created.getTenantId());
            assertEquals(EMAIL,        created.getEmail());
            assertEquals(DISPLAY_NAME, created.getDisplayName());
            assertEquals("AZURE",      created.getSource());
            assertEquals("ACTIVE",     created.getStatus());
            assertNotNull(created.getFirstSeenAt());
        }

        @Test
        @DisplayName("Happy Path — null azureGroupIds returns user without group processing")
        void happyPath_nullGroupIds_earlyReturn() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, null);

            assertNotNull(result);
            verifyNoInteractions(eventsGroupRepository);
            verifyNoInteractions(mappingRepository);
        }

        @Test
        @DisplayName("Happy Path — empty azureGroupIds returns user without group processing")
        void happyPath_emptyGroupIds_earlyReturn() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, Collections.emptyList());

            assertNotNull(result);
            verifyNoInteractions(eventsGroupRepository);
            verifyNoInteractions(mappingRepository);
        }

        @Test
        @DisplayName("Happy Path — no Azure groups in tenant skips mapping creation")
        void happyPath_noAzureGroupsInTenant_skipsMappings() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(Collections.emptyList());

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of(AZURE_OID_A));

            assertNotNull(result);
            verifyNoInteractions(mappingRepository);
        }

        @Test
        @DisplayName("Happy Path — JWT groups don't match tenant Azure groups; no mappings created")
        void happyPath_noMatchingGroups_noMappings() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(List.of(groupA));

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of("oid-zzz-no-match"));

            assertNotNull(result);
            verify(mappingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Happy Path — existing mapping is skipped (no duplicate created)")
        void happyPath_existingMapping_notDuplicated() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(List.of(groupA));
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_A)).thenReturn(true);

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of(AZURE_OID_A));

            assertNotNull(result);
            verify(mappingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Happy Path — one existing mapping skipped, one new mapping created")
        void happyPath_mixedMappings_onlyNewOnesCreated() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(List.of(groupA, groupB));
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_A)).thenReturn(true);
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_B)).thenReturn(false);
            when(mappingRepository.save(any(EventsGroupDeviceUserMapping.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of(AZURE_OID_A, AZURE_OID_B));

            assertNotNull(result);
            verify(mappingRepository, times(1)).save(any(EventsGroupDeviceUserMapping.class));
        }

        @Test
        @DisplayName("Happy Path — unauthorized group is still mapped (new behavior)")
        void happyPath_unauthorizedGroup_stillMapped() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(List.of(groupB));
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_B)).thenReturn(false);
            when(mappingRepository.save(any(EventsGroupDeviceUserMapping.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            DeviceUser result = service.autoAssignAzureUserToGroups(
                    TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of(AZURE_OID_B));

            assertNotNull(result);
            verify(mappingRepository, times(1)).save(any(EventsGroupDeviceUserMapping.class));

            ArgumentCaptor<EventsGroupDeviceUserMapping> captor =
                    ArgumentCaptor.forClass(EventsGroupDeviceUserMapping.class);
            verify(mappingRepository).save(captor.capture());
            EventsGroupDeviceUserMapping saved = captor.getValue();
            assertEquals(GROUP_ID_B,     saved.getFkEventsGroupId());
            assertEquals(DEVICE_USER_ID, saved.getFkDeviceUserId());
            assertEquals("SYSTEM_AUTO",  saved.getAssignedBy());
            assertNotNull(saved.getPkMappingId());
            assertNotNull(saved.getAssignedAt());
        }

        @Test
        @DisplayName("Sad Path — deviceUserRepository.save throws; exception propagates")
        void sadPath_saveThrows_exceptionPropagates() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.empty());
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenThrow(new RuntimeException("DB write failed"));

            assertThrows(RuntimeException.class, () ->
                    service.autoAssignAzureUserToGroups(
                            TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of(AZURE_OID_A)));

            verifyNoInteractions(eventsGroupRepository);
            verifyNoInteractions(mappingRepository);
        }

        @Test
        @DisplayName("Sad Path — mappingRepository.save throws on new mapping; exception propagates")
        void sadPath_mappingSaveThrows_exceptionPropagates() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                    .thenReturn(Optional.of(mockDeviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class)))
                    .thenReturn(mockDeviceUser);
            when(eventsGroupRepository.findByTenantIdAndGroupType(
                    TENANT_ID, EventsGroup.GroupType.AZURE_GROUP))
                    .thenReturn(List.of(groupA));
            when(mappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
                    DEVICE_USER_ID, GROUP_ID_A)).thenReturn(false);
            when(mappingRepository.save(any(EventsGroupDeviceUserMapping.class)))
                    .thenThrow(new RuntimeException("Constraint violation"));

            assertThrows(RuntimeException.class, () ->
                    service.autoAssignAzureUserToGroups(
                            TENANT_ID, USER_ID, EMAIL, DISPLAY_NAME, List.of(AZURE_OID_A)));
        }
    }

    // =========================================================================
    // resolvePoliciesForDeviceUser
    // =========================================================================

    @Nested
    @DisplayName("resolvePoliciesForDeviceUser")
    class ResolvePoliciesForDeviceUser {

        // ── Helpers ───────────────────────────────────────────────────────────

        private PolicyAssignment buildAssignment(String id,
                                                 boolean hasBrowser,
                                                 boolean hasNetwork,
                                                 boolean hasExtension) {
            PolicyAssignment pa = mock(PolicyAssignment.class);
            // These two are always used (by log statements in service), so strict stubbing is fine
            lenient().when(pa.getId()).thenReturn(id);
            lenient().when(pa.getAzureResourceName()).thenReturn("GroupResource-" + id);

            // ✅ BrowserPolicy — top-level entity
            if (hasBrowser) {
                BrowserPolicy bp = mock(BrowserPolicy.class);
                lenient().when(bp.getName()).thenReturn("BrowserPolicy-" + id);
                lenient().when(pa.getBrowserPolicy()).thenReturn(bp);
            } else {
                lenient().when(pa.getBrowserPolicy()).thenReturn(null);
            }

            // ✅ NetworkPolicy — top-level entity
            if (hasNetwork) {
                NetworkPolicy np = mock(NetworkPolicy.class);
                lenient().when(np.getName()).thenReturn("NetworkPolicy-" + id);
                lenient().when(pa.getNetworkPolicy()).thenReturn(np);
            } else {
                lenient().when(pa.getNetworkPolicy()).thenReturn(null);
            }

            // ✅ ExtensionPolicy — top-level entity
            if (hasExtension) {
                ExtensionPolicy ep = mock(ExtensionPolicy.class);
                lenient().when(ep.getName()).thenReturn("ExtensionPolicy-" + id);
                lenient().when(pa.getExtensionPolicy()).thenReturn(ep);
            } else {
                lenient().when(pa.getExtensionPolicy()).thenReturn(null);
            }

            return pa;

        }

        private EventsGroupDeviceUserMapping buildMapping(String groupId) {
            EventsGroupDeviceUserMapping m = mock(EventsGroupDeviceUserMapping.class);
            when(m.getFkEventsGroupId()).thenReturn(groupId);
            return m;
        }

        // ── Happy path ────────────────────────────────────────────────────────

        @Test
        @DisplayName("Happy Path — all three policy types resolved (first-wins)")
        void happyPath_allThreePoliciesResolved() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(
                    buildMapping(GROUP_ID_A),
                    buildMapping(GROUP_ID_B)
            );

            PolicyAssignment pa1 = buildAssignment("pa1", true,  true,  false);
            PolicyAssignment pa2 = buildAssignment("pa2", true,  false, true);
            PolicyAssignment pa3 = buildAssignment("pa3", false, false, true);

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(pa1, pa2, pa3));

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertTrue(result.size() >= 1 && result.size() <= 3);
            assertTrue(result.contains(pa1));
            verify(mappingRepository).findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID);
            verify(policyAssignmentRepository)
                    .findByAzureResourceIdInAndTenantId(anyList(), eq(TENANT_ID));
        }

        @Test
        @DisplayName("Happy Path — first-wins: second browser policy is ignored")
        void happyPath_firstWins_secondBrowserPolicyIgnored() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));

            PolicyAssignment pa1 = buildAssignment("pa1", true, false, false);
            PolicyAssignment pa2 = buildAssignment("pa2", true, false, false);

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(pa1, pa2));

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.contains(pa1));
            assertFalse(result.contains(pa2));
        }

        @Test
        @DisplayName("Happy Path — early stop when all three types found")
        void happyPath_earlyStopWhenAllThreeFound() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));

            PolicyAssignment pa1 = buildAssignment("pa1", true, true, true);
            PolicyAssignment pa2 = buildAssignment("pa2", true, true, true);

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(pa1, pa2));

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertTrue(result.contains(pa1));
            assertFalse(result.contains(pa2));
        }

        @Test
        @DisplayName("Happy Path — only browser policy present")
        void happyPath_onlyBrowserPolicy() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));
            PolicyAssignment pa = buildAssignment("pa1", true, false, false);

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(pa));

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("pa1", result.get(0).getId());
        }

        @Test
        @DisplayName("Happy Path — only network policy present")
        void happyPath_onlyNetworkPolicy() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));
            PolicyAssignment pa = buildAssignment("pa1", false, true, false);

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(pa));

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Happy Path — only extension policy present")
        void happyPath_onlyExtensionPolicy() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));
            PolicyAssignment pa = buildAssignment("pa1", false, false, true);

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(pa));

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Happy Path — assignment with all null policies adds nothing")
        void happyPath_allNullPolicies_addsNothing() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));
            PolicyAssignment pa = buildAssignment("pa1", false, false, false);

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(List.of(pa));

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        // ── Sad path ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("Sad Path — no group mappings returns empty list")
        void sadPath_noGroupMappings_returnsEmpty() {
            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(Collections.emptyList());

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verifyNoInteractions(policyAssignmentRepository);
        }

        @Test
        @DisplayName("Sad Path — groups found but no policy assignments; returns empty list")
        void sadPath_noPolicyAssignments_returnsEmpty() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenReturn(Collections.emptyList());

            List<PolicyAssignment> result = service.resolvePoliciesForDeviceUser(
                    TENANT_ID, DEVICE_USER_ID);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Sad Path — mappingRepository throws; exception propagates")
        void sadPath_mappingRepositoryThrows_exceptionPropagates() {
            when(mappingRepository.findActiveGroupsForDeviceUser(anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB down"));

            assertThrows(RuntimeException.class, () ->
                    service.resolvePoliciesForDeviceUser(TENANT_ID, DEVICE_USER_ID));
            verifyNoInteractions(policyAssignmentRepository);
        }

        @Test
        @DisplayName("Sad Path — policyAssignmentRepository throws; exception propagates")
        void sadPath_policyAssignmentRepositoryThrows_exceptionPropagates() {
            List<EventsGroupDeviceUserMapping> mappings = List.of(buildMapping(GROUP_ID_A));

            when(mappingRepository.findActiveGroupsForDeviceUser(DEVICE_USER_ID, TENANT_ID))
                    .thenReturn(mappings);
            when(policyAssignmentRepository.findByAzureResourceIdInAndTenantId(
                    anyList(), eq(TENANT_ID)))
                    .thenThrow(new RuntimeException("Query timeout"));

            assertThrows(RuntimeException.class, () ->
                    service.resolvePoliciesForDeviceUser(TENANT_ID, DEVICE_USER_ID));
        }
    }
}