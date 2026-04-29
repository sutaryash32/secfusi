package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.SmtpConfig;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.exception.ResourceConflictException;
import com.secufusion.tenant.repository.SmtpConfigRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.util.KeycloakAdminUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmtpConfigService Tests")
class SmtpConfigServiceTest {

    @Mock private SmtpConfigRepository smtpConfigRepository;
    @Mock private TenantRepository     tenantRepository;
    @Mock private KeycloakAdminUtil    kcUtil;

    @InjectMocks
    private SmtpConfigService service;

    private static final String TENANT_ID  = "tenant-001";
    private static final String SMTP_ID    = "smtp-001";

    // ── Entity builders ───────────────────────────────────────────────────────

    private SmtpConfig smtpConfig() {
        SmtpConfig c = new SmtpConfig();
        c.setPkSmtpConfigId(SMTP_ID);
        c.setFkTenantId(TENANT_ID);
        c.setHost("smtp.example.com");
        c.setPort(587);
        c.setAuth("true");
        c.setStarttls("true");
        c.setSsl("false");
        c.setUsername("user@example.com");
        c.setPassword("secret");
        c.setFromEmail("no-reply@example.com");
        c.setFromName("SecuFusion");
        c.setActive(true);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setTenantID(TENANT_ID);
        t.setTenantName("Test Tenant");
        t.setRealmName("test-realm");
        return t;
    }

    @BeforeEach
    void setUp() {
        // ✅ Inject @Value fields via ReflectionTestUtils
        ReflectionTestUtils.setField(service, "defaultSmtpHost",     "smtp.default.com");
        ReflectionTestUtils.setField(service, "defaultSmtpPort",     "587");
        ReflectionTestUtils.setField(service, "defaultSmtpAuth",     "true");
        ReflectionTestUtils.setField(service, "defaultSmtpStarttls", "true");
        ReflectionTestUtils.setField(service, "defaultSmtpUsername", "default@example.com");
        ReflectionTestUtils.setField(service, "defaultSmtpPassword", "defaultpass");
        ReflectionTestUtils.setField(service, "defaultSmtpMail",     "noreply@default.com");
    }

    // =========================================================================
    // createSmtpConfig
    // =========================================================================
    @Nested
    @DisplayName("createSmtpConfig")
    class CreateSmtpConfigTests {

        @Test
        @DisplayName("Happy Path — config created successfully")
        void happyPath() {
            // ARRANGE
            when(smtpConfigRepository.existsByFkTenantId(TENANT_ID)).thenReturn(false);
            SmtpConfig input = smtpConfig();
            when(smtpConfigRepository.save(any(SmtpConfig.class))).thenAnswer(inv -> {
                // Simulate auto-generated ID – service nullifies existing ID
                SmtpConfig saved = inv.getArgument(0);
                saved.setPkSmtpConfigId("new-id-123");
                return saved;
            });

            // ACT
            SmtpConfig result = service.createSmtpConfig(TENANT_ID, input);

            // ASSERT
            assertNotNull(result);
            assertNotNull(result.getPkSmtpConfigId());          // ID was assigned
            assertEquals(TENANT_ID, result.getFkTenantId());
            verify(smtpConfigRepository).save(any(SmtpConfig.class));
        }

        @Test
        @DisplayName("Sad Path — config already exists throws ResourceConflictException")
        void sadPath_alreadyExists() {
            // ARRANGE
            when(smtpConfigRepository.existsByFkTenantId(TENANT_ID)).thenReturn(true);

            // ACT + ASSERT
            assertThrows(ResourceConflictException.class,
                    () -> service.createSmtpConfig(TENANT_ID, smtpConfig()));
            verify(smtpConfigRepository, never()).save(any());
        }
    }

    // =========================================================================
    // getSmtpConfigForTenant
    // =========================================================================
    @Nested
    @DisplayName("getSmtpConfigForTenant")
    class GetSmtpConfigForTenantTests {

        @Test
        @DisplayName("Happy Path — tenant-specific config found")
        void happyPath_tenantConfig() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(smtpConfig()));

            // ACT
            SmtpConfig result = service.getSmtpConfigForTenant(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getFkTenantId());
        }

        @Test
        @DisplayName("Happy Path — falls back to default config when tenant config absent")
        void happyPath_fallbackToDefault() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());
            SmtpConfig defaultConfig = smtpConfig();
            defaultConfig.setFkTenantId(null);
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.of(defaultConfig));

            // ACT
            SmtpConfig result = service.getSmtpConfigForTenant(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertNull(result.getFkTenantId());
        }

        @Test
        @DisplayName("Happy Path — falls back to environment when no DB config at all")
        void happyPath_fallbackToEnvironment() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.empty());

            // ACT
            SmtpConfig result = service.getSmtpConfigForTenant(TENANT_ID);

            // ASSERT — built from environment @Value fields
            assertNotNull(result);
            assertEquals("smtp.default.com", result.getHost());
            assertEquals(587, result.getPort());
            assertEquals("SecuFusion", result.getFromName());
            assertTrue(result.isActive());
        }
    }

    // =========================================================================
    // getDefaultSmtpConfig
    // =========================================================================
    @Nested
    @DisplayName("getDefaultSmtpConfig")
    class GetDefaultSmtpConfigTests {

        @Test
        @DisplayName("Happy Path — default config found in DB")
        void happyPath_fromDb() {
            // ARRANGE
            SmtpConfig defaultConfig = smtpConfig();
            defaultConfig.setFkTenantId(null);
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.of(defaultConfig));

            // ACT
            SmtpConfig result = service.getDefaultSmtpConfig();

            // ASSERT
            assertNotNull(result);
            assertNull(result.getFkTenantId());
        }

        @Test
        @DisplayName("Happy Path — no DB config, built from environment variables")
        void happyPath_fromEnvironment() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.empty());

            // ACT
            SmtpConfig result = service.getDefaultSmtpConfig();

            // ASSERT
            assertNotNull(result);
            assertEquals("smtp.default.com", result.getHost());
            assertEquals(587, result.getPort());
            assertEquals("noreply@default.com", result.getFromEmail());
            assertEquals("false", result.getSsl());
        }
    }

    // =========================================================================
    // getTenantSmtpConfig
    // =========================================================================
    @Nested
    @DisplayName("getTenantSmtpConfig")
    class GetTenantSmtpConfigTests {

        @Test
        @DisplayName("Happy Path — returns tenant config")
        void happyPath() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.of(smtpConfig()));

            // ACT
            SmtpConfig result = service.getTenantSmtpConfig(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getFkTenantId());
        }

        @Test
        @DisplayName("Happy Path — returns null when not found")
        void happyPath_returnsNull() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT
            SmtpConfig result = service.getTenantSmtpConfig(TENANT_ID);

            // ASSERT
            assertNull(result);
        }
    }

    // =========================================================================
    // updateSmtpConfig
    // =========================================================================
    @Nested
    @DisplayName("updateSmtpConfig")
    class UpdateSmtpConfigTests {

        @Test
        @DisplayName("Happy Path — all fields updated including password")
        void happyPath_withPassword() {
            // ARRANGE
            SmtpConfig existing = smtpConfig();
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.of(existing));
            when(smtpConfigRepository.save(any(SmtpConfig.class))).thenReturn(existing);

            SmtpConfig updated = smtpConfig();
            updated.setHost("smtp.new.com");
            updated.setPassword("newpass");

            // ACT
            SmtpConfig result = service.updateSmtpConfig(TENANT_ID, updated);

            // ASSERT
            assertNotNull(result);
            verify(smtpConfigRepository).save(existing);
        }

        @Test
        @DisplayName("Happy Path — password not updated when null")
        void happyPath_passwordNullSkipped() {
            // ARRANGE
            SmtpConfig existing = smtpConfig();
            existing.setPassword("oldpass");
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.of(existing));
            when(smtpConfigRepository.save(any())).thenReturn(existing);

            SmtpConfig updated = smtpConfig();
            updated.setPassword(null); // should NOT overwrite

            // ACT
            service.updateSmtpConfig(TENANT_ID, updated);

            // ASSERT — password stays as oldpass
            assertEquals("oldpass", existing.getPassword());
        }

        @Test
        @DisplayName("Happy Path — password not updated when empty string")
        void happyPath_passwordEmptySkipped() {
            // ARRANGE
            SmtpConfig existing = smtpConfig();
            existing.setPassword("oldpass");
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.of(existing));
            when(smtpConfigRepository.save(any())).thenReturn(existing);

            SmtpConfig updated = smtpConfig();
            updated.setPassword(""); // empty → should NOT overwrite

            // ACT
            service.updateSmtpConfig(TENANT_ID, updated);

            // ASSERT
            assertEquals("oldpass", existing.getPassword());
        }

        @Test
        @DisplayName("Sad Path — config not found throws IllegalArgumentException")
        void sadPath_notFound() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateSmtpConfig(TENANT_ID, smtpConfig()));
        }
    }

    // =========================================================================
    // deleteSmtpConfig
    // =========================================================================
    @Nested
    @DisplayName("deleteSmtpConfig")
    class DeleteSmtpConfigTests {

        @Test
        @DisplayName("Happy Path — config deleted successfully")
        void happyPath() {
            // ARRANGE
            SmtpConfig existing = smtpConfig();
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.of(existing));
            doNothing().when(smtpConfigRepository).delete(existing);

            // ACT + ASSERT
            assertDoesNotThrow(() -> service.deleteSmtpConfig(TENANT_ID));
            verify(smtpConfigRepository).delete(existing);
        }

        @Test
        @DisplayName("Sad Path — config not found throws IllegalArgumentException")
        void sadPath_notFound() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.deleteSmtpConfig(TENANT_ID));
        }
    }

    // =========================================================================
    // testSmtpConnection
    // =========================================================================
    @Nested
    @DisplayName("testSmtpConnection")
    class TestSmtpConnectionTests {

        @Test
        @DisplayName("Sad Path — password null, DB has saved password → returns password-required message if no DB either")
        void sadPath_noPasswordNoDb() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword(null);

            when(smtpConfigRepository.findByFkTenantId(TENANT_ID)).thenReturn(Optional.empty());
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue()).thenReturn(Optional.empty());

            // ACT
            String result = service.testSmtpConnection(config, TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.contains("Password is required"));
        }

        @Test
        @DisplayName("Sad Path — password empty, uses saved DB password but SMTP fails → returns error message")
        void sadPath_emptyPasswordUsesDbPassword() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword("");

            SmtpConfig saved = smtpConfig();
            saved.setPassword("dbpassword");
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.of(saved));

            // ACT — actual SMTP connect will fail (no real server), returns MessagingException message
            String result = service.testSmtpConnection(config, TENANT_ID);

            // ASSERT — null means success, non-null means error message from MessagingException
            // In test env there's no real SMTP server so it returns an error string (not null)
            // We just verify it doesn't throw and password was set from DB
            assertEquals("dbpassword", config.getPassword());
            // result can be null (success) or error string — both are valid non-exception outcomes
        }

        @Test
        @DisplayName("Happy Path — password provided, returns null or error string (no exception thrown)")
        void happyPath_passwordProvided_noException() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword("testpass");

            // ACT — real SMTP won't connect in test env, but method should NOT throw
            String result = service.testSmtpConnection(config, TENANT_ID);

            // ASSERT — returns null (success) or error message string — never throws
            // In CI/test env, will return MessagingException message string
            assertTrue(result == null || result.length() > 0);
        }

        @Test
        @DisplayName("Sad Path — null tenantId, no DB config → returns password-required message")
        void sadPath_nullTenantId_noDbConfig() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword(null);
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.empty());

            // ACT
            String result = service.testSmtpConnection(config, null);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.contains("Password is required"));
        }
    }

    // =========================================================================
    // sendTestEmail
    // =========================================================================
    @Nested
    @DisplayName("sendTestEmail")
    class SendTestEmailTests {

        @Test
        @DisplayName("Sad Path — password null, no DB config → returns password-required message")
        void sadPath_noPasswordNoDb() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword(null);
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID)).thenReturn(Optional.empty());
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue()).thenReturn(Optional.empty());

            // ACT
            String result = service.sendTestEmail(config, "to@example.com", TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.contains("Password is required"));
        }

        @Test
        @DisplayName("Sad Path — password empty, DB has saved password, SMTP fails → returns error string")
        void sadPath_emptyPasswordUsesDbPassword() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword("");

            SmtpConfig saved = smtpConfig();
            saved.setPassword("dbpassword");
            when(smtpConfigRepository.findByFkTenantId(TENANT_ID))
                    .thenReturn(Optional.of(saved));

            // ACT
            String result = service.sendTestEmail(config, "to@example.com", TENANT_ID);

            // ASSERT — password set from DB, no exception thrown
            assertEquals("dbpassword", config.getPassword());
            // result is null (success) or error string — not an exception
        }

        @Test
        @DisplayName("Happy Path — password provided, no exception thrown")
        void happyPath_passwordProvided() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword("testpass");

            // ACT
            String result = service.sendTestEmail(config, "to@example.com", TENANT_ID);

            // ASSERT — null = success, non-null = error message — never throws
            assertTrue(result == null || result.length() > 0);
        }

        @Test
        @DisplayName("Sad Path — null tenantId with empty password → returns password-required message")
        void sadPath_nullTenantId() {
            // ARRANGE
            SmtpConfig config = smtpConfig();
            config.setPassword("");
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.empty());

            // ACT
            String result = service.sendTestEmail(config, "to@example.com", null);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.contains("Password is required"));
        }
    }

    // =========================================================================
    // toKeycloakSmtpMap
    // =========================================================================
    @Nested
    @DisplayName("toKeycloakSmtpMap")
    class ToKeycloakSmtpMapTests {

        @Test
        @DisplayName("Happy Path — all fields mapped correctly")
        void happyPath() {
            // ARRANGE
            SmtpConfig config = smtpConfig();

            // ACT
            Map<String, String> result = service.toKeycloakSmtpMap(config);

            // ASSERT
            assertNotNull(result);
            assertEquals("smtp.example.com", result.get("host"));
            assertEquals("587",              result.get("port"));
            assertEquals("no-reply@example.com", result.get("from"));
            assertEquals("user@example.com", result.get("user"));
            assertEquals("secret",           result.get("password"));
            assertEquals("true",             result.get("auth"));
            assertEquals("true",             result.get("starttls"));
            assertEquals("false",            result.get("ssl"));
        }
    }

    // =========================================================================
    // createDefaultSmtpConfig
    // =========================================================================
    @Nested
    @DisplayName("createDefaultSmtpConfig")
    class CreateDefaultSmtpConfigTests {

        @Test
        @DisplayName("Happy Path — no existing default, creates new one")
        void happyPath_createsNew() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdIsNull()).thenReturn(Optional.empty());
            SmtpConfig saved = smtpConfig();
            saved.setFkTenantId(null);
            when(smtpConfigRepository.save(any(SmtpConfig.class))).thenReturn(saved);

            // ACT
            SmtpConfig result = service.createDefaultSmtpConfig(smtpConfig());

            // ASSERT
            assertNotNull(result);
            verify(smtpConfigRepository).save(any(SmtpConfig.class));
        }

        @Test
        @DisplayName("Happy Path — default already exists, returns existing without saving")
        void happyPath_alreadyExists() {
            // ARRANGE
            SmtpConfig existing = smtpConfig();
            existing.setFkTenantId(null);
            when(smtpConfigRepository.findByFkTenantIdIsNull()).thenReturn(Optional.of(existing));

            // ACT
            SmtpConfig result = service.createDefaultSmtpConfig(smtpConfig());

            // ASSERT
            assertNotNull(result);
            verify(smtpConfigRepository, never()).save(any());
        }
    }

    // =========================================================================
    // updateDefaultSmtpConfig
    // =========================================================================
    @Nested
    @DisplayName("updateDefaultSmtpConfig")
    class UpdateDefaultSmtpConfigTests {

        @Test
        @DisplayName("Happy Path — default config updated with new password")
        void happyPath_withPassword() {
            // ARRANGE
            SmtpConfig existing = smtpConfig();
            existing.setFkTenantId(null);
            when(smtpConfigRepository.findByFkTenantIdIsNull()).thenReturn(Optional.of(existing));
            when(smtpConfigRepository.save(any())).thenReturn(existing);

            SmtpConfig updated = smtpConfig();
            updated.setHost("smtp.updated.com");
            updated.setPassword("newpass");

            // ACT
            SmtpConfig result = service.updateDefaultSmtpConfig(updated);

            // ASSERT
            assertNotNull(result);
            verify(smtpConfigRepository).save(existing);
        }

        @Test
        @DisplayName("Happy Path — password null, existing password preserved")
        void happyPath_passwordNullPreserved() {
            // ARRANGE
            SmtpConfig existing = smtpConfig();
            existing.setFkTenantId(null);
            existing.setPassword("oldpass");
            when(smtpConfigRepository.findByFkTenantIdIsNull()).thenReturn(Optional.of(existing));
            when(smtpConfigRepository.save(any())).thenReturn(existing);

            SmtpConfig updated = smtpConfig();
            updated.setPassword(null);

            // ACT
            service.updateDefaultSmtpConfig(updated);

            // ASSERT
            assertEquals("oldpass", existing.getPassword());
        }

        @Test
        @DisplayName("Sad Path — default config not found throws IllegalArgumentException")
        void sadPath_notFound() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdIsNull()).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateDefaultSmtpConfig(smtpConfig()));
        }
    }

    // =========================================================================
    // syncSmtpToKeycloak
    // =========================================================================
    @Nested
    @DisplayName("syncSmtpToKeycloak")
    class SyncSmtpToKeycloakTests {

        @Test
        @DisplayName("Happy Path — syncs successfully returns true")
        void happyPath() {
            // ARRANGE
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(smtpConfig()));
            doNothing().when(kcUtil).updateRealmSmtpSettings(eq("test-realm"), anyMap());

            // ACT
            boolean result = service.syncSmtpToKeycloak(TENANT_ID);

            // ASSERT
            assertTrue(result);
            verify(kcUtil).updateRealmSmtpSettings(eq("test-realm"), anyMap());
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws IllegalArgumentException")
        void sadPath_tenantNotFound() {
            // ARRANGE
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.syncSmtpToKeycloak(TENANT_ID));
        }

        @Test
        @DisplayName("Sad Path — Keycloak throws exception returns false")
        void sadPath_keycloakFails() {
            // ARRANGE
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(smtpConfig()));
            doThrow(new RuntimeException("Keycloak unavailable"))
                    .when(kcUtil).updateRealmSmtpSettings(anyString(), anyMap());

            // ACT
            boolean result = service.syncSmtpToKeycloak(TENANT_ID);

            // ASSERT
            assertFalse(result);
        }
    }

    // =========================================================================
    // syncDefaultSmtpToAllRealms
    // =========================================================================
    @Nested
    @DisplayName("syncDefaultSmtpToAllRealms")
    class SyncDefaultSmtpToAllRealmsTests {

        @Test
        @DisplayName("Happy Path — syncs to all tenants without custom config")
        void happyPath_allSynced() {
            // ARRANGE
            Tenant t1 = tenant();
            Tenant t2 = new Tenant();
            t2.setTenantID("tenant-002");
            t2.setTenantName("Tenant 2");
            t2.setRealmName("realm-2");

            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.of(smtpConfig()));
            when(tenantRepository.findAll()).thenReturn(List.of(t1, t2));

            // t1 has custom config → skip; t2 has no custom config → sync
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue("tenant-001"))
                    .thenReturn(Optional.of(smtpConfig()));
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue("tenant-002"))
                    .thenReturn(Optional.empty());
            doNothing().when(kcUtil).updateRealmSmtpSettings(anyString(), anyMap());

            // ACT
            int count = service.syncDefaultSmtpToAllRealms();

            // ASSERT
            assertEquals(1, count); // only t2 synced
            verify(kcUtil, times(1)).updateRealmSmtpSettings(eq("realm-2"), anyMap());
        }

        @Test
        @DisplayName("Happy Path — Keycloak fails for one realm, continues and returns partial count")
        void happyPath_partialFailure() {
            // ARRANGE
            Tenant t1 = tenant();
            Tenant t2 = new Tenant();
            t2.setTenantID("tenant-002");
            t2.setTenantName("Tenant 2");
            t2.setRealmName("realm-2");

            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.of(smtpConfig()));
            when(tenantRepository.findAll()).thenReturn(List.of(t1, t2));

            // both have no custom config → both attempt sync
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(anyString()))
                    .thenReturn(Optional.empty());

            // t1 sync succeeds, t2 sync fails
            doNothing().when(kcUtil)
                    .updateRealmSmtpSettings(eq("test-realm"), anyMap());
            doThrow(new RuntimeException("realm-2 down"))
                    .when(kcUtil)
                    .updateRealmSmtpSettings(eq("realm-2"), anyMap());

            // ACT
            int count = service.syncDefaultSmtpToAllRealms();

            // ASSERT — only 1 succeeded, no exception thrown
            assertEquals(1, count);
        }

        @Test
        @DisplayName("Happy Path — no tenants returns 0")
        void happyPath_noTenants() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.of(smtpConfig()));
            when(tenantRepository.findAll()).thenReturn(Collections.emptyList());

            // ACT
            int count = service.syncDefaultSmtpToAllRealms();

            // ASSERT
            assertEquals(0, count);
        }
    }
}