package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExtensionSyncService Tests")
class ExtensionSyncServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private InstalledExtensionRepository installedExtensionRepository;
    @Mock private ExtensionEventRepository extensionEventRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ExtensionSyncService service;

    private static final String TENANT_ID    = "tenant-001";
    private static final String DEVICE_ID    = "device-001";
    private static final String DEVICE_TOKEN = "token-abc";
    private static final String CLIENT_IP    = "192.168.1.1";
    private static final String USER_ID      = "user-001";
    private static final String USER_NAME    = "Test User";

    private Tenant testTenant;
    private Device testDevice;

    // ── helpers to avoid builder dependencies ──────────────────────
    private Tenant createTestTenant() {
        Tenant t = new Tenant();
        t.setTenantID(TENANT_ID);
        t.setTenantCode("TENANT-CODE");
        return t;
    }

    private Device createTestDevice() {
        Device d = new Device();
        d.setDeviceId(DEVICE_ID);
        d.setTenantId(TENANT_ID);
        d.setDeviceToken(DEVICE_TOKEN);
        d.setIsAnonymous(false);
        d.setStatus(DeviceStatus.ACTIVE);
        d.setUserName(null);  // explicitly null
        return d;
    }

    @BeforeEach
    void setUp() {
        testTenant = createTestTenant();
        testDevice = createTestDevice();

        // lenient stubs to avoid UnnecessaryStubbingException
        lenient().doNothing().when(entityManager).flush();
        lenient().doNothing().when(entityManager).refresh(any());
    }

    // ── build request helpers ──────────────────────────────────────
    private PublicExtensionSyncRequest buildPublicRequest() {
        PublicExtensionSyncRequest req = new PublicExtensionSyncRequest();
        req.setTenantCode("TENANT-CODE");
        req.setUserEmail("user@example.com");
        req.setDeviceFingerprint("fp-123");
        req.setBrowserType("Chrome");
        req.setExtensionVersion("1.0");
        req.setUserAgent("Mozilla/5.0 (Windows NT 10.0)");
        req.setOsInfo("Windows 10");
        req.setDeviceName("TestPC");
        req.setExtensions(new ArrayList<>());
        return req;
    }

    private ExtensionSyncRequest.ExtensionInfo buildExtInfo(String id, String name, String version) {
        ExtensionSyncRequest.ExtensionInfo info = new ExtensionSyncRequest.ExtensionInfo();
        info.setExtensionId(id);
        info.setName(name);
        info.setVersion(version);
        info.setPermissions(Collections.emptyList());
        info.setHostPermissions(Collections.emptyList());
        return info;
    }

    // =================== syncExtensionsPublic =======================
    @Nested
    @DisplayName("syncExtensionsPublic")
    class SyncExtensionsPublic {

        @Test
        @DisplayName("Happy Path — tenant by code, returning device")
        void happyPath_tenantByCode_returningDevice() {
            // ARRANGE
            PublicExtensionSyncRequest request = buildPublicRequest();
            request.setDeviceToken(DEVICE_TOKEN);
            when(tenantRepository.findByTenantCodeIgnoreCase("TENANT-CODE"))
                    .thenReturn(Optional.of(testTenant));
            when(deviceRepository.findByDeviceTokenAndTenantId(DEVICE_TOKEN, TENANT_ID))
                    .thenReturn(Optional.of(testDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(testDevice);

            when(installedExtensionRepository.findActiveExtensionIds(anyString()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.findByDeviceDeviceIdAndExtensionIdIn(anyString(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            ExtensionSyncResponse result = service.syncExtensionsPublic(request, CLIENT_IP);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getSuccess());               // fix: getSuccess()
            assertEquals(DEVICE_TOKEN, result.getDeviceToken());
            verify(tenantRepository).findByTenantCodeIgnoreCase("TENANT-CODE");
            verify(deviceRepository).save(any(Device.class));
        }

        @Test
        @DisplayName("Happy Path — tenant by email domain, new device")
        void happyPath_tenantByDomain_newDevice() {
            // ARRANGE
            PublicExtensionSyncRequest request = buildPublicRequest();
            request.setTenantCode(null);                       // force domain lookup
            request.setUserEmail("user@example.com");          // getEmailDomain() → "example.com"
            when(tenantRepository.findByTenantCodeIgnoreCase(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomainIgnoreCase("example.com"))
                    .thenReturn(Optional.of(testTenant));
            when(deviceRepository.findByDeviceFingerprintAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // Simulate device save: assign new ID and token
            Device savedDevice = new Device();
            savedDevice.setDeviceId("new-device");
            savedDevice.setDeviceToken("new-token");
            savedDevice.setIsAnonymous(true);
            when(deviceRepository.save(any(Device.class))).thenReturn(savedDevice);

            when(installedExtensionRepository.findActiveExtensionIds(anyString()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.findByDeviceDeviceIdAndExtensionIdIn(anyString(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            ExtensionSyncResponse result = service.syncExtensionsPublic(request, CLIENT_IP);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getSuccess());               // fix: getSuccess()
            assertNull(result.getDeviceId());              // anonymous device hides ID
            assertNotNull(result.getDeviceToken());
        }

        @Test
        @DisplayName("Sad Path — tenant not found for code")
        void sadPath_tenantNotFoundForCode() {
            // ARRANGE
            PublicExtensionSyncRequest request = buildPublicRequest();
            when(tenantRepository.findByTenantCodeIgnoreCase("TENANT-CODE"))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.syncExtensionsPublic(request, CLIENT_IP));
        }

        @Test
        @DisplayName("Sad Path — tenant not found for email domain")
        void sadPath_tenantNotFoundForDomain() {
            // ARRANGE
            PublicExtensionSyncRequest request = buildPublicRequest();
            request.setTenantCode(null);
            request.setUserEmail("user@unknown.com");
            when(tenantRepository.findByTenantCodeIgnoreCase(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomainIgnoreCase("unknown.com")).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.syncExtensionsPublic(request, CLIENT_IP));
        }

        @Test
        @DisplayName("Sad Path — neither tenant code nor email provided")
        void sadPath_noTenantIdentifier() {
            // ARRANGE
            PublicExtensionSyncRequest request = new PublicExtensionSyncRequest();
            request.setTenantCode(null);
            request.setUserEmail(null);         // no email domain

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.syncExtensionsPublic(request, CLIENT_IP));
        }
    }

    // =================== syncExtensionsAuthenticated =================
    @Nested
    @DisplayName("syncExtensionsAuthenticated")
    class SyncExtensionsAuthenticated {

        @Test
        @DisplayName("Happy Path — existing device by ID, link anonymous")
        void happyPath_existingDeviceById_linkAnonymous() {
            // ARRANGE
            ExtensionSyncRequest request = new ExtensionSyncRequest();
            request.setDeviceId(DEVICE_ID);
            request.setExtensions(new ArrayList<>());

            Device anonymousDevice = createTestDevice();
            anonymousDevice.setIsAnonymous(true);
            anonymousDevice.setLinkedUserId(null);

            when(deviceRepository.findByDeviceIdAndTenantId(DEVICE_ID, TENANT_ID))
                    .thenReturn(Optional.of(anonymousDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(anonymousDevice);

            when(installedExtensionRepository.findActiveExtensionIds(anyString()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.findByDeviceDeviceIdAndExtensionIdIn(anyString(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
            when(installedExtensionRepository.findByDeviceDeviceIdOrderByExtensionNameAsc(anyString()))
                    .thenReturn(Collections.emptyList());

            // ACT
            ExtensionSyncResponse result = service.syncExtensionsAuthenticated(
                    request, TENANT_ID, USER_ID, USER_NAME, CLIENT_IP);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getSuccess());               // fix: getSuccess()
            assertEquals(DEVICE_ID, result.getDeviceId());
            verify(deviceRepository, atLeastOnce()).save(any(Device.class));
            verify(installedExtensionRepository).findByDeviceDeviceIdOrderByExtensionNameAsc(DEVICE_ID);
        }

        @Test
        @DisplayName("Happy Path — new authenticated device created")
        void happyPath_newDevice() {
            // ARRANGE
            ExtensionSyncRequest request = new ExtensionSyncRequest();
            request.setDeviceId(null);
            request.setDeviceToken(null);
            request.setExtensions(new ArrayList<>());

            Device newDevice = new Device();
            newDevice.setDeviceId("new-authenticated");
            newDevice.setDeviceToken("auth-token");
            when(deviceRepository.save(any(Device.class))).thenReturn(newDevice);

            when(installedExtensionRepository.findActiveExtensionIds(anyString()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.findByDeviceDeviceIdAndExtensionIdIn(anyString(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            ExtensionSyncResponse result = service.syncExtensionsAuthenticated(
                    request, TENANT_ID, USER_ID, USER_NAME, CLIENT_IP);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getSuccess());               // fix: getSuccess()
            assertEquals("new-authenticated", result.getDeviceId());
            assertNotNull(result.getDeviceToken());
        }
    }

    // =================== registerAnonymousDevice =====================
    @Nested
    @DisplayName("registerAnonymousDevice")
    class RegisterAnonymousDevice {

        @Test
        @DisplayName("Happy Path — tenant by code, new fingerprint")
        void happyPath_newDevice() {
            // ARRANGE
            AnonymousDeviceRegistrationRequest req = new AnonymousDeviceRegistrationRequest();
            req.setTenantCode("TENANT-CODE");
            req.setDeviceFingerprint("new-fp");
            req.setUserAgent("Mozilla/5.0 (Windows NT 10.0)");
            req.setExtensionVersion("1.0");
            req.setIpAddress(CLIENT_IP);
            when(tenantRepository.findByTenantCodeIgnoreCase("TENANT-CODE"))
                    .thenReturn(Optional.of(testTenant));
            when(deviceRepository.findByDeviceFingerprintAndTenantId("new-fp", TENANT_ID))
                    .thenReturn(Optional.empty());

            Device anonDevice = new Device();
            anonDevice.setDeviceId("anon-device");
            anonDevice.setDeviceToken("anon-token");
            anonDevice.setIsAnonymous(true);
            when(deviceRepository.save(any(Device.class))).thenReturn(anonDevice);

            // ACT
            AnonymousDeviceResponse result = service.registerAnonymousDevice(req);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getSuccess());
            assertNotNull(result.getDeviceToken());
            assertEquals("anon-token", result.getDeviceToken());
            assertTrue(result.getIsNewDevice());   // new device → isNewDevice is true
        }

        @Test
        @DisplayName("Happy Path — existing device by fingerprint reused")
        void happyPath_existingDevice() {
            // ARRANGE
            AnonymousDeviceRegistrationRequest req = new AnonymousDeviceRegistrationRequest();
            req.setTenantCode("TENANT-CODE");
            req.setDeviceFingerprint("fp-123");
            testDevice.setDeviceFingerprint("fp-123");

            when(tenantRepository.findByTenantCodeIgnoreCase("TENANT-CODE"))
                    .thenReturn(Optional.of(testTenant));
            when(deviceRepository.findByDeviceFingerprintAndTenantId("fp-123", TENANT_ID))
                    .thenReturn(Optional.of(testDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(testDevice);

            // ACT
            AnonymousDeviceResponse result = service.registerAnonymousDevice(req);

            // ASSERT
            assertNotNull(result);
            assertEquals(DEVICE_ID, result.getDeviceId());
            // existing device may or may not be anonymous depending on fromEntity logic
            // just check that the response is not null
            assertNotNull(result.getDeviceToken());
        }

        @Test
        @DisplayName("Sad Path — no tenant identifier")
        void sadPath_noTenantIdentifier() {
            // ARRANGE
            AnonymousDeviceRegistrationRequest req = new AnonymousDeviceRegistrationRequest();

            // ACT + ASSERT
            assertThrows(IllegalArgumentException.class,
                    () -> service.registerAnonymousDevice(req));
        }
    }

    // =================== syncExtensionsAnonymous =====================
    @Nested
    @DisplayName("syncExtensionsAnonymous")
    class SyncExtensionsAnonymous {

        @Test
        @DisplayName("Happy Path — found device, sync with extensions")
        void happyPath() {
            // ARRANGE
            ExtensionSyncRequest request = new ExtensionSyncRequest();
            request.setDeviceToken(DEVICE_TOKEN);
            request.setExtensions(new ArrayList<>());
            when(deviceRepository.findByDeviceToken(DEVICE_TOKEN))
                    .thenReturn(Optional.of(testDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(testDevice);

            when(installedExtensionRepository.findActiveExtensionIds(anyString()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.findByDeviceDeviceIdAndExtensionIdIn(anyString(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(installedExtensionRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            ExtensionSyncResponse result = service.syncExtensionsAnonymous(request);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getSuccess());               // fix: getSuccess()
            verify(deviceRepository).save(any(Device.class));
        }

        @Test
        @DisplayName("Sad Path — device not found by token")
        void sadPath_deviceNotFound() {
            // ARRANGE
            ExtensionSyncRequest request = new ExtensionSyncRequest();
            request.setDeviceToken("bad-token");
            when(deviceRepository.findByDeviceToken("bad-token")).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.syncExtensionsAnonymous(request));
        }
    }

    // =================== processHeartbeat ============================
    @Nested
    @DisplayName("processHeartbeat")
    class ProcessHeartbeat {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            when(deviceRepository.findByDeviceToken(DEVICE_TOKEN)).thenReturn(Optional.of(testDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(testDevice);

            // ACT
            ExtensionSyncService.HeartbeatResult result = service.processHeartbeat(DEVICE_TOKEN, "1.0.1");

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getSuccess());
            assertEquals(300, result.getSyncIntervalSeconds());
            verify(deviceRepository).save(any(Device.class));
            assertEquals("1.0.1", testDevice.getExtensionVersion());
        }

        @Test
        @DisplayName("Sad Path — device not found")
        void sadPath_deviceNotFound() {
            // ARRANGE
            when(deviceRepository.findByDeviceToken(anyString())).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.processHeartbeat("bad-token", "1.0"));
        }
    }

    // =================== linkAnonymousDeviceToUser ===================
    @Nested
    @DisplayName("linkAnonymousDeviceToUser")
    class LinkAnonymousDeviceToUser {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            testDevice.setIsAnonymous(true);
            when(deviceRepository.findByDeviceTokenAndTenantId(DEVICE_TOKEN, TENANT_ID))
                    .thenReturn(Optional.of(testDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(testDevice);
            when(installedExtensionRepository.findByDeviceDeviceIdOrderByExtensionNameAsc(anyString()))
                    .thenReturn(Collections.emptyList());

            // ACT
            DeviceResponse result = service.linkAnonymousDeviceToUser(
                    DEVICE_TOKEN, TENANT_ID, USER_ID, USER_NAME);

            // ASSERT
            assertNotNull(result);
            assertEquals(DEVICE_ID, result.getDeviceId());
            verify(deviceRepository).save(any(Device.class));
        }

        @Test
        @DisplayName("Sad Path — device not anonymous")
        void sadPath_deviceNotAnonymous() {
            // ARRANGE
            testDevice.setIsAnonymous(false);
            when(deviceRepository.findByDeviceTokenAndTenantId(DEVICE_TOKEN, TENANT_ID))
                    .thenReturn(Optional.of(testDevice));

            // ACT + ASSERT
            assertThrows(IllegalStateException.class,
                    () -> service.linkAnonymousDeviceToUser(DEVICE_TOKEN, TENANT_ID, USER_ID, USER_NAME));
        }

        @Test
        @DisplayName("Sad Path — device not found")
        void sadPath_deviceNotFound() {
            // ARRANGE
            when(deviceRepository.findByDeviceTokenAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.linkAnonymousDeviceToUser(DEVICE_TOKEN, TENANT_ID, USER_ID, USER_NAME));
        }
    }

    // =================== acknowledgeWarning =========================
    @Nested
    @DisplayName("acknowledgeWarning")
    class AcknowledgeWarning {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            InstalledExtension ext = new InstalledExtension();
            ext.setDevice(testDevice);
            ext.setExtensionId("ext-1");
            ext.setExtensionName("Test Ext");
            ext.setPolicyAction("WARN");
            when(installedExtensionRepository.findByDeviceDeviceIdAndExtensionId(DEVICE_ID, "ext-1"))
                    .thenReturn(Optional.of(ext));
            when(extensionEventRepository.save(any(ExtensionEvent.class))).thenReturn(null);

            // ACT + ASSERT (no exception expected)
            assertDoesNotThrow(() -> service.acknowledgeWarning(
                    TENANT_ID, DEVICE_ID, "ext-1", USER_ID, USER_NAME, "ALLOW", "Safe"));

            verify(extensionEventRepository).save(any(ExtensionEvent.class));
        }

        @Test
        @DisplayName("Sad Path — extension not found")
        void sadPath_extensionNotFound() {
            // ARRANGE
            when(installedExtensionRepository.findByDeviceDeviceIdAndExtensionId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.acknowledgeWarning(
                            TENANT_ID, DEVICE_ID, "ext-1", USER_ID, USER_NAME, "ALLOW", "Safe"));
        }
    }

    // =================== searchExtensions ============================
    @Nested
    @DisplayName("searchExtensions")
    class SearchExtensions {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            Page<InstalledExtension> page = new PageImpl<>(Collections.emptyList());
            when(installedExtensionRepository.searchExtensions(eq(TENANT_ID), eq("test"), any(PageRequest.class)))
                    .thenReturn(page);

            // ACT
            Page<InstalledExtensionDto> result = service.searchExtensions(TENANT_ID, "test", 0, 10);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =================== getAllExtensions ============================
    @Nested
    @DisplayName("getAllExtensions")
    class GetAllExtensions {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            Page<InstalledExtension> page = new PageImpl<>(Collections.emptyList());
            when(installedExtensionRepository.findByTenantIdOrderByLastSeenAtDesc(eq(TENANT_ID), any(PageRequest.class)))
                    .thenReturn(page);

            // ACT
            Page<InstalledExtensionDto> result = service.getAllExtensions(TENANT_ID, 0, 10);

            // ASSERT
            assertNotNull(result);
        }
    }

    // =================== getDeviceExtensions =========================
    @Nested
    @DisplayName("getDeviceExtensions")
    class GetDeviceExtensions {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            when(installedExtensionRepository.findByDeviceDeviceIdOrderByExtensionNameAsc(DEVICE_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<InstalledExtensionDto> result = service.getDeviceExtensions(DEVICE_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =================== getHighRiskExtensions =======================
    @Nested
    @DisplayName("getHighRiskExtensions")
    class GetHighRiskExtensions {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            when(installedExtensionRepository.findHighRiskExtensions(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<InstalledExtensionDto> result = service.getHighRiskExtensions(TENANT_ID);

            // ASSERT
            assertNotNull(result);
        }
    }

    // =================== getBlockedExtensions ========================
    @Nested
    @DisplayName("getBlockedExtensions")
    class GetBlockedExtensions {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            when(installedExtensionRepository.findBlockedExtensions(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<InstalledExtensionDto> result = service.getBlockedExtensions(TENANT_ID);

            // ASSERT
            assertNotNull(result);
        }
    }

    // =================== getExtensionStats ===========================
    @Nested
    @DisplayName("getExtensionStats")
    class GetExtensionStats {

        @Test
        @DisplayName("Happy Path — includes risk distribution and common extensions")
        void happyPath() {
            // ARRANGE
            when(installedExtensionRepository.countUniqueExtensions(TENANT_ID)).thenReturn(120L);
            when(installedExtensionRepository.countHighRiskExtensions(TENANT_ID)).thenReturn(8L);
            when(installedExtensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "BLOCK")).thenReturn(5L);

            List<Object[]> riskRows = new ArrayList<>();
            riskRows.add(new Object[]{"HIGH", 8L});
            riskRows.add(new Object[]{"MEDIUM", 20L});
            when(installedExtensionRepository.countByRiskLevel(TENANT_ID)).thenReturn(riskRows);

            List<Object[]> commonRows = new ArrayList<>();
            commonRows.add(new Object[]{"ext-1", "Common Ext", 30L});
            when(installedExtensionRepository.findMostCommonExtensions(TENANT_ID, 10)).thenReturn(commonRows);

            // ACT
            Map<String, Object> result = service.getExtensionStats(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(120L, result.get("totalExtensions"));
            assertTrue(result.containsKey("riskDistribution"));
            assertTrue(result.containsKey("mostCommon"));
        }
    }

    // =================== getExtensionEvents ==========================
    @Nested
    @DisplayName("getExtensionEvents")
    class GetExtensionEvents {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            Page<ExtensionEvent> page = new PageImpl<>(Collections.emptyList());
            when(extensionEventRepository.findByTenantIdOrderByEventTimestampDesc(eq(TENANT_ID), any(PageRequest.class)))
                    .thenReturn(page);

            // ACT
            Page<ExtensionEventDto> result = service.getExtensionEvents(TENANT_ID, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =================== getDeviceExtensionEvents ====================
    @Nested
    @DisplayName("getDeviceExtensionEvents")
    class GetDeviceExtensionEvents {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            // ARRANGE
            Page<ExtensionEvent> page = new PageImpl<>(Collections.emptyList());
            when(extensionEventRepository.findByDeviceDeviceIdOrderByEventTimestampDesc(eq(DEVICE_ID), any(PageRequest.class)))
                    .thenReturn(page);

            // ACT
            Page<ExtensionEventDto> result = service.getDeviceExtensionEvents(DEVICE_ID, 0, 10);

            // ASSERT
            assertNotNull(result);
        }
    }

    // =================== cleanupDuplicateExtensions ==================
    @Nested
    @DisplayName("cleanupDuplicateExtensions")
    class CleanupDuplicateExtensions {

        @Test
        @DisplayName("Happy Path — no duplicates")
        void happyPath_noDuplicates() {
            // ARRANGE
            when(installedExtensionRepository.findDuplicateExtensionIds()).thenReturn(Collections.emptyList());

            // ACT
            int deleted = service.cleanupDuplicateExtensions();

            // ASSERT
            assertEquals(0, deleted);
        }

        @Test
        @DisplayName("Happy Path — duplicates removed")
        void happyPath_duplicatesRemoved() {
            // ARRANGE
            List<String> duplicateIds = List.of("dup1", "dup2");
            when(installedExtensionRepository.findDuplicateExtensionIds()).thenReturn(duplicateIds);
            when(installedExtensionRepository.deleteByIds(duplicateIds)).thenReturn(2);

            // ACT
            int deleted = service.cleanupDuplicateExtensions();

            // ASSERT
            assertEquals(2, deleted);
            verify(installedExtensionRepository).deleteByIds(duplicateIds);
        }
    }
}