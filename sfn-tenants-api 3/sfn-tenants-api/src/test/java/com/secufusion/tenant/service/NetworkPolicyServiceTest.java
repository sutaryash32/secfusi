package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.NetworkPolicyRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NetworkPolicyService Tests")
class NetworkPolicyServiceTest {

    @Mock
    private NetworkPolicyRepository networkPolicyRepository;

    @Mock
    private PolicyAssignmentRepository policyAssignmentRepository;

    @InjectMocks
    private NetworkPolicyService service;

    private static final String TENANT_ID = "tenant-123";
    private static final String POLICY_ID = "policy-uuid-001";
    private static final String POLICY_KEY = "policy-key-001";
    private static final String VERSION = "0.1";

    private NetworkPolicy mockPolicy;
    private NetworkPolicyRequestDTO mockRequestDto;
    private NetworkPolicy globalDefaultPolicy;

    @BeforeEach
    void setUp() {
        // Build mock policy
        mockPolicy = new NetworkPolicy();
        mockPolicy.setPkNetworkPolicyId(POLICY_ID);
        mockPolicy.setPolicyKey(POLICY_KEY);
        mockPolicy.setVersion(VERSION);
        mockPolicy.setActive(true);
        mockPolicy.setFkTenantId(TENANT_ID);
        mockPolicy.setName("Test Policy");
        mockPolicy.setDescription("Test Description");
        mockPolicy.setEnabled(true);
        mockPolicy.setTenantDefault(false);
        mockPolicy.setCreatedAt(LocalDateTime.now());
        mockPolicy.setUpdatedAt(LocalDateTime.now());
        mockPolicy.setUrlFilters(new ArrayList<>());
        NetworkConfiguration nc = new NetworkConfiguration();
        nc.setProxyMode("direct");
        mockPolicy.setNetworkConfiguration(nc);

        // Build global default policy
        globalDefaultPolicy = new NetworkPolicy();
        globalDefaultPolicy.setPkNetworkPolicyId("global-001");
        globalDefaultPolicy.setFkTenantId(null);
        globalDefaultPolicy.setName("Default Network Policy");
        globalDefaultPolicy.setVersion("0.1");
        globalDefaultPolicy.setActive(true);
        globalDefaultPolicy.setTenantDefault(false);
        globalDefaultPolicy.setUrlFilters(new ArrayList<>());
        globalDefaultPolicy.setNetworkConfiguration(nc);

        // Build request DTO
        mockRequestDto = new NetworkPolicyRequestDTO();
        mockRequestDto.setName("Updated Policy");
        mockRequestDto.setDescription("Updated Description");
        mockRequestDto.setEnabled(false);
        mockRequestDto.setUrlFilters(List.of(createUrlFilterDTO()));
        mockRequestDto.setNetworkConfiguration(createNetworkConfigurationDTO());
    }

    private UrlFilterDTO createUrlFilterDTO() {
        UrlFilterDTO dto = new UrlFilterDTO();
        dto.setFilterType("ALLOW");
        dto.setPatternType("REGEX");
        dto.setPattern(".*\\.example\\.com");
        dto.setDescription("Allow example.com");
        return dto;
    }

    private NetworkConfigurationDTO createNetworkConfigurationDTO() {
        NetworkConfigurationDTO dto = new NetworkConfigurationDTO();
        dto.setProxyMode("manual");
        dto.setPacUrl("http://pac.example.com");
        dto.setProxyServers("proxy.example.com:8080");
        dto.setBypassList("localhost,127.0.0.1");
        dto.setUseSecufusionIdpProxy(true);
        dto.setCustomIdpHostnames("idp.example.com");
        dto.setIdentityProvider("AZURE");
        dto.setHostnames("{\"example\":\"value\"}"); // JSON string, will be parsed by DTO setter
        return dto;
    }

    // ==================== CREATE ====================

    @Nested
    @DisplayName("create")
    class CreateMethod {

        @Test
        @DisplayName("Happy Path — creates new policy with version 0.1")
        void happyPath_createsPolicy() {
            given(networkPolicyRepository.save(any(NetworkPolicy.class))).willReturn(mockPolicy);

            NetworkPolicyResponseDTO response = service.create(mockRequestDto, TENANT_ID);

            assertNotNull(response);
            assertEquals(POLICY_ID, response.getNetworkPolicyId());
            verify(networkPolicyRepository).save(argThat(policy -> {
                assertEquals(TENANT_ID, policy.getFkTenantId());
                assertEquals("0.1", policy.getVersion());
                assertTrue(policy.isActive());
                assertNotNull(policy.getPolicyKey());
                assertNotNull(policy.getUrlFilters());
                assertEquals(1, policy.getUrlFilters().size());
                return true;
            }));
        }

        @Test
        @DisplayName("Sad Path — repository save throws exception")
        void sadPath_saveThrows() {
            given(networkPolicyRepository.save(any(NetworkPolicy.class)))
                    .willThrow(new RuntimeException("DB error"));

            assertThrows(RuntimeException.class, () -> service.create(mockRequestDto, TENANT_ID));
        }
    }

    // ==================== DEFAULT POLICY ====================

    @Nested
    @DisplayName("createDefaultPolicyIfNotExists")
    class CreateDefaultPolicyIfNotExistsMethod {

        @Test
        @DisplayName("Happy Path — global default does not exist, creates new")
        void happyPath_createsGlobalDefault() {
            given(networkPolicyRepository.findTopByFkTenantIdIsNull()).willReturn(Optional.empty());
            given(networkPolicyRepository.save(any(NetworkPolicy.class))).willAnswer(inv -> inv.getArgument(0));

            NetworkPolicy result = service.createDefaultPolicyIfNotExists();

            assertNotNull(result);
            assertNull(result.getFkTenantId());
            assertEquals("Default Network Policy", result.getName());
            assertTrue(result.isEnabled());
            assertTrue(result.isActive());
            assertEquals("0.1", result.getVersion());
            verify(networkPolicyRepository).save(any(NetworkPolicy.class));
        }

        @Test
        @DisplayName("Happy Path — global default already exists, returns existing")
        void happyPath_alreadyExists() {
            given(networkPolicyRepository.findTopByFkTenantIdIsNull()).willReturn(Optional.of(mockPolicy));

            NetworkPolicy result = service.createDefaultPolicyIfNotExists();

            assertEquals(mockPolicy, result);
            verify(networkPolicyRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — concurrent creation, falls back to fetch")
        void sadPath_concurrentCreation() {
            given(networkPolicyRepository.findTopByFkTenantIdIsNull())
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(mockPolicy));
            given(networkPolicyRepository.save(any(NetworkPolicy.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            NetworkPolicy result = service.createDefaultPolicyIfNotExists();

            assertEquals(mockPolicy, result);
            verify(networkPolicyRepository, times(2)).findTopByFkTenantIdIsNull();
        }
    }

    // ==================== READ ====================

    @Nested
    @DisplayName("getById")
    class GetByIdMethod {

        @Test
        @DisplayName("Happy Path — policy found by ID")
        void happyPath_policyFound() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            NetworkPolicyResponseDTO response = service.getById(POLICY_ID, TENANT_ID);

            assertNotNull(response);
            assertEquals(POLICY_ID, response.getNetworkPolicyId());
        }

        @Test
        @DisplayName("Happy Path — policy not found, falls back to tenant default")
        void happyPath_fallbackToTenantDefault() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.empty());
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .willReturn(Optional.of(mockPolicy));

            NetworkPolicyResponseDTO response = service.getById(POLICY_ID, TENANT_ID);

            assertNotNull(response);
            assertEquals(POLICY_ID, response.getNetworkPolicyId());
        }

        @Test
        @DisplayName("Sad Path — policy and tenant default not found")
        void sadPath_notFound() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.empty());
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .willReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.getById(POLICY_ID, TENANT_ID));
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAllMethod {

        @Test
        @DisplayName("Happy Path — returns active policies plus default")
        void happyPath_returnsPolicies() {
            List<NetworkPolicy> policies = List.of(mockPolicy);
            NetworkPolicy tenantDefaultPolicy = new NetworkPolicy();
            tenantDefaultPolicy.setPkNetworkPolicyId("policy-uuid-default");
            tenantDefaultPolicy.setFkTenantId(TENANT_ID);
            tenantDefaultPolicy.setTenantDefault(true);
            tenantDefaultPolicy.setActive(true);
            tenantDefaultPolicy.setName("Tenant Default Policy");
            tenantDefaultPolicy.setCreatedAt(LocalDateTime.now().minusMinutes(1));
            tenantDefaultPolicy.setUrlFilters(new ArrayList<>());
            tenantDefaultPolicy.setNetworkConfiguration(new NetworkConfiguration());

            given(networkPolicyRepository.findAllByFkTenantIdAndIsActiveTrue(TENANT_ID)).willReturn(policies);
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .willReturn(Optional.of(tenantDefaultPolicy));
            given(networkPolicyRepository.findByIdWithRelations("policy-uuid-default"))
                .willReturn(Optional.of(tenantDefaultPolicy));

            List<NetworkPolicyResponseDTO> result = service.getAll(TENANT_ID);

            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("getNetworkPolicyById")
    class GetNetworkPolicyByIdMethod {

        @Test
        @DisplayName("Happy Path — policy belongs to tenant")
        void happyPath_policyBelongsToTenant() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            NetworkPolicy result = service.getNetworkPolicyById(TENANT_ID, POLICY_ID);

            assertEquals(mockPolicy, result);
        }

        @Test
        @DisplayName("Sad Path — policy does not belong to tenant")
        void sadPath_wrongTenant() {
            mockPolicy.setFkTenantId("other-tenant");
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            assertThrows(IllegalArgumentException.class, () -> service.getNetworkPolicyById(TENANT_ID, POLICY_ID));
        }

        @Test
        @DisplayName("Sad Path — policy not found")
        void sadPath_notFound() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.getNetworkPolicyById(TENANT_ID, POLICY_ID));
        }
    }

    @Nested
    @DisplayName("getDefaultPolicyForTenant")
    class GetDefaultPolicyForTenantMethod {

        @Test
        @DisplayName("Happy Path — tenant default exists")
        void happyPath_tenantDefaultExists() {
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .willReturn(Optional.of(mockPolicy));
            given(networkPolicyRepository.findByIdWithRelations(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            NetworkPolicy result = service.getDefaultPolicyForTenant(TENANT_ID);

            assertEquals(mockPolicy, result);
        }

        @Test
        @DisplayName("Happy Path — creates tenant default if missing")
        void happyPath_createsTenantDefault() {
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .willReturn(Optional.empty());
            given(networkPolicyRepository.findTopByFkTenantIdIsNull()).willReturn(Optional.of(globalDefaultPolicy));
            given(networkPolicyRepository.save(any(NetworkPolicy.class))).willAnswer(inv -> inv.getArgument(0));
            given(networkPolicyRepository.findByIdWithRelations(any())).willReturn(Optional.empty());

            NetworkPolicy result = service.getDefaultPolicyForTenant(TENANT_ID);

            assertNotNull(result);
            assertEquals(TENANT_ID, result.getFkTenantId());
            assertTrue(result.isTenantDefault());
        }

        @Test
        @DisplayName("Happy Path — tenantId null, returns global default")
        void happyPath_globalDefault() {
            given(networkPolicyRepository.findTopByFkTenantIdIsNull()).willReturn(Optional.of(globalDefaultPolicy));
            given(networkPolicyRepository.findByIdWithRelations(any())).willReturn(Optional.of(globalDefaultPolicy));

            NetworkPolicy result = service.getDefaultPolicyForTenant(null);

            assertEquals(globalDefaultPolicy, result);
        }
    }

    @Nested
    @DisplayName("createTenantDefaultPolicy")
    class CreateTenantDefaultPolicyMethod {

        @Test
        @DisplayName("Happy Path — creates tenant default from global")
        void happyPath_createsFromGlobal() {
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .willReturn(Optional.empty());
            given(networkPolicyRepository.findTopByFkTenantIdIsNull()).willReturn(Optional.of(globalDefaultPolicy));
            given(networkPolicyRepository.save(any(NetworkPolicy.class))).willAnswer(inv -> inv.getArgument(0));

            NetworkPolicy result = service.createTenantDefaultPolicy(TENANT_ID);

            assertNotNull(result);
            assertEquals(TENANT_ID, result.getFkTenantId());
            assertTrue(result.isTenantDefault());
            assertEquals(globalDefaultPolicy.getName(), result.getName());
        }

        @Test
        @DisplayName("Sad Path — already exists, returns existing")
        void sadPath_alreadyExists() {
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .willReturn(Optional.of(mockPolicy));

            NetworkPolicy result = service.createTenantDefaultPolicy(TENANT_ID);

            assertEquals(mockPolicy, result);
            verify(networkPolicyRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — concurrent insert, fallback")
        void sadPath_concurrentInsert() {
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(mockPolicy));
            given(networkPolicyRepository.findTopByFkTenantIdIsNull()).willReturn(Optional.of(globalDefaultPolicy));
            given(networkPolicyRepository.save(any(NetworkPolicy.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            NetworkPolicy result = service.createTenantDefaultPolicy(TENANT_ID);

            assertEquals(mockPolicy, result);
        }
    }

    @Nested
    @DisplayName("getEffectivePolicy")
    class GetEffectivePolicyMethod {

        @Test
        @DisplayName("Happy Path — assignment matches role, returns associated policy")
        void happyPath_matchByRole() {
            PolicyAssignment assignment = new PolicyAssignment();
            assignment.setNetworkPolicy(mockPolicy);
            given(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                    .willReturn(List.of(assignment));
            given(networkPolicyRepository.findByIdWithRelations(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            NetworkPolicy result = service.getEffectivePolicy(TENANT_ID, List.of("ADMIN"), List.of());

            assertEquals(mockPolicy, result);
        }

        @Test
        @DisplayName("Happy Path — assignment matches group")
        void happyPath_matchByGroup() {
            PolicyAssignment assignment = new PolicyAssignment();
            assignment.setNetworkPolicy(mockPolicy);
            given(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                    .willReturn(List.of(assignment));
            given(networkPolicyRepository.findByIdWithRelations(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            NetworkPolicy result = service.getEffectivePolicy(TENANT_ID, List.of(), List.of("group-1"));

            assertEquals(mockPolicy, result);
        }

        @Test
        @DisplayName("Happy Path — no assignment, falls back to default policy")
        void happyPath_fallbackToDefault() {
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                .willReturn(Optional.empty());
            given(networkPolicyRepository.findTopByFkTenantIdIsNull()).willReturn(Optional.of(globalDefaultPolicy));
            given(networkPolicyRepository.save(any(NetworkPolicy.class))).willAnswer(inv -> inv.getArgument(0));
            given(networkPolicyRepository.findByIdWithRelations(any())).willReturn(Optional.empty());

            NetworkPolicy result = service.getEffectivePolicy(TENANT_ID, List.of(), List.of());

            assertNotNull(result);
            assertEquals(TENANT_ID, result.getFkTenantId());
            assertTrue(result.isTenantDefault());
        }

        @Test
        @DisplayName("Sad Path — repository throws exception, falls back to default")
        void sadPath_repositoryThrows() {
            given(policyAssignmentRepository.findEffectiveAssignments(eq(TENANT_ID), anyList(), anyList()))
                    .willThrow(new RuntimeException("DB error"));
            given(networkPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(TENANT_ID))
                    .willReturn(Optional.of(mockPolicy));
            given(networkPolicyRepository.findByIdWithRelations(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            NetworkPolicy result = service.getEffectivePolicy(TENANT_ID, List.of("ADMIN"), List.of());

            assertEquals(mockPolicy, result);
        }
    }

    // ==================== UPDATE ====================

    @Nested
    @DisplayName("update")
    class UpdateMethod {

        @Test
        @DisplayName("Happy Path — deactivates old, creates new version with incremented minor")
        void happyPath_updateSuccess() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));
            given(networkPolicyRepository.saveAndFlush(any(NetworkPolicy.class))).willReturn(mockPolicy);
            given(networkPolicyRepository.save(any(NetworkPolicy.class))).willAnswer(inv -> inv.getArgument(0));
            doNothing().when(policyAssignmentRepository).updateNetworkPolicyReference(anyString(), any());

            NetworkPolicyResponseDTO response = service.update(POLICY_ID, mockRequestDto, TENANT_ID);

            assertNotNull(response);
            assertEquals("Updated Policy", response.getName());
            ArgumentCaptor<NetworkPolicy> captor = ArgumentCaptor.forClass(NetworkPolicy.class);
            verify(networkPolicyRepository, times(1)).save(captor.capture());
            NetworkPolicy newVersion = captor.getValue();
            assertTrue(newVersion.isActive());
            assertEquals("0.2", newVersion.getVersion());
            assertEquals(mockPolicy.getPolicyKey(), newVersion.getPolicyKey());
        }

        @Test
        @DisplayName("Sad Path — cannot update global default")
        void sadPath_globalDefault() {
            mockPolicy.setFkTenantId(null);
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            assertThrows(IllegalArgumentException.class, () -> service.update(POLICY_ID, mockRequestDto, TENANT_ID));
            verify(networkPolicyRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — policy not found")
        void sadPath_notFound() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.update(POLICY_ID, mockRequestDto, TENANT_ID));
        }
    }

    // ==================== DELETE ====================

    @Nested
    @DisplayName("delete")
    class DeleteMethod {

        @Test
        @DisplayName("Happy Path — deletes policy owned by tenant")
        void happyPath_deleteSuccess() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));
            doNothing().when(networkPolicyRepository).delete(mockPolicy);

            assertDoesNotThrow(() -> service.delete(POLICY_ID, TENANT_ID));
            verify(networkPolicyRepository).delete(mockPolicy);
        }

        @Test
        @DisplayName("Sad Path — cannot delete global default")
        void sadPath_globalDefault() {
            mockPolicy.setFkTenantId(null);
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            assertThrows(IllegalArgumentException.class, () -> service.delete(POLICY_ID, TENANT_ID));
            verify(networkPolicyRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Sad Path — cannot delete tenant default")
        void sadPath_tenantDefault() {
            mockPolicy.setTenantDefault(true);
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            assertThrows(IllegalArgumentException.class, () -> service.delete(POLICY_ID, TENANT_ID));
        }

        @Test
        @DisplayName("Sad Path — policy belongs to different tenant")
        void sadPath_wrongTenant() {
            mockPolicy.setFkTenantId("other-tenant");
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.of(mockPolicy));

            assertThrows(IllegalArgumentException.class, () -> service.delete(POLICY_ID, TENANT_ID));
        }

        @Test
        @DisplayName("Sad Path — policy not found")
        void sadPath_notFound() {
            given(networkPolicyRepository.findById(POLICY_ID)).willReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.delete(POLICY_ID, TENANT_ID));
        }
    }

    // ==================== ADDITIONAL TESTS FOR PRIVATE METHODS (indirect coverage) ====================

    @Nested
    @DisplayName("mapToResponsePublic - public wrapper for private mapToResponse")
    class MapToResponsePublicMethod {

        @Test
        @DisplayName("Happy Path — returns null for null policy")
        void happyPath_nullPolicy() {
            assertNull(service.mapToResponsePublic(null));
        }

        @Test
        @DisplayName("Happy Path — returns response DTO for valid policy")
        void happyPath_validPolicy() {
            NetworkPolicyResponseDTO response = service.mapToResponsePublic(mockPolicy);
            assertNotNull(response);
            assertEquals(POLICY_ID, response.getNetworkPolicyId());
        }
    }
}