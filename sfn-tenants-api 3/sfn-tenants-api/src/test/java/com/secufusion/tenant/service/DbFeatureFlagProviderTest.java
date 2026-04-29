package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.ApiFlagEntity;
import com.secufusion.tenant.entity.TenantApiMappingEntity;
import com.secufusion.tenant.repository.ApiFlagRepository;
import com.secufusion.tenant.repository.TenantApiMappingRepository;
import dev.openfeature.sdk.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbFeatureFlagProviderTest {

    @Mock
    private ApiFlagRepository apiRepo;

    @Mock
    private TenantApiMappingRepository tenantApiRepo;

    @InjectMocks
    private DbFeatureFlagProvider provider;

    private EvaluationContext ctxWithTenant;
    private EvaluationContext ctxNoTenant;

    @BeforeEach
    void setUp() {
        // Use MutableContext or ImmutableContext – here MutableContext
        MutableContext mutableCtx = new MutableContext();
        mutableCtx.add("tenantId", "tenant-1");
        ctxWithTenant = mutableCtx;

        ctxNoTenant = new ImmutableContext(); // empty context (no tenant)
    }

    @Test
    void getMetadata_returnsProviderName() {
        Metadata metadata = provider.getMetadata();
        assertNotNull(metadata);
        assertEquals("db-feature-provider", metadata.getName());
    }

    @Nested
    @DisplayName("getBooleanEvaluation")
    class GetBooleanEvaluation {

        @Test
        void allows_whenTenantIdMissing() {
            // ARRANGE – context without tenantId

            // ACT
            ProviderEvaluation<Boolean> eval = provider.getBooleanEvaluation("/path", false, ctxNoTenant);

            // ASSERT
            assertNotNull(eval);
            assertTrue(eval.getValue());
            assertEquals("NO_TENANT_ID", eval.getReason());
            verifyNoInteractions(apiRepo, tenantApiRepo);
        }

        @Test
        void allows_whenApiNotDefined() {
            // ARRANGE
            when(apiRepo.findByPath("/api")).thenReturn(Optional.empty());

            // ACT
            ProviderEvaluation<Boolean> eval = provider.getBooleanEvaluation("/api", false, ctxWithTenant);

            // ASSERT
            assertTrue(eval.getValue());
            assertEquals("API_NOT_DEFINED", eval.getReason());
            verify(apiRepo).findByPath("/api");
            verifyNoInteractions(tenantApiRepo);
        }

        @Test
        void allows_whenNoTenantMapping() {
            // ARRANGE
            ApiFlagEntity api = new ApiFlagEntity();
            api.setId(10L);
            api.setPath("/api");
            when(apiRepo.findByPath("/api")).thenReturn(Optional.of(api));
            when(tenantApiRepo.findByApiIdAndTenantId(10L, "tenant-1")).thenReturn(Optional.empty());

            // ACT
            ProviderEvaluation<Boolean> eval = provider.getBooleanEvaluation("/api", false, ctxWithTenant);

            // ASSERT
            assertTrue(eval.getValue());
            assertEquals("NO_TENANT_MAPPING", eval.getReason());
            verify(tenantApiRepo).findByApiIdAndTenantId(10L, "tenant-1");
        }

        @Test
        void blocks_whenMappingEnabledTrue() {
            // ARRANGE
            ApiFlagEntity api = new ApiFlagEntity();
            api.setId(10L);
            api.setPath("/api");
            when(apiRepo.findByPath("/api")).thenReturn(Optional.of(api));

            TenantApiMappingEntity mapping = new TenantApiMappingEntity();
            mapping.setEnabled(true);
            when(tenantApiRepo.findByApiIdAndTenantId(10L, "tenant-1")).thenReturn(Optional.of(mapping));

            // ACT
            ProviderEvaluation<Boolean> eval = provider.getBooleanEvaluation("/api", true, ctxWithTenant);

            // ASSERT
            assertFalse(eval.getValue());
            assertEquals("BLOCK", eval.getVariant());
            assertEquals("TENANT_MAPPING_FOUND", eval.getReason());
        }

        @Test
        void allows_whenMappingEnabledFalse() {
            // ARRANGE
            ApiFlagEntity api = new ApiFlagEntity();
            api.setId(10L);
            api.setPath("/api");
            when(apiRepo.findByPath("/api")).thenReturn(Optional.of(api));

            TenantApiMappingEntity mapping = new TenantApiMappingEntity();
            mapping.setEnabled(false);
            when(tenantApiRepo.findByApiIdAndTenantId(10L, "tenant-1")).thenReturn(Optional.of(mapping));

            // ACT
            ProviderEvaluation<Boolean> eval = provider.getBooleanEvaluation("/api", false, ctxWithTenant);

            // ASSERT
            assertTrue(eval.getValue());
            assertEquals("ALLOW", eval.getVariant());
            assertEquals("TENANT_MAPPING_FOUND", eval.getReason());
        }
    }

    @Nested
    @DisplayName("unsupported evaluation types")
    class UnsupportedTypes {

        @Test
        void getStringEvaluation_returnsDefaultWithUnsupportedReason() {
            ProviderEvaluation<String> eval = provider.getStringEvaluation("k", "d", ctxWithTenant);
            assertNotNull(eval);
            assertEquals("d", eval.getValue());
            assertEquals("UNSUPPORTED", eval.getReason());
        }

        @Test
        void getIntegerEvaluation_returnsDefaultWithUnsupportedReason() {
            ProviderEvaluation<Integer> eval = provider.getIntegerEvaluation("k", 5, ctxWithTenant);
            assertNotNull(eval);
            assertEquals(5, eval.getValue());
            assertEquals("UNSUPPORTED", eval.getReason());
        }

        @Test
        void getDoubleEvaluation_returnsDefaultWithUnsupportedReason() {
            ProviderEvaluation<Double> eval = provider.getDoubleEvaluation("k", 2.5, ctxWithTenant);
            assertNotNull(eval);
            assertEquals(2.5, eval.getValue());
            assertEquals("UNSUPPORTED", eval.getReason());
        }

        @Test
        void getObjectEvaluation_returnsDefaultWithUnsupportedReason() {
            Value def = new Value("x");
            ProviderEvaluation<Value> eval = provider.getObjectEvaluation("k", def, ctxWithTenant);
            assertNotNull(eval);
            assertEquals(def, eval.getValue());
            assertEquals("UNSUPPORTED", eval.getReason());
        }
    }
}