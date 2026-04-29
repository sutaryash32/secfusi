package com.secufusion.events.service;

import com.secufusion.events.entity.ExtensionApiKeyConfiguration;
import com.secufusion.events.repository.ExtensionApiKeyConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtensionApiKeyConfigurationService Tests")
class ExtensionApiKeyConfigurationServiceTest {

    @Mock
    private ExtensionApiKeyConfigurationRepository configRepository;

    @InjectMocks
    private ExtensionApiKeyConfigurationService service;

    // ── Common config keys (mirrors enum names) ───────────────────────────────
    private static final String KEY_EXPIRY_DAYS     =
            ExtensionApiKeyConfiguration.ConfigKey.DEFAULT_EXPIRY_DAYS.name();
    private static final String KEY_WARNING_DAYS    =
            ExtensionApiKeyConfiguration.ConfigKey.EXPIRY_WARNING_THRESHOLD_DAYS.name();
    private static final String KEY_AUTO_DISABLE    =
            ExtensionApiKeyConfiguration.ConfigKey.AUTO_DISABLE_AFTER_EXPIRY.name();
    private static final String KEY_ALLOW_EXTENSION =
            ExtensionApiKeyConfiguration.ConfigKey.ALLOW_EXPIRY_EXTENSION.name();
    private static final String KEY_MAX_EXTENSION   =
            ExtensionApiKeyConfiguration.ConfigKey.MAX_EXPIRY_EXTENSION_DAYS.name();
    private static final String KEY_MAX_KEYS        =
            ExtensionApiKeyConfiguration.ConfigKey.MAX_KEYS_PER_TENANT.name();
    private static final String KEY_RATE_LIMIT      =
            ExtensionApiKeyConfiguration.ConfigKey.DEFAULT_RATE_LIMIT.name();

    // ── Helper: build a config entity ─────────────────────────────────────────
    private ExtensionApiKeyConfiguration buildConfig(String key, String value, boolean editable) {
        ExtensionApiKeyConfiguration cfg = new ExtensionApiKeyConfiguration();
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        cfg.setIsEditable(editable);
        cfg.setDescription("desc-" + key);
        return cfg;
    }

    // =========================================================================
    // initializeDefaultConfigurations  (@PostConstruct)
    // =========================================================================

    @Nested
    @DisplayName("initializeDefaultConfigurations")
    class InitializeDefaultConfigurations {

        @Test
        @DisplayName("Happy Path — missing keys are created with defaults")
        void happyPath_missingKeys_createdWithDefaults() {
            // ARRANGE — none of the keys exist yet
            when(configRepository.existsByConfigKey(anyString())).thenReturn(false);
            when(configRepository.save(any(ExtensionApiKeyConfiguration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ACT
            service.initializeDefaultConfigurations();

            // ASSERT — one save per ConfigKey enum value
            int enumCount = ExtensionApiKeyConfiguration.ConfigKey.values().length;
            verify(configRepository, times(enumCount))
                    .save(any(ExtensionApiKeyConfiguration.class));
        }

        @Test
        @DisplayName("Happy Path — already-existing keys are skipped (no duplicate saves)")
        void happyPath_existingKeys_notSavedAgain() {
            // ARRANGE — all keys already exist
            when(configRepository.existsByConfigKey(anyString())).thenReturn(true);

            // ACT
            service.initializeDefaultConfigurations();

            // ASSERT — nothing saved
            verify(configRepository, never()).save(any());
        }

        @Test
        @DisplayName("Happy Path — partial init: existing keys skipped, missing ones created")
        void happyPath_partialInit_missingOnesCreated() {
            // ARRANGE — stub each key individually: DEFAULT_EXPIRY_DAYS exists, all others do not
            for (ExtensionApiKeyConfiguration.ConfigKey key :
                    ExtensionApiKeyConfiguration.ConfigKey.values()) {
                boolean exists = key == ExtensionApiKeyConfiguration.ConfigKey.DEFAULT_EXPIRY_DAYS;
                when(configRepository.existsByConfigKey(key.name())).thenReturn(exists);
            }
            when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            service.initializeDefaultConfigurations();

            // ASSERT — saved for every key except DEFAULT_EXPIRY_DAYS
            int expectedSaves = ExtensionApiKeyConfiguration.ConfigKey.values().length - 1;
            verify(configRepository, times(expectedSaves)).save(any());
        }
        @Test
        @DisplayName("Happy Path — saved config carries correct key, value and description")
        void happyPath_savedConfigHasCorrectFields() {
            // ARRANGE
            when(configRepository.existsByConfigKey(anyString())).thenReturn(false);
            when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            service.initializeDefaultConfigurations();

            // ASSERT — capture all saves and verify one of them (DEFAULT_EXPIRY_DAYS)
            ArgumentCaptor<ExtensionApiKeyConfiguration> captor =
                    ArgumentCaptor.forClass(ExtensionApiKeyConfiguration.class);
            verify(configRepository, atLeastOnce()).save(captor.capture());

            ExtensionApiKeyConfiguration expiryDaysConfig = captor.getAllValues().stream()
                    .filter(c -> KEY_EXPIRY_DAYS.equals(c.getConfigKey()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(KEY_EXPIRY_DAYS, expiryDaysConfig.getConfigKey());
            assertNotNull(expiryDaysConfig.getConfigValue());
            assertNotNull(expiryDaysConfig.getDescription());
            assertTrue(expiryDaysConfig.getIsEditable());
        }
    }

    // =========================================================================
    // getConfigValue
    // =========================================================================

    @Nested
    @DisplayName("getConfigValue")
    class GetConfigValue {

        @Test
        @DisplayName("Happy Path — key found returns stored value")
        void happyPath_keyFound_returnsStoredValue() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.of(buildConfig(KEY_EXPIRY_DAYS, "120", true)));

            // ACT
            String result = service.getConfigValue(KEY_EXPIRY_DAYS, "90");

            // ASSERT
            assertEquals("120", result);
            verify(configRepository).findByConfigKey(KEY_EXPIRY_DAYS);
        }

        @Test
        @DisplayName("Sad Path — key not found returns supplied default")
        void sadPath_keyNotFound_returnsDefault() {
            // ARRANGE
            when(configRepository.findByConfigKey(anyString()))
                    .thenReturn(Optional.empty());

            // ACT
            String result = service.getConfigValue("MISSING_KEY", "fallback");

            // ASSERT
            assertEquals("fallback", result);
        }
    }

    // =========================================================================
    // getConfigValueAsInt
    // =========================================================================

    @Nested
    @DisplayName("getConfigValueAsInt")
    class GetConfigValueAsInt {

        @Test
        @DisplayName("Happy Path — valid integer string parsed correctly")
        void happyPath_validInteger_parsed() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.of(buildConfig(KEY_EXPIRY_DAYS, "180", true)));

            // ACT
            Integer result = service.getConfigValueAsInt(KEY_EXPIRY_DAYS, 90);

            // ASSERT
            assertEquals(180, result);
        }

        @Test
        @DisplayName("Happy Path — key not found returns default integer")
        void happyPath_keyNotFound_returnsDefaultInt() {
            // ARRANGE
            when(configRepository.findByConfigKey(anyString()))
                    .thenReturn(Optional.empty());

            // ACT
            Integer result = service.getConfigValueAsInt("MISSING_KEY", 42);

            // ASSERT
            assertEquals(42, result);
        }

        @Test
        @DisplayName("Sad Path — non-numeric value returns default (NumberFormatException caught)")
        void sadPath_nonNumericValue_returnsDefault() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.of(buildConfig(KEY_EXPIRY_DAYS, "not-a-number", true)));

            // ACT
            Integer result = service.getConfigValueAsInt(KEY_EXPIRY_DAYS, 99);

            // ASSERT — catch block returns the default
            assertEquals(99, result);
        }
    }

    // =========================================================================
    // getConfigValueAsBoolean
    // =========================================================================

    @Nested
    @DisplayName("getConfigValueAsBoolean")
    class GetConfigValueAsBoolean {

        @Test
        @DisplayName("Happy Path — 'true' string parsed as Boolean.TRUE")
        void happyPath_trueString_parsedAsTrue() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_AUTO_DISABLE))
                    .thenReturn(Optional.of(buildConfig(KEY_AUTO_DISABLE, "true", true)));

            // ACT
            Boolean result = service.getConfigValueAsBoolean(KEY_AUTO_DISABLE, false);

            // ASSERT
            assertTrue(result);
        }

        @Test
        @DisplayName("Happy Path — 'false' string parsed as Boolean.FALSE")
        void happyPath_falseString_parsedAsFalse() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_ALLOW_EXTENSION))
                    .thenReturn(Optional.of(buildConfig(KEY_ALLOW_EXTENSION, "false", true)));

            // ACT
            Boolean result = service.getConfigValueAsBoolean(KEY_ALLOW_EXTENSION, true);

            // ASSERT
            assertFalse(result);
        }

        @Test
        @DisplayName("Sad Path — key not found returns supplied default Boolean")
        void sadPath_keyNotFound_returnsDefault() {
            // ARRANGE
            when(configRepository.findByConfigKey(anyString()))
                    .thenReturn(Optional.empty());

            // ACT
            Boolean result = service.getConfigValueAsBoolean("MISSING_KEY", true);

            // ASSERT
            assertTrue(result);
        }
    }

    // =========================================================================
    // getAllConfigurations
    // =========================================================================

    @Nested
    @DisplayName("getAllConfigurations")
    class GetAllConfigurations {

        @Test
        @DisplayName("Happy Path — all stored configs returned as key-value map")
        void happyPath_returnsAllAsMap() {
            // ARRANGE
            List<ExtensionApiKeyConfiguration> stored = new ArrayList<>();
            stored.add(buildConfig(KEY_EXPIRY_DAYS,  "90",   true));
            stored.add(buildConfig(KEY_WARNING_DAYS, "7",    true));
            stored.add(buildConfig(KEY_AUTO_DISABLE, "true", true));
            when(configRepository.findAll()).thenReturn(stored);

            // ACT
            Map<String, String> result = service.getAllConfigurations();

            // ASSERT
            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("90",   result.get(KEY_EXPIRY_DAYS));
            assertEquals("7",    result.get(KEY_WARNING_DAYS));
            assertEquals("true", result.get(KEY_AUTO_DISABLE));
            verify(configRepository).findAll();
        }

        @Test
        @DisplayName("Happy Path — empty repository returns empty map")
        void happyPath_emptyRepo_returnsEmptyMap() {
            // ARRANGE
            when(configRepository.findAll()).thenReturn(new ArrayList<>());

            // ACT
            Map<String, String> result = service.getAllConfigurations();

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // updateConfiguration
    // =========================================================================

    @Nested
    @DisplayName("updateConfiguration")
    class UpdateConfiguration {

        @Test
        @DisplayName("Happy Path — editable config updated and saved")
        void happyPath_editableConfig_updatedAndSaved() {
            // ARRANGE
            ExtensionApiKeyConfiguration cfg = buildConfig(KEY_EXPIRY_DAYS, "90", true);
            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.of(cfg));
            when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            service.updateConfiguration(KEY_EXPIRY_DAYS, "180", "admin-user");

            // ASSERT
            assertEquals("180",        cfg.getConfigValue());
            assertEquals("admin-user", cfg.getUpdatedBy());
            verify(configRepository).save(cfg);
        }

        @Test
        @DisplayName("Sad Path — key not found throws IllegalArgumentException")
        void sadPath_keyNotFound_throwsIllegalArgument() {
            // ARRANGE
            when(configRepository.findByConfigKey(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateConfiguration("MISSING_KEY", "value", "user"));
            verify(configRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — non-editable config throws IllegalStateException")
        void sadPath_nonEditableConfig_throwsIllegalState() {
            // ARRANGE
            ExtensionApiKeyConfiguration cfg = buildConfig(KEY_EXPIRY_DAYS, "90", false);
            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.of(cfg));

            // ACT + ASSERT
            assertThrows(IllegalStateException.class,
                    () -> service.updateConfiguration(KEY_EXPIRY_DAYS, "180", "admin"));
            verify(configRepository, never()).save(any());
        }
    }

    // =========================================================================
    // updateConfigurations (batch)
    // =========================================================================

    @Nested
    @DisplayName("updateConfigurations (batch)")
    class UpdateConfigurations {

        @Test
        @DisplayName("Happy Path — multiple keys updated via forEach")
        void happyPath_multipleKeysUpdated() {
            // ARRANGE
            ExtensionApiKeyConfiguration cfgExpiry   = buildConfig(KEY_EXPIRY_DAYS,  "90",  true);
            ExtensionApiKeyConfiguration cfgWarning  = buildConfig(KEY_WARNING_DAYS, "7",   true);

            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.of(cfgExpiry));
            when(configRepository.findByConfigKey(KEY_WARNING_DAYS))
                    .thenReturn(Optional.of(cfgWarning));
            when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> updates = Map.of(
                    KEY_EXPIRY_DAYS,  "120",
                    KEY_WARNING_DAYS, "14"
            );

            // ACT
            service.updateConfigurations(updates, "batch-admin");

            // ASSERT
            assertEquals("120", cfgExpiry.getConfigValue());
            assertEquals("14",  cfgWarning.getConfigValue());
            verify(configRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("Sad Path — one key not found causes IllegalArgumentException")
        void sadPath_oneKeyMissing_throwsException() {
            // ARRANGE
            when(configRepository.findByConfigKey(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateConfigurations(
                            Map.of("MISSING_KEY", "value"), "user"));
        }
    }

    // =========================================================================
    // Convenience delegates — getDefaultExpiryDays etc.
    // =========================================================================

    @Nested
    @DisplayName("Convenience delegate methods")
    class ConvenienceDelegates {

        @Test
        @DisplayName("getDefaultExpiryDays — stored value returned")
        void getDefaultExpiryDays_storedValueReturned() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.of(buildConfig(KEY_EXPIRY_DAYS, "60", true)));

            // ACT + ASSERT
            assertEquals(60, service.getDefaultExpiryDays());
        }

        @Test
        @DisplayName("getDefaultExpiryDays — missing key returns hardcoded default 90")
        void getDefaultExpiryDays_missingKey_returns90() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_EXPIRY_DAYS))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertEquals(90, service.getDefaultExpiryDays());
        }

        @Test
        @DisplayName("getExpiryWarningThresholdDays — stored value returned")
        void getExpiryWarningThresholdDays_storedValueReturned() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_WARNING_DAYS))
                    .thenReturn(Optional.of(buildConfig(KEY_WARNING_DAYS, "14", true)));

            // ACT + ASSERT
            assertEquals(14, service.getExpiryWarningThresholdDays());
        }

        @Test
        @DisplayName("getExpiryWarningThresholdDays — missing key returns hardcoded default 7")
        void getExpiryWarningThresholdDays_missingKey_returns7() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_WARNING_DAYS))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertEquals(7, service.getExpiryWarningThresholdDays());
        }

        @Test
        @DisplayName("isAutoDisableAfterExpiry — 'true' value returns true")
        void isAutoDisableAfterExpiry_trueValue() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_AUTO_DISABLE))
                    .thenReturn(Optional.of(buildConfig(KEY_AUTO_DISABLE, "true", true)));

            // ACT + ASSERT
            assertTrue(service.isAutoDisableAfterExpiry());
        }

        @Test
        @DisplayName("isAutoDisableAfterExpiry — missing key returns hardcoded default true")
        void isAutoDisableAfterExpiry_missingKey_returnsTrue() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_AUTO_DISABLE))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertTrue(service.isAutoDisableAfterExpiry());
        }

        @Test
        @DisplayName("isExpiryExtensionAllowed — 'false' value returns false")
        void isExpiryExtensionAllowed_falseValue() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_ALLOW_EXTENSION))
                    .thenReturn(Optional.of(buildConfig(KEY_ALLOW_EXTENSION, "false", true)));

            // ACT + ASSERT
            assertFalse(service.isExpiryExtensionAllowed());
        }

        @Test
        @DisplayName("isExpiryExtensionAllowed — missing key returns hardcoded default false")
        void isExpiryExtensionAllowed_missingKey_returnsFalse() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_ALLOW_EXTENSION))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertFalse(service.isExpiryExtensionAllowed());
        }

        @Test
        @DisplayName("getMaxExpiryExtensionDays — stored value returned")
        void getMaxExpiryExtensionDays_storedValueReturned() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_MAX_EXTENSION))
                    .thenReturn(Optional.of(buildConfig(KEY_MAX_EXTENSION, "180", true)));

            // ACT + ASSERT
            assertEquals(180, service.getMaxExpiryExtensionDays());
        }

        @Test
        @DisplayName("getMaxExpiryExtensionDays — missing key returns hardcoded default 365")
        void getMaxExpiryExtensionDays_missingKey_returns365() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_MAX_EXTENSION))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertEquals(365, service.getMaxExpiryExtensionDays());
        }

        @Test
        @DisplayName("getMaxKeysPerTenant — stored value returned")
        void getMaxKeysPerTenant_storedValueReturned() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_MAX_KEYS))
                    .thenReturn(Optional.of(buildConfig(KEY_MAX_KEYS, "50", true)));

            // ACT + ASSERT
            assertEquals(50, service.getMaxKeysPerTenant());
        }

        @Test
        @DisplayName("getMaxKeysPerTenant — missing key returns hardcoded default 0 (unlimited)")
        void getMaxKeysPerTenant_missingKey_returns0() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_MAX_KEYS))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertEquals(0, service.getMaxKeysPerTenant());
        }

        @Test
        @DisplayName("getDefaultRateLimit — stored value returned")
        void getDefaultRateLimit_storedValueReturned() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_RATE_LIMIT))
                    .thenReturn(Optional.of(buildConfig(KEY_RATE_LIMIT, "1000", true)));

            // ACT + ASSERT
            assertEquals(1000, service.getDefaultRateLimit());
        }

        @Test
        @DisplayName("getDefaultRateLimit — missing key returns hardcoded default 0 (unlimited)")
        void getDefaultRateLimit_missingKey_returns0() {
            // ARRANGE
            when(configRepository.findByConfigKey(KEY_RATE_LIMIT))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertEquals(0, service.getDefaultRateLimit());
        }
    }

    // =========================================================================
    // getConfigDescription — all switch branches via initializeDefaultConfigurations
    // =========================================================================

    @Nested
    @DisplayName("getConfigDescription — all switch branches")
    class GetConfigDescription {

        @Test
        @DisplayName("All ConfigKey enum values produce non-null descriptions on init")
        void allConfigKeys_haveNonNullDescriptions() {
            // ARRANGE — allow all saves so every enum key passes through getConfigDescription
            when(configRepository.existsByConfigKey(anyString())).thenReturn(false);
            when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            service.initializeDefaultConfigurations();

            // ASSERT — capture and verify description is set for every saved entity
            ArgumentCaptor<ExtensionApiKeyConfiguration> captor =
                    ArgumentCaptor.forClass(ExtensionApiKeyConfiguration.class);
            verify(configRepository, atLeastOnce()).save(captor.capture());

            captor.getAllValues().forEach(cfg -> {
                assertNotNull(cfg.getDescription(),
                        "Description must not be null for key: " + cfg.getConfigKey());
                assertFalse(cfg.getDescription().isBlank(),
                        "Description must not be blank for key: " + cfg.getConfigKey());
            });
        }
    }
}