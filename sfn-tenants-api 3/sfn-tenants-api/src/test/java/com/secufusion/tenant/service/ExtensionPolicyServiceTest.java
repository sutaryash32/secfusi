package com.secufusion.tenant.service;

import com.secufusion.tenant.config.ExtensionPolicyDefaults;
import com.secufusion.tenant.dto.ExtensionDetailDto;
import com.secufusion.tenant.dto.ManagedExtensionDto;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.EventsGroupRepository;
import com.secufusion.tenant.repository.ExtensionPolicyRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import com.secufusion.tenant.repository.UrlFilterRepository;
import com.secufusion.tenant.util.JwtUtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtensionPolicyServiceTest {

    @Mock private ExtensionPolicyRepository extensionPolicyRepository;
    @Mock private ExtensionPolicyDefaults extensionPolicyDefaults;
    @Mock private JwtUtl jwtUtl;
    @Mock private EntityManager entityManager;
    @Mock private PolicyAssignmentRepository policyAssignmentRepository;
    @Mock private EventsGroupRepository eventsGroupRepository;
    @Mock private UrlFilterRepository urlFilterRepository;

    @InjectMocks
    private ExtensionPolicyService extensionPolicyService;

    private static final String TENANT_ID = "tenant-123";
    private static final String POLICY_ID = "policy-uuid";
    private static final String DEFAULT_ENFORCEMENT = "WARN_USER";
    private static final String DEFAULT_MESSAGE = "Default warning message";
    private static final String GLOBAL_DEFAULT_ID = "global-default-id";
    private static final String TENANT_DEFAULT_ID = "tenant-default-id";

    private ExtensionPolicy globalDefault;
    private ExtensionPolicy tenantDefault;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        // Arrange common stubs for extensionPolicyDefaults
        lenient().when(extensionPolicyDefaults.getEnforcementAction()).thenReturn(DEFAULT_ENFORCEMENT);
        lenient().when(extensionPolicyDefaults.getWarningMessage()).thenReturn(DEFAULT_MESSAGE);

        // Common test entities
        globalDefault = new ExtensionPolicy();
        globalDefault.setPkExtensionPolicyId(GLOBAL_DEFAULT_ID);
        globalDefault.setName("Default Extension Policy");
        globalDefault.setDescription("Applied when no extension policy is assigned");
        globalDefault.setPolicyKey(UUID.randomUUID().toString());
        globalDefault.setVersion("0.1");
        globalDefault.setIsActive(true);
        globalDefault.setFkTenantId(null);
        ManagedExtension globalMe = new ManagedExtension();
        globalMe.setAction(ExtensionAction.ALLOW_ALL);
        globalMe.setEnforcementAction(DEFAULT_ENFORCEMENT);
        globalMe.setWarningMessage(DEFAULT_MESSAGE);
        globalDefault.setManagedExtension(globalMe);

        tenantDefault = new ExtensionPolicy();
        tenantDefault.setPkExtensionPolicyId(TENANT_DEFAULT_ID);
        tenantDefault.setFkTenantId(TENANT_ID);
        tenantDefault.setTenantDefault(true);
        tenantDefault.setName("Tenant Default");
        tenantDefault.setDescription("Tenant default policy");
        tenantDefault.setPolicyKey(UUID.randomUUID().toString());
        tenantDefault.setVersion("0.1");
        tenantDefault.setIsActive(true);
        ManagedExtension tdMe = new ManagedExtension();
        tdMe.setAction(ExtensionAction.ALLOW_ALL);
        tdMe.setEnforcementAction(DEFAULT_ENFORCEMENT);
        tdMe.setWarningMessage(DEFAULT_MESSAGE);
        tenantDefault.setManagedExtension(tdMe);
        tenantDefault.setUrlFilters(new ArrayList<>());

        tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
    }

    // ==========================================================
    // createDefaultPolicyIfNotExists()
    // ==========================================================

    @Test
    void createDefaultPolicyIfNotExists_whenAlreadyExists_returnsExisting() {
        // ARRANGE
        when(extensionPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.of(globalDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.createDefaultPolicyIfNotExists();

        // ASSERT
        assertNotNull(result);
        assertEquals(GLOBAL_DEFAULT_ID, result.getPkExtensionPolicyId());
        verify(extensionPolicyRepository, never()).save(any());
    }

    @Test
    void createDefaultPolicyIfNotExists_whenNotExists_createsAndSaves() {
        // ARRANGE
        when(extensionPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.empty());
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class))).thenReturn(globalDefault);

        // ACT
        ExtensionPolicy result = extensionPolicyService.createDefaultPolicyIfNotExists();

        // ASSERT
        assertNotNull(result);
        assertNotNull(result.getPolicyKey());
        assertTrue(result.getIsActive());
        assertEquals(ExtensionAction.ALLOW_ALL, result.getManagedExtension().getAction());
        assertEquals(DEFAULT_ENFORCEMENT, result.getManagedExtension().getEnforcementAction());
        assertEquals(DEFAULT_MESSAGE, result.getManagedExtension().getWarningMessage());
        verify(extensionPolicyRepository).save(any(ExtensionPolicy.class));
    }

    // ==========================================================
    // createPolicy()
    // ==========================================================

    @Test
    void createPolicy_happyPath_savesAndRefetches() {
        // ARRANGE
        ExtensionPolicy dto = new ExtensionPolicy();
        dto.setName("New Policy");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

        ExtensionPolicy saved = new ExtensionPolicy();
        saved.setPkExtensionPolicyId("saved-id");
        saved.setName("New Policy");
        saved.setFkTenantId(TENANT_ID);

        when(extensionPolicyRepository.save(any(ExtensionPolicy.class))).thenReturn(saved);
        when(extensionPolicyRepository.findByIdWithRelations("saved-id")).thenReturn(Optional.of(saved));

        // ACT
        ExtensionPolicy result = extensionPolicyService.createPolicy(dto, request);

        // ASSERT
        assertNotNull(result);
        assertEquals("saved-id", result.getPkExtensionPolicyId());
        assertEquals(TENANT_ID, dto.getFkTenantId());
        assertNotNull(dto.getPolicyKey());
        assertEquals("0.1", dto.getVersion());
        assertTrue(dto.getIsActive());
        verify(extensionPolicyRepository).save(dto);
        verify(extensionPolicyRepository).findByIdWithRelations("saved-id");
    }

    @Test
    void createPolicy_withUrlFilters_linksThem() {
        // ARRANGE
        ExtensionPolicy dto = new ExtensionPolicy();
        dto.setName("With Filters");
        UrlFilter filter = new UrlFilter();
        filter.setPattern("example.com");
        dto.setUrlFilters(List.of(filter));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);

        ExtensionPolicy saved = new ExtensionPolicy();
        saved.setPkExtensionPolicyId("saved-filter-id");
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class))).thenReturn(saved);
        when(extensionPolicyRepository.findByIdWithRelations("saved-filter-id")).thenReturn(Optional.of(saved));

        // ACT
        ExtensionPolicy result = extensionPolicyService.createPolicy(dto, request);

        // ASSERT
        assertNotNull(result);
        assertEquals(dto, filter.getExtensionPolicy()); // filter linked before save
        verify(extensionPolicyRepository).save(dto);
    }

    @Test
    void createPolicy_whenSaveThrowsException_rethrows() {
        // ARRANGE
        ExtensionPolicy dto = new ExtensionPolicy();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class)))
                .thenThrow(new RuntimeException("DB error"));

        // ACT + ASSERT
        assertThrows(RuntimeException.class, () ->
                extensionPolicyService.createPolicy(dto, request));
    }

    // ==========================================================
    // getPolicyById(String id)
    // ==========================================================

    @Test
    void getPolicyById_happyPath_returnsPolicy() {
        // ARRANGE
        when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(globalDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.getPolicyById(POLICY_ID);

        // ASSERT
        assertNotNull(result);
        assertEquals(GLOBAL_DEFAULT_ID, result.getPkExtensionPolicyId());
    }

    @Test
    void getPolicyById_notFound_throwsIllegalArgumentException() {
        // ARRANGE
        when(extensionPolicyRepository.findByIdWithRelations("unknown-id")).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(IllegalArgumentException.class, () ->
                extensionPolicyService.getPolicyById("unknown-id"));
    }

    // ==========================================================
    // getPolicyById(id, tenantId) - tenant validation
    // ==========================================================

    @Test
    void getPolicyByIdWithTenantId_globalDefault_allowsAccess() {
        // ARRANGE
        globalDefault.setFkTenantId(null);
        when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(globalDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.getPolicyById(POLICY_ID, TENANT_ID);

        // ASSERT
        assertNotNull(result);
    }

    @Test
    void getPolicyByIdWithTenantId_matchingTenant_returnsPolicy() {
        // ARRANGE
        tenantDefault.setFkTenantId(TENANT_ID);
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID)).thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.getPolicyById(TENANT_DEFAULT_ID, TENANT_ID);

        // ASSERT
        assertNotNull(result);
    }

    @Test
    void getPolicyByIdWithTenantId_nonMatchingTenant_throwsException() {
        // ARRANGE
        ExtensionPolicy otherTenantPolicy = new ExtensionPolicy();
        otherTenantPolicy.setPkExtensionPolicyId("other-id");
        otherTenantPolicy.setFkTenantId("other-tenant");
        when(extensionPolicyRepository.findByIdWithRelations("other-id")).thenReturn(Optional.of(otherTenantPolicy));

        // ACT + ASSERT
        assertThrows(IllegalArgumentException.class, () ->
                extensionPolicyService.getPolicyById("other-id", TENANT_ID));
    }

    // ==========================================================
    // getAllPolicies()
    // ==========================================================

    @Test
    void getAllPolicies_defaultNotInList_addsDefaultAtFront() {
        // ARRANGE
        // Tenant policies (does NOT contain the tenant default)
        List<ExtensionPolicy> tenantPolicies = new ArrayList<>();
        ExtensionPolicy tenantPolicy = new ExtensionPolicy();
        tenantPolicy.setPkExtensionPolicyId("tenant-policy-1");
        tenantPolicies.add(tenantPolicy);
        when(extensionPolicyRepository.findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TENANT_ID))
                .thenReturn(tenantPolicies);

        // getDefaultPolicy finds existing tenant default (no creation)
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(tenantDefault));
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID))
                .thenReturn(Optional.of(tenantDefault));

        // ACT
        List<ExtensionPolicy> result = extensionPolicyService.getAllPolicies(TENANT_ID);

        // ASSERT
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(TENANT_DEFAULT_ID, result.get(0).getPkExtensionPolicyId()); // default first
    }

    @Test
    void getAllPolicies_defaultAlreadyPresent_doesNotAddAgain() {
        // ARRANGE
        // List already includes the tenant default
        List<ExtensionPolicy> tenantPolicies = new ArrayList<>();
        tenantPolicies.add(tenantDefault);
        when(extensionPolicyRepository.findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TENANT_ID))
                .thenReturn(tenantPolicies);

        // getDefaultPolicy returns same tenant default
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(tenantDefault));
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID))
                .thenReturn(Optional.of(tenantDefault));

        // ACT
        List<ExtensionPolicy> result = extensionPolicyService.getAllPolicies(TENANT_ID);

        // ASSERT
        assertEquals(1, result.size());
    }

    // ==========================================================
    // updatePolicy()
    // ==========================================================

    @Test
    void updatePolicy_happyPath_versionBumpAndClone() {
        // ARRANGE
        String id = POLICY_ID;
        ExtensionPolicy current = createCurrentPolicy("1.5", TENANT_ID);
        current.setGlobalDefault(false); // not global default
        when(extensionPolicyRepository.findByIdWithRelations(id)).thenReturn(Optional.of(current));

        ExtensionPolicy updated = new ExtensionPolicy();
        updated.setName("New Name");
        updated.setDescription("New Desc");
        updated.setLandingPageUrl("https://new.url");
        updated.setLandingPageId("new-lp");

        // Mocks for saveAndFlush, save
        when(extensionPolicyRepository.saveAndFlush(current)).thenReturn(current);
        ExtensionPolicy newSaved = new ExtensionPolicy();
        newSaved.setPkExtensionPolicyId("new-version-id");
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class))).thenReturn(newSaved);
        when(extensionPolicyRepository.findByIdWithRelations("new-version-id")).thenReturn(Optional.of(newSaved));

        // URL filters carried forward
        UrlFilter urlFilter = new UrlFilter();
        urlFilter.setPattern("*.test.com");
        current.setUrlFilters(List.of(urlFilter));

        HttpServletRequest request = mock(HttpServletRequest.class);

        // ACT
        ExtensionPolicy result = extensionPolicyService.updatePolicy(id, updated, request);

        // ASSERT
        assertNotNull(result);
        // Verify deactivation
        verify(extensionPolicyRepository).saveAndFlush(current);
        assertFalse(current.getIsActive());
        // Verify new version created with bumped version: 1.5 -> 1.6
        verify(extensionPolicyRepository).save(argThat(policy -> "1.6".equals(policy.getVersion())));
        // URL filters saved
        verify(urlFilterRepository).saveAll(anyList());
        // PolicyAssignment reference updated
        verify(policyAssignmentRepository).updateExtensionPolicyReference(eq(current.getPkExtensionPolicyId()), eq("new-version-id"));
    }

    @Test
    void updatePolicy_whenGlobalDefault_throwsException() {
        // ARRANGE
        ExtensionPolicy global = globalDefault; // fkTenantId null -> global
        when(extensionPolicyRepository.findByIdWithRelations(anyString())).thenReturn(Optional.of(global));

        // ACT + ASSERT
        assertThrows(IllegalArgumentException.class, () ->
                extensionPolicyService.updatePolicy("any-id", new ExtensionPolicy(), mock(HttpServletRequest.class)));
    }

    @Test
    void updatePolicy_nextMinorVersion_wrapsCorrectly() {
        // ARRANGE - version 1.9 -> 2.0
        ExtensionPolicy current = createCurrentPolicy("1.9", TENANT_ID);
        when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(current));
        when(extensionPolicyRepository.saveAndFlush(current)).thenReturn(current);
        ExtensionPolicy newSaved = new ExtensionPolicy();
        newSaved.setPkExtensionPolicyId("v2.0-id");
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class))).thenReturn(newSaved);
        when(extensionPolicyRepository.findByIdWithRelations("v2.0-id")).thenReturn(Optional.of(newSaved));

        ExtensionPolicy updated = new ExtensionPolicy();
        updated.setName("Name");

        // ACT
        extensionPolicyService.updatePolicy(POLICY_ID, updated, mock(HttpServletRequest.class));

        // ASSERT
        verify(extensionPolicyRepository).save(argThat(policy -> "2.0".equals(policy.getVersion())));
    }

    @Test
    void updatePolicy_nullDlpIsCarriedForward() {
        // ARRANGE
        ExtensionPolicy current = createCurrentPolicy("0.1", TENANT_ID);
        current.setDlp(null);
        when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(current));
        when(extensionPolicyRepository.saveAndFlush(current)).thenReturn(current);
        ExtensionPolicy newSaved = new ExtensionPolicy();
        newSaved.setPkExtensionPolicyId("new-id");
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class))).thenReturn(newSaved);
        when(extensionPolicyRepository.findByIdWithRelations("new-id")).thenReturn(Optional.of(newSaved));

        // ACT
        extensionPolicyService.updatePolicy(POLICY_ID, new ExtensionPolicy(), mock(HttpServletRequest.class));

        // ASSERT - no DLP on new version
        verify(extensionPolicyRepository).save(argThat(policy -> policy.getDlp() == null));
    }

    // ==========================================================
    // deletePolicy()
    // ==========================================================

    @Test
    void deletePolicy_happyPath_removesNonDefaultPolicy() {
        // ARRANGE
        ExtensionPolicy policy = new ExtensionPolicy();
        policy.setPkExtensionPolicyId(POLICY_ID);
        policy.setFkTenantId(TENANT_ID);
        policy.setGlobalDefault(false);
        policy.setTenantDefault(false);
        when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(policy));

        // ACT
        extensionPolicyService.deletePolicy(POLICY_ID);

        // ASSERT
        verify(extensionPolicyRepository).delete(policy);
    }

    @Test
    void deletePolicy_globalDefault_throwsException() {
        // ARRANGE
        when(extensionPolicyRepository.findByIdWithRelations(POLICY_ID)).thenReturn(Optional.of(globalDefault));

        // ACT + ASSERT
        assertThrows(IllegalArgumentException.class, () ->
                extensionPolicyService.deletePolicy(POLICY_ID));
    }

    @Test
    void deletePolicy_tenantDefault_throwsException() {
        // ARRANGE
        ExtensionPolicy tenantDefaultPolicy = new ExtensionPolicy();
        tenantDefaultPolicy.setPkExtensionPolicyId(TENANT_DEFAULT_ID);
        tenantDefaultPolicy.setTenantDefault(true);
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID)).thenReturn(Optional.of(tenantDefaultPolicy));

        // ACT + ASSERT
        assertThrows(IllegalArgumentException.class, () ->
                extensionPolicyService.deletePolicy(TENANT_DEFAULT_ID));
    }

    // ==========================================================
    // resolvePolicyForCurrentUser()
    // ==========================================================

    @Test
    void resolvePolicyForCurrentUser_happyPath_rolesAndGroups_resolvesToEffective() {
        // ARRANGE
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);
        List<String> roles = List.of("admin");
        List<String> groups = List.of("group-oid");
        when(jwtUtl.getClaimAsStringList(request, "roles")).thenReturn(roles);
        when(jwtUtl.getClaimAsStringList(request, "groups")).thenReturn(groups);

        // Resolve groups to EventsGroup IDs
        EventsGroup eg = new EventsGroup();
        eg.setPkEventsGroupId("eg-id");
        when(eventsGroupRepository.findByTenantIdAndIdentifiers(TENANT_ID, groups)).thenReturn(List.of(eg));

        // Effective policy will be called; mock its internals
        PolicyAssignment assignment = new PolicyAssignment();
        assignment.setExtensionPolicy(tenantDefault);
        when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                .thenReturn(List.of(assignment));
        // getEffectivePolicy calls findByIdWithRelations on the matched policy
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID)).thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.resolvePolicyForCurrentUser(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
        verify(extensionPolicyRepository).findByIdWithRelations(TENANT_DEFAULT_ID);
    }

    @Test
    void resolvePolicyForCurrentUser_tenantNull_throwsSecurityException() {
        // ARRANGE
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtl.getTenantFromRequest(request)).thenReturn(null);

        // ACT + ASSERT
        assertThrows(SecurityException.class, () ->
                extensionPolicyService.resolvePolicyForCurrentUser(request));
    }

    @Test
    void resolvePolicyForCurrentUser_noRolesOrGroups_machineTokenFallback() {
        // ARRANGE
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);
        when(jwtUtl.getClaimAsStringList(request, "roles")).thenReturn(null);
        when(jwtUtl.getClaimAsStringList(request, "groups")).thenReturn(null);

        EventsGroup defaultApiKeyGroup = new EventsGroup();
        defaultApiKeyGroup.setPkEventsGroupId("apikey-group-id");
        when(eventsGroupRepository.findByTenantIdAndIsDefault(TENANT_ID, true))
                .thenReturn(Optional.of(defaultApiKeyGroup));

        // No effective assignments, so fallback to default
        when(policyAssignmentRepository.findEffectiveAssignments(anyString(), anyList(), anyList()))
                .thenReturn(Collections.emptyList());
        // getDefaultPolicy returns existing tenant default to avoid creation
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(tenantDefault));
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID))
                .thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.resolvePolicyForCurrentUser(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
    }

    // ==========================================================
    // getEffectivePolicy()
    // ==========================================================

    @Test
    void getEffectivePolicy_assignmentFound_returnsPolicy() {
        // ARRANGE
        PolicyAssignment assignment = new PolicyAssignment();
        assignment.setExtensionPolicy(tenantDefault);
        when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                .thenReturn(List.of(assignment));
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID)).thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.getEffectivePolicy(TENANT_ID, List.of("role1"), List.of("grp1"));

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
    }

    @Test
    void getEffectivePolicy_noAssignment_fallsBackToDefault() {
        // ARRANGE
        when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                .thenReturn(Collections.emptyList());
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(tenantDefault)); // tenant default exists
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID)).thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.getEffectivePolicy(TENANT_ID, List.of("role1"), List.of("grp1"));

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
    }

    @Test
    void getEffectivePolicy_emptyRolesAndGroups_returnsDefaultDirectly() {
        // ARRANGE
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(tenantDefault));
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID)).thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.getEffectivePolicy(TENANT_ID, Collections.emptyList(), Collections.emptyList());

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
        verify(policyAssignmentRepository, never()).findEffectiveAssignments(anyString(), anyList(), anyList());
    }

    @Test
    void getEffectivePolicy_repoThrowsException_fallsBackToDefault() {
        // ARRANGE
        when(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                .thenThrow(new RuntimeException("DB error"));
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(tenantDefault));
        when(extensionPolicyRepository.findByIdWithRelations(TENANT_DEFAULT_ID)).thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.getEffectivePolicy(TENANT_ID, List.of("role1"), List.of("grp1"));

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
    }

    // ==========================================================
    // createTenantDefaultPolicy()
    // ==========================================================

    @Test
    void createTenantDefaultPolicy_alreadyExists_returnsExisting() {
        // ARRANGE
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.of(tenantDefault));

        // ACT
        ExtensionPolicy result = extensionPolicyService.createTenantDefaultPolicy(TENANT_ID);

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
        verify(extensionPolicyRepository, never()).save(any());
    }

    @Test
    void createTenantDefaultPolicy_createsFromGlobal_success() {
        // ARRANGE
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.empty());
        when(extensionPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.of(globalDefault)); // global exists
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class))).thenReturn(tenantDefault);

        // ACT
        ExtensionPolicy result = extensionPolicyService.createTenantDefaultPolicy(TENANT_ID);

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isTenantDefault());
        assertEquals(TENANT_ID, result.getFkTenantId());
        verify(extensionPolicyRepository).save(argThat(policy -> policy.isTenantDefault()));
    }

    @Test
    void createTenantDefaultPolicy_concurrentCreation_catchesDataIntegrityException() {
        // ARRANGE
        // first call returns empty, second returns existing (after exception)
        when(extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .thenReturn(Optional.empty())   // initial check
                .thenReturn(Optional.of(tenantDefault)); // re-fetch after exception

        when(extensionPolicyRepository.findTopByFkTenantIdIsNull()).thenReturn(Optional.of(globalDefault));
        when(extensionPolicyRepository.save(any(ExtensionPolicy.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        // ACT
        ExtensionPolicy result = extensionPolicyService.createTenantDefaultPolicy(TENANT_ID);

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_DEFAULT_ID, result.getPkExtensionPolicyId());
        verify(extensionPolicyRepository, atLeastOnce())
                .findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID);
    }

    // Helper method to create a policy for update tests
    private ExtensionPolicy createCurrentPolicy(String version, String tenantId) {
        ExtensionPolicy current = new ExtensionPolicy();
        current.setPkExtensionPolicyId(POLICY_ID);
        current.setFkTenantId(tenantId);
        current.setVersion(version);
        current.setIsActive(true);
        current.setName("Current Name");
        current.setDescription("Current Desc");
        current.setLandingPageUrl("https://old.url");
        current.setLandingPageId("old-lp");
        current.setGlobalDefault(tenantId == null); // null tenantId means global
        current.setTenantDefault(false);

        // Simple DLP
        Dlp dlp = new Dlp();
        dlp.setDisableCopy(true);
        current.setDlp(dlp);

        // ComplianceRules
        ComplianceRules cr = new ComplianceRules();
        cr.setAntivirusCheck(true);
        current.setComplianceRules(cr);

        // ManagedExtension
        ManagedExtension me = new ManagedExtension();
        me.setAction(ExtensionAction.BLOCK_ALL);
        me.setEnforcementAction("BLOCK");
        current.setManagedExtension(me);

        return current;
    }
}