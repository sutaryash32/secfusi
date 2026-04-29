package com.secufusion.tenant.service;

import com.fasterxml.jackson.databind.node.TextNode;
import com.secufusion.tenant.dto.BrowserPolicyWithAssignmentsDto;
import com.secufusion.tenant.dto.PolicyVersionResponse;
import com.secufusion.tenant.dto.UserEffectivePolicyResponse;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.BrowserPolicyRepository;
import com.secufusion.tenant.repository.EventsGroupRepository;
import com.secufusion.tenant.repository.LandingPageRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import com.secufusion.tenant.util.JwtUtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.hibernate.envers.query.AuditQueryCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrowserPolicyServiceTest {

    @Mock private BrowserPolicyRepository browserPolicyRepository;
    @Mock private EntityManager entityManager;
    @Mock private PolicyAssignmentRepository policyAssignmentRepository;
    @Mock private JwtUtl jwtUtl;
    @Mock private EventsGroupRepository eventsGroupRepository;
    @Mock private LandingPageRepository landingPageRepository;
    @Mock private NetworkPolicyService networkPolicyService;
    @Mock private ExtensionPolicyService extensionPolicyService;
    @Mock private HttpServletRequest request;

    @InjectMocks
    private BrowserPolicyService browserPolicyService;

    private BrowserPolicy browserPolicy;
    private final String TENANT_ID = "tenant-123";
    private final String POLICY_ID = "policy-123";

    @BeforeEach
    void setUp() {
        browserPolicy = new BrowserPolicy();
        browserPolicy.setPkBrowserPolicyId(POLICY_ID);
        browserPolicy.setName("Test Policy");
        browserPolicy.setVersion("0.1");
        browserPolicy.setActive(true);
        browserPolicy.setFkTenantId(TENANT_ID);

        Dlp dlp = new Dlp();
        dlp.setWatermarking(new Watermarking());
        browserPolicy.setDlp(dlp);

        browserPolicy.setComplianceRules(new ComplianceRules());
        browserPolicy.setHomepage(new Homepage());
    }

    @Nested
    @DisplayName("createdDefaultPolicyIfNotExists")
    class CreatedDefaultPolicyIfNotExists {

        @Test
        void shouldReturnExistingGlobalDefault_whenFound() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.createdDefaultPolicyIfNotExists();

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
            verify(browserPolicyRepository, never()).save(any());
        }

        @Test
        void shouldReturnSameNameGlobalDefault_whenFound() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.empty());
            when(browserPolicyRepository.findTopByFkTenantIdIsNullAndNameIgnoreCase("Default Browser Policy"))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.createdDefaultPolicyIfNotExists();

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
            verify(browserPolicyRepository, never()).save(any());
        }

        @Test
        void shouldCreateAndReturnNewGlobalDefault_whenNotFound() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.empty());
            when(browserPolicyRepository.findTopByFkTenantIdIsNullAndNameIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());
            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenAnswer(invocation -> {
                BrowserPolicy saved = invocation.getArgument(0);
                saved.setPkBrowserPolicyId("new-id");
                return saved;
            });

            // ACT
            BrowserPolicy result = browserPolicyService.createdDefaultPolicyIfNotExists();

            // ASSERT
            assertNotNull(result);
            assertEquals("new-id", result.getPkBrowserPolicyId());
            assertEquals("Default Browser Policy", result.getName());
            assertTrue(result.isActive());
        }

        @Test
        void shouldFallbackToFetch_whenDataIntegrityViolationExceptionThrown() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.empty(), Optional.of(browserPolicy));
            when(browserPolicyRepository.findTopByFkTenantIdIsNullAndNameIgnoreCase(anyString())).thenReturn(Optional.empty());
            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenThrow(new DataIntegrityViolationException("Conflict"));

            // ACT
            BrowserPolicy result = browserPolicyService.createdDefaultPolicyIfNotExists();

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
        }

        @Test
        void shouldThrowException_whenFallbackFailsAfterDataIntegrityViolationException() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.empty());
            when(browserPolicyRepository.findTopByFkTenantIdIsNullAndNameIgnoreCase(anyString())).thenReturn(Optional.empty());
            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenThrow(new DataIntegrityViolationException("Conflict"));

            // ACT & ASSERT
            assertThrows(IllegalStateException.class, () -> browserPolicyService.createdDefaultPolicyIfNotExists());
        }
    }

    @Nested
    @DisplayName("createPolicy")
    class CreatePolicy {

        @Test
        void shouldCreatePolicy() {
            // ARRANGE
            BrowserPolicy requestPolicy = new BrowserPolicy();
            requestPolicy.setName("New Policy");

            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenAnswer(i -> {
                BrowserPolicy p = i.getArgument(0);
                p.setPkBrowserPolicyId("new-pk");
                return p;
            });

            // ACT
            BrowserPolicy result = browserPolicyService.createPolicy(requestPolicy);

            // ASSERT
            assertNotNull(result);
            assertEquals("new-pk", result.getPkBrowserPolicyId());
            assertEquals("0.1", result.getVersion());
            assertTrue(result.isActive());
            assertNotNull(result.getPolicyKey());
        }
    }

    @Nested
    @DisplayName("getPolicyById")
    class GetPolicyById {

        @Test
        void shouldReturnPolicy_whenFound() {
            // ARRANGE
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.getPolicyById(POLICY_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
        }

        @Test
        void shouldThrowIllegalArgumentException_whenNotFound() {
            // ARRANGE
            when(browserPolicyRepository.findById("invalid")).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(IllegalArgumentException.class, () -> browserPolicyService.getPolicyById("invalid"));
        }
    }

    @Nested
    @DisplayName("getAllPolicies")
    class GetAllPolicies {

        @Test
        void shouldReturnAllPoliciesWithDefaultAtTop_whenDefaultNotAlreadyInList() {
            // ARRANGE
            BrowserPolicy defaultPolicy = new BrowserPolicy();
            defaultPolicy.setPkBrowserPolicyId("default-id");
            defaultPolicy.setTenantDefault(true);

            when(browserPolicyRepository.findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TENANT_ID))
                    .thenReturn(new ArrayList<>(List.of(browserPolicy)));
            // Mocking getDefaultPolicy flow
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(defaultPolicy));

            // ACT
            List<BrowserPolicy> result = browserPolicyService.getAllPolicies(TENANT_ID);

            // ASSERT
            assertEquals(2, result.size());
            assertEquals("default-id", result.get(0).getPkBrowserPolicyId());
            assertEquals(POLICY_ID, result.get(1).getPkBrowserPolicyId());
        }

        @Test
        void shouldReturnPolicies_whenDefaultAlreadyInList() {
            // ARRANGE
            browserPolicy.setTenantDefault(true); // Make the existing one the default
            when(browserPolicyRepository.findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TENANT_ID))
                    .thenReturn(new ArrayList<>(List.of(browserPolicy)));
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            List<BrowserPolicy> result = browserPolicyService.getAllPolicies(TENANT_ID);

            // ASSERT
            assertEquals(1, result.size());
            assertEquals(POLICY_ID, result.get(0).getPkBrowserPolicyId());
        }
    }

    @Nested
    @DisplayName("updatePolicy")
    class UpdatePolicy {

        @Test
        void shouldThrowIllegalArgumentException_whenUpdatingGlobalDefault() {
            // ARRANGE
            browserPolicy.setFkTenantId(null); // Makes it global
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));

            // ACT & ASSERT
            assertThrows(IllegalArgumentException.class, () ->
                    browserPolicyService.updatePolicy(POLICY_ID, new BrowserPolicy(), request));
        }

        @Test
        void shouldUpdatePolicyVersionAndDeactivateOld() {
            // ARRANGE
            browserPolicy.setVersion("0.1");
            browserPolicy.setPolicyKey("key-123");

            BrowserPolicy updatedInfo = new BrowserPolicy();
            updatedInfo.setName("Updated Name");
            updatedInfo.setLandingPageUrl("http://new.com");

            ManagedExtension me = new ManagedExtension();
            me.setAction(ExtensionAction.ALLOW_LIST);
            me.setExtensions(List.of(new ExtensionDetail(null, "ext-1", "Name", "Pub")));
            updatedInfo.setManagedExtension(me);

            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));
            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenAnswer(i -> i.getArgument(0));

            // ACT
            BrowserPolicy result = browserPolicyService.updatePolicy(POLICY_ID, updatedInfo, request);

            // ASSERT
            assertFalse(browserPolicy.isActive()); // Old deactivated
            verify(browserPolicyRepository).saveAndFlush(browserPolicy);

            assertNotNull(result);
            assertTrue(result.isActive());
            assertEquals("0.2", result.getVersion());
            assertEquals("key-123", result.getPolicyKey());
            assertEquals("Updated Name", result.getName());
            assertEquals("http://new.com", result.getLandingPageUrl());
            assertNotNull(result.getManagedExtension());
            assertEquals(ExtensionAction.ALLOW_LIST, result.getManagedExtension().getAction());

            verify(policyAssignmentRepository).updateBrowserPolicyReference(POLICY_ID, result.getPkBrowserPolicyId());
        }

        @Test
        void shouldIncrementMajorVersion_whenMinorIsNine() {
            // ARRANGE
            browserPolicy.setVersion("1.9");
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));
            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenAnswer(i -> i.getArgument(0));

            // ACT
            BrowserPolicy result = browserPolicyService.updatePolicy(POLICY_ID, new BrowserPolicy(), request);

            // ASSERT
            assertEquals("2.0", result.getVersion());
        }

        @Test
        void shouldResetVersion_whenInvalidVersionString() {
            // ARRANGE
            browserPolicy.setVersion("invalid-version");
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));
            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenAnswer(i -> i.getArgument(0));

            // ACT
            BrowserPolicy result = browserPolicyService.updatePolicy(POLICY_ID, new BrowserPolicy(), request);

            // ASSERT
            assertEquals("0.1", result.getVersion()); // Resets to 0.1
        }
    }

    @Nested
    @DisplayName("deletePolicy")
    class DeletePolicy {

        @Test
        void shouldThrowException_whenDeletingGlobalDefault() {
            // ARRANGE
            browserPolicy.setFkTenantId(null);
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));

            // ACT & ASSERT
            assertThrows(IllegalArgumentException.class, () -> browserPolicyService.deletePolicy(POLICY_ID));
        }

        @Test
        void shouldThrowException_whenDeletingTenantDefault() {
            // ARRANGE
            browserPolicy.setTenantDefault(true);
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));

            // ACT & ASSERT
            assertThrows(IllegalArgumentException.class, () -> browserPolicyService.deletePolicy(POLICY_ID));
        }

        @Test
        void shouldDeletePolicy() {
            // ARRANGE
            browserPolicy.setTenantDefault(false);
            browserPolicy.setFkTenantId(TENANT_ID);
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));

            // ACT
            browserPolicyService.deletePolicy(POLICY_ID);

            // ASSERT
            verify(browserPolicyRepository).delete(browserPolicy);
        }
    }

    @Nested
    @DisplayName("Envers Audit Methods")
    class EnversAuditMethods {

        @Test
        void shouldGetPolicyRevisions() {
            try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
                // ARRANGE
                AuditReader reader = mock(AuditReader.class);
                AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
                AuditQuery query = mock(AuditQuery.class);

                factory.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(reader);
                when(reader.createQuery()).thenReturn(queryCreator);
                when(queryCreator.forRevisionsOfEntity(BrowserPolicy.class, false, true)).thenReturn(query);
                when(query.add(any())).thenReturn(query);

                List<Object[]> rows = new ArrayList<>();
                rows.add(new Object[]{browserPolicy, null, null});
                when(query.getResultList()).thenReturn(rows);

                // ACT
                List<Object[]> result = browserPolicyService.getPolicyRevisions(POLICY_ID);

                // ASSERT
                assertNotNull(result);
                assertFalse(result.isEmpty());
                assertEquals(1, result.size());
            }
        }

        @Test
        void shouldGetPolicyAtRevision() {
            try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
                // ARRANGE
                AuditReader reader = mock(AuditReader.class);
                factory.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(reader);
                when(reader.find(BrowserPolicy.class, POLICY_ID, 1)).thenReturn(browserPolicy);

                // ACT
                BrowserPolicy result = browserPolicyService.getPolicyAtRevision(POLICY_ID, 1);

                // ASSERT
                assertNotNull(result);
                assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
            }
        }
    }

    @Nested
    @DisplayName("getEffectivePolicy")
    class GetEffectivePolicy {

        @Test
        void shouldReturnMatchFromAssignments() {
            // ARRANGE
            PolicyAssignment assignment = new PolicyAssignment();
            assignment.setId("assign-1");
            assignment.setAssignmentType("ROLE");
            assignment.setBrowserPolicy(browserPolicy);

            when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                    .thenReturn(List.of(assignment));

            // ACT
            BrowserPolicy result = browserPolicyService.getEffectivePolicy(TENANT_ID, List.of("Admin"), List.of());

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
        }

        @Test
        void shouldReturnDefaultPolicy_whenNoAssignmentsMatch() {
            // ARRANGE
            when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.getEffectivePolicy(TENANT_ID, List.of("User"), List.of());

            // ASSERT
            assertNotNull(result);
        }

        @Test
        void shouldReturnDefaultPolicy_whenRolesAndGroupsAreEmpty() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.getEffectivePolicy(TENANT_ID, null, null);

            // ASSERT
            assertNotNull(result);
            verifyNoInteractions(policyAssignmentRepository);
        }

        @Test
        void shouldReturnDefaultPolicy_whenExceptionOccurs() {
            // ARRANGE
            when(policyAssignmentRepository.findEffectiveAssignments(any(), any(), any()))
                    .thenThrow(new RuntimeException("DB Error"));
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.getEffectivePolicy(TENANT_ID, List.of("Role"), List.of("Group"));

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
        }
    }

    @Nested
    @DisplayName("resolvePolicyForCurrentUser")
    class ResolvePolicyForCurrentUser {

        @Test
        void shouldThrowSecurityException_whenTenantMissing() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(null);

            // ACT & ASSERT
            assertThrows(SecurityException.class, () -> browserPolicyService.resolvePolicyForCurrentUser(request));
        }

        @Test
        void shouldResolvePolicyWithAzureGroupsAndRoles() {
            // ARRANGE
            Tenant tenant = new Tenant();
            tenant.setTenantID(TENANT_ID);
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

            when(jwtUtl.getClaimAsStringList(request, "roles")).thenReturn(List.of("Admin"));
            when(jwtUtl.getClaimAsStringList(request, "groups")).thenReturn(List.of("azure-oid"));

            EventsGroup group = new EventsGroup();
            group.setPkEventsGroupId("group-123");
            when(eventsGroupRepository.findByTenantIdAndIdentifiers(TENANT_ID, List.of("azure-oid")))
                    .thenReturn(List.of(group));

            when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.resolvePolicyForCurrentUser(request);

            // ASSERT
            assertNotNull(result);
            verify(eventsGroupRepository).findByTenantIdAndIdentifiers(TENANT_ID, List.of("azure-oid"));
        }

        @Test
        void shouldFallbackToDefaultGroup_whenMachineToken() {
            // ARRANGE
            Tenant tenant = new Tenant();
            tenant.setTenantID(TENANT_ID);
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

            when(jwtUtl.getClaimAsStringList(request, "roles")).thenReturn(Collections.emptyList());
            when(jwtUtl.getClaimAsStringList(request, "groups")).thenReturn(Collections.emptyList());

            EventsGroup defaultGroup = new EventsGroup();
            defaultGroup.setPkEventsGroupId("def-group-123");
            when(eventsGroupRepository.findByTenantIdAndIsDefault(TENANT_ID, true))
                    .thenReturn(Optional.of(defaultGroup));

            when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.resolvePolicyForCurrentUser(request);

            // ASSERT
            assertNotNull(result);
            verify(eventsGroupRepository).findByTenantIdAndIsDefault(TENANT_ID, true);
        }
    }

    @Nested
    @DisplayName("resolveAllPoliciesForCurrentUser")
    class ResolveAllPoliciesForCurrentUser {

        @Test
        void shouldResolveAllPoliciesIncludingLandingPage() {
            // ARRANGE
            Tenant tenant = new Tenant();
            tenant.setTenantID(TENANT_ID);
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);
            when(jwtUtl.getClaimAsStringList(request, "roles")).thenReturn(Collections.emptyList());
            when(jwtUtl.getClaimAsStringList(request, "groups")).thenReturn(Collections.emptyList());

            // For resolvePolicyForCurrentUser inner call
            when(eventsGroupRepository.findByTenantIdAndIsDefault(TENANT_ID, true)).thenReturn(Optional.empty());

            browserPolicy.setLandingPageId("lp-123");
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            NetworkPolicy np = new NetworkPolicy();
            np.setPkNetworkPolicyId("np-123");
            when(networkPolicyService.getEffectivePolicy(eq(TENANT_ID), anyList(), anyList())).thenReturn(np);

            ExtensionPolicy ep = new ExtensionPolicy();
            ep.setPkExtensionPolicyId("ep-123");
            when(extensionPolicyService.getEffectivePolicy(eq(TENANT_ID), anyList(), anyList())).thenReturn(ep);

            LandingPage lp = new LandingPage();
            lp.setPkLandingPageId("lp-123");
            when(landingPageRepository.findByIdWithShortcuts("lp-123")).thenReturn(Optional.of(lp));

            // ACT
            UserEffectivePolicyResponse result = browserPolicyService.resolveAllPoliciesForCurrentUser(request);

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getBrowserPolicy().getPkBrowserPolicyId());
            assertEquals("np-123", result.getNetworkPolicy().getPkNetworkPolicyId());
            assertEquals("ep-123", result.getExtensionPolicy().getPkExtensionPolicyId());
            assertNotNull(result.getLandingPage());
            assertEquals("lp-123", result.getLandingPage().getPkLandingPageId());
        }

        @Test
        void shouldHandleExceptionWhenLandingPageFails() {
            // ARRANGE
            Tenant tenant = new Tenant();
            tenant.setTenantID(TENANT_ID);
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

            browserPolicy.setLandingPageId("lp-123");
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            when(networkPolicyService.getEffectivePolicy(any(), any(), any())).thenReturn(new NetworkPolicy());
            when(extensionPolicyService.getEffectivePolicy(any(), any(), any())).thenReturn(new ExtensionPolicy());

            when(landingPageRepository.findByIdWithShortcuts("lp-123")).thenThrow(new RuntimeException("DB Error"));

            // ACT
            UserEffectivePolicyResponse result = browserPolicyService.resolveAllPoliciesForCurrentUser(request);

            // ASSERT
            assertNotNull(result);
            assertNull(result.getLandingPage()); // Exception caught and null returned
        }
    }

    @Nested
    @DisplayName("getPolicyVersionsForCurrentUser")
    class GetPolicyVersionsForCurrentUser {

        @Test
        void shouldReturnLightweightVersionResponse() {
            // ARRANGE
            Tenant tenant = new Tenant();
            tenant.setTenantID(TENANT_ID);
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            NetworkPolicy np = new NetworkPolicy();
            np.setPkNetworkPolicyId("np-123");
            np.setVersion("1.0");
            when(networkPolicyService.getEffectivePolicy(any(), any(), any())).thenReturn(np);

            ExtensionPolicy ep = new ExtensionPolicy();
            ep.setPkExtensionPolicyId("ep-123");
            ep.setVersion("2.0");
            when(extensionPolicyService.getEffectivePolicy(any(), any(), any())).thenReturn(ep);

            // ACT
            PolicyVersionResponse result = browserPolicyService.getPolicyVersionsForCurrentUser(request);

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getBrowserPolicy().getPolicyId());
            assertEquals("0.1", result.getBrowserPolicy().getVersion());
            assertEquals("np-123", result.getNetworkPolicy().getPolicyId());
            assertEquals("1.0", result.getNetworkPolicy().getVersion());
            assertEquals("ep-123", result.getExtensionPolicy().getPolicyId());
            assertEquals("2.0", result.getExtensionPolicy().getVersion());
        }

        @Test
        void shouldThrowExceptionIfTenantMissing() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(null);

            // ACT & ASSERT
            assertThrows(SecurityException.class, () -> browserPolicyService.getPolicyVersionsForCurrentUser(request));
        }
    }

    @Nested
    @DisplayName("createTenantDefaultPolicy")
    class CreateTenantDefaultPolicy {

        @Test
        void shouldReturnExistingTenantDefault_whenFound() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            // ACT
            BrowserPolicy result = browserPolicyService.createTenantDefaultPolicy(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
            verify(browserPolicyRepository, never()).save(any());
        }

        @Test
        void shouldCloneGlobalDefaultAndSave() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            // Mock global default fetch
            when(browserPolicyRepository.findTopByFkTenantIdIsNull())
                    .thenReturn(Optional.of(browserPolicy)); // Global default

            when(browserPolicyRepository.save(any())).thenAnswer(i -> {
                BrowserPolicy p = i.getArgument(0);
                p.setPkBrowserPolicyId("new-tenant-def");
                return p;
            });

            // ACT
            BrowserPolicy result = browserPolicyService.createTenantDefaultPolicy(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals("new-tenant-def", result.getPkBrowserPolicyId());
            assertEquals(TENANT_ID, result.getFkTenantId());
            assertTrue(result.isTenantDefault());
        }

        @Test
        void shouldFallbackToFetch_whenDataIntegrityViolationExceptionThrown() {
            // ARRANGE
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty(), Optional.of(browserPolicy));

            when(browserPolicyRepository.findTopByFkTenantIdIsNull())
                    .thenReturn(Optional.of(browserPolicy));

            when(browserPolicyRepository.save(any())).thenThrow(new DataIntegrityViolationException("Conflict"));

            // ACT
            BrowserPolicy result = browserPolicyService.createTenantDefaultPolicy(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
        }
    }

    @Nested
    @DisplayName("Assignment Status Methods")
    class AssignmentStatusMethods {

        @Test
        void shouldGetPolicyByIdWithAssignments() {
            // ARRANGE
            when(browserPolicyRepository.findById(POLICY_ID)).thenReturn(Optional.of(browserPolicy));

            PolicyAssignment assignment = new PolicyAssignment();
            assignment.setAssignmentType("ROLE");
            when(policyAssignmentRepository.findByBrowserPolicy_PkBrowserPolicyId(POLICY_ID))
                    .thenReturn(List.of(assignment));

            // ACT
            BrowserPolicyWithAssignmentsDto result = browserPolicyService.getPolicyByIdWithAssignments(POLICY_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
            assertEquals(1, result.getTotalAssignments());
            assertEquals(1, result.getRoleAssignments());
            assertTrue(result.isAssigned());
        }

        @Test
        void shouldGetAllPoliciesWithAssignments() {
            // ARRANGE
            when(browserPolicyRepository.findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TENANT_ID))
                    .thenReturn(new ArrayList<>(List.of(browserPolicy)));
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            PolicyAssignment assignment = new PolicyAssignment();
            assignment.setAssignmentType("GROUP");
            assignment.setBrowserPolicy(browserPolicy);
            when(policyAssignmentRepository.findByTenantId(TENANT_ID))
                    .thenReturn(List.of(assignment));

            // ACT
            List<BrowserPolicyWithAssignmentsDto> result = browserPolicyService.getAllPoliciesWithAssignments(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getTotalAssignments());
            assertEquals(1, result.get(0).getGroupAssignments());
        }

        @Test
        void shouldResolveEffectivePolicyWithAssignments() {
            // ARRANGE
            Tenant tenant = new Tenant();
            tenant.setTenantID(TENANT_ID);
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(browserPolicy));

            PolicyAssignment assignment = new PolicyAssignment();
            assignment.setAssignmentType("ROLE");
            when(policyAssignmentRepository.findByBrowserPolicy_PkBrowserPolicyId(POLICY_ID))
                    .thenReturn(List.of(assignment));

            // ACT
            BrowserPolicyWithAssignmentsDto result = browserPolicyService.resolveEffectivePolicyWithAssignments(request);

            // ASSERT
            assertNotNull(result);
            assertEquals(POLICY_ID, result.getPkBrowserPolicyId());
            assertEquals(1, result.getTotalAssignments());
        }

        @Test
        void shouldReturnNewlyCreatedGlobalDefault_whenNoPoliciesExist() {
            // ARRANGE
            Tenant tenant = new Tenant();
            tenant.setTenantID(TENANT_ID);
            when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

            // No tenant default, no global default, no name‑specific default
            when(browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());
            when(browserPolicyRepository.findTopByFkTenantIdIsNull())
                    .thenReturn(Optional.empty());
            when(browserPolicyRepository.findTopByFkTenantIdIsNullAndNameIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            // The service will create a fresh global default
            BrowserPolicy newGlobal = new BrowserPolicy();
            newGlobal.setName("Global Default");
            // save() returns the created policy
            when(browserPolicyRepository.save(any(BrowserPolicy.class))).thenReturn(newGlobal);

            // ACT
            BrowserPolicyWithAssignmentsDto result = browserPolicyService.resolveEffectivePolicyWithAssignments(request);

            // ASSERT
            assertNotNull(result);
            assertEquals("Global Default", result.getName());
            // Newly created policy has no ID yet
            assertNull(result.getPkBrowserPolicyId());
        }
    }
}

