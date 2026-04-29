package com.secufusion.events.service;

import com.secufusion.events.dto.DeviceRegistrationRequest;
import com.secufusion.events.dto.DeviceResponse;
import com.secufusion.events.dto.DeviceStatusUpdateRequest;
import com.secufusion.events.entity.Device;
import com.secufusion.events.entity.DeviceStatus;
import com.secufusion.events.entity.DeviceUser;
import com.secufusion.events.entity.PolicyAssignment;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.util.JwtUtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceService Tests")
class DeviceServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────
    @Mock private DeviceRepository                 deviceRepository;
    @Mock private EntityManager                    entityManager;
    @Mock private DeviceUserGroupMappingService    deviceUserGroupMappingService;
    @Mock private JwtUtl                           jwtGroupExtractor;
    @Mock private HttpServletRequest               httpRequest;

    @InjectMocks
    private DeviceService deviceService;

    // ── Common test data ─────────────────────────────────────────────────────
    private static final String TENANT_ID   = "tenant-001";
    private static final String USER_ID     = "user-abc";
    private static final String USER_NAME   = "john.doe";
    private static final String EMAIL       = "john.doe@example.com";
    private static final String DISPLAY     = "John Doe";
    private static final String DEVICE_ID   = "device-xyz";
    private static final String FINGERPRINT = "fp-1234";

    private Device                   mockDevice;
    private DeviceRegistrationRequest regRequest;
    private DeviceUser               mockDeviceUser;
    private PolicyAssignment         mockAssignment;

    @BeforeEach
    void setUp() {
        mockDevice = Device.builder()
                .deviceId(DEVICE_ID)
                .tenantId(TENANT_ID)
                .userName(USER_NAME)
                .deviceName("John's Chrome")
                .deviceFingerprint(FINGERPRINT)
                .status(DeviceStatus.ACTIVE)
                .browserType("Chrome")
                .deviceType("DESKTOP")
                .osInfo("Windows 11")
                .firstSeenAt(LocalDateTime.now().minusDays(1))
                .lastSeenAt(LocalDateTime.now())
                .build();

        regRequest = new DeviceRegistrationRequest();
        regRequest.setDeviceFingerprint(FINGERPRINT);
        regRequest.setUserAgent("Mozilla/5.0 Chrome/120");
        regRequest.setExtensionVersion("1.2.3");
        regRequest.setIpAddress("192.168.1.10");
        regRequest.setOsInfo("Windows 11");
        regRequest.setDeviceName("John's Chrome");
        regRequest.setUserEmail(EMAIL);
        regRequest.setUserDisplayName(DISPLAY);

        mockDeviceUser = new DeviceUser();
        mockDeviceUser.setPkDeviceUserId("du-001");

        mockAssignment = mock(PolicyAssignment.class);
    }

    // =========================================================================
    // registerDevice
    // =========================================================================

    @Nested
    @DisplayName("registerDevice")
    class RegisterDevice {

        // ── Happy path: existing device updated ───────────────────────────────

        @Test
        @DisplayName("Happy Path — existing device by fingerprint is updated")
        void happyPath_existingDeviceUpdated() {
            // ARRANGE
            mockDevice.setStatus(DeviceStatus.INACTIVE); // triggers ACTIVE reset

            when(deviceRepository.findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID))
                    .thenReturn(Optional.of(mockDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                DeviceResponse mockResponse = mock(DeviceResponse.class);
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mockResponse);

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, USER_ID, USER_NAME, EMAIL, DISPLAY, regRequest, httpRequest);

                // ASSERT
                assertNotNull(result);
                verify(deviceRepository).findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID);
                verify(deviceRepository).save(any(Device.class));
                verify(entityManager).flush();
                verify(entityManager).refresh(any());
            }
        }

        @Test
        @DisplayName("Happy Path — existing ACTIVE device keeps ACTIVE status")
        void happyPath_existingActiveDeviceKeepsStatus() {
            // ARRANGE
            mockDevice.setStatus(DeviceStatus.ACTIVE);

            when(deviceRepository.findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID))
                    .thenReturn(Optional.of(mockDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, USER_ID, USER_NAME, EMAIL, DISPLAY, regRequest, httpRequest);

                // ASSERT — status setter NOT called a second time for ACTIVE devices
                assertNotNull(result);
                // device remains ACTIVE (no extra setStatus call needed)
                assertEquals(DeviceStatus.ACTIVE, mockDevice.getStatus());
            }
        }

        // ── Happy path: new device created ────────────────────────────────────

        @Test
        @DisplayName("Happy Path — no fingerprint match creates new device")
        void happyPath_newDeviceCreated() {
            // ARRANGE
            when(deviceRepository.findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID))
                    .thenReturn(Optional.empty());
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                DeviceResponse mockResponse = mock(DeviceResponse.class);
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mockResponse);

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, USER_ID, USER_NAME, EMAIL, DISPLAY, regRequest, httpRequest);

                // ASSERT
                assertNotNull(result);
                verify(deviceRepository).save(any(Device.class));
            }
        }

        @Test
        @DisplayName("Happy Path — blank fingerprint skips fingerprint lookup")
        void happyPath_blankFingerprint_skipsLookup() {
            // ARRANGE
            regRequest.setDeviceFingerprint("   "); // blank

            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, USER_ID, USER_NAME, EMAIL, DISPLAY, regRequest, httpRequest);

                // ASSERT
                assertNotNull(result);
                verify(deviceRepository, never())
                        .findByDeviceFingerprintAndTenantId(anyString(), anyString());
            }
        }

        // ── Happy path: Azure tenant with groups ──────────────────────────────

        @Test
        @DisplayName("Happy Path — Azure tenant with groups triggers autoAssignAzureUserToGroups")
        void happyPath_azureTenantWithGroups() {
            // ARRANGE
            String bearerToken = "Bearer eyJhbGciOiJSUzI1NiJ9.fake.token";
            when(httpRequest.getHeader("Authorization")).thenReturn(bearerToken);
            when(jwtGroupExtractor.extractAzureTenantIdFromToken(anyString()))
                    .thenReturn("azure-tenant-001");
            when(jwtGroupExtractor.extractAzureGroupsFromToken(anyString()))
                    .thenReturn(List.of("group-aaa", "group-bbb"));

            when(deviceRepository.findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID))
                    .thenReturn(Optional.empty());
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    eq(TENANT_ID), eq(USER_ID), eq(EMAIL), eq(DISPLAY),
                    eq(List.of("group-aaa", "group-bbb"))))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, USER_ID, USER_NAME, EMAIL, DISPLAY, regRequest, httpRequest);

                // ASSERT
                assertNotNull(result);
                verify(jwtGroupExtractor).extractAzureTenantIdFromToken(anyString());
                verify(jwtGroupExtractor).extractAzureGroupsFromToken(anyString());
                verify(deviceUserGroupMappingService).autoAssignAzureUserToGroups(
                        eq(TENANT_ID), eq(USER_ID), eq(EMAIL), eq(DISPLAY),
                        eq(List.of("group-aaa", "group-bbb")));
            }
        }

        // ── Happy path: MSI/Intune flow (no email) ────────────────────────────

        @Test
        @DisplayName("Happy Path — MSI/Intune flow uses fingerprint as identity")
        void happyPath_msiIntuneFlow_usesFingerprintAsEmail() {
            // ARRANGE — no email anywhere, but fingerprint present
            regRequest.setUserEmail(null);
            regRequest.setUserDisplayName(null);
            regRequest.setDeviceName(null);

            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceRepository.findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID))
                    .thenReturn(Optional.empty());
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, null, USER_NAME, null, null, regRequest, httpRequest);

                // ASSERT
                assertNotNull(result);
                // effectiveEmail derived from fingerprint
                verify(deviceUserGroupMappingService).autoAssignAzureUserToGroups(
                        eq(TENANT_ID), anyString(),
                        eq("device-" + FINGERPRINT + "@" + TENANT_ID),
                        anyString(), anyList());
            }
        }

        @Test
        @DisplayName("Happy Path — MSI/Intune with no fingerprint falls back to deviceId")
        void happyPath_msiIntuneFlow_noFingerprint_usesDeviceId() {
            // ARRANGE
            regRequest.setDeviceFingerprint(null);
            regRequest.setUserEmail(null);
            regRequest.setUserDisplayName(null);
            regRequest.setDeviceName(null);

            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, null, USER_NAME, null, null, regRequest, httpRequest);

                // ASSERT
                assertNotNull(result);
                verify(deviceUserGroupMappingService).autoAssignAzureUserToGroups(
                        eq(TENANT_ID), anyString(),
                        contains("device-"),
                        anyString(), anyList());
            }
        }

        // ── Happy path: existing device with blank deviceName skips rename ────

        @Test
        @DisplayName("Happy Path — blank deviceName on update skips sanitizeDeviceName")
        void happyPath_blankDeviceName_skipsRename() {
            // ARRANGE
            regRequest.setDeviceName("   "); // blank — should not trigger rename

            when(deviceRepository.findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID))
                    .thenReturn(Optional.of(mockDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn(mockDeviceUser);
            when(deviceUserGroupMappingService.resolvePoliciesForDeviceUser(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, USER_ID, USER_NAME, EMAIL, DISPLAY, regRequest, httpRequest);

                // ASSERT
                assertNotNull(result);
                // deviceName on the existing device must remain unchanged
                assertEquals("John's Chrome", mockDevice.getDeviceName());
            }
        }

        // ── Sad path: auto-assignment throws — device still registered ────────

        @Test
        @DisplayName("Sad Path — autoAssign throws; device registration still succeeds")
        void sadPath_autoAssignThrows_deviceStillRegistered() {
            // ARRANGE
            when(deviceRepository.findByDeviceFingerprintAndTenantId(FINGERPRINT, TENANT_ID))
                    .thenReturn(Optional.empty());
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);
            doNothing().when(entityManager).flush();
            doNothing().when(entityManager).refresh(any());
            when(httpRequest.getHeader("Authorization")).thenReturn(null);
            when(deviceUserGroupMappingService.autoAssignAzureUserToGroups(
                    anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenThrow(new RuntimeException("Keycloak unavailable"));

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                DeviceResponse mockResponse = mock(DeviceResponse.class);
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mockResponse);

                // ACT
                DeviceResponse result = deviceService.registerDevice(
                        TENANT_ID, USER_ID, USER_NAME, EMAIL, DISPLAY, regRequest, httpRequest);

                // ASSERT — catch block swallows the exception; response is still returned
                assertNotNull(result);
                verify(deviceRepository).save(any(Device.class));
                // resolvePolicies should NOT be reached
                verify(deviceUserGroupMappingService, never())
                        .resolvePoliciesForDeviceUser(anyString(), anyString());
            }
        }
    }

    // =========================================================================
    // getDevices
    // =========================================================================

    @Nested
    @DisplayName("getDevices")
    class GetDevices {

        @Test
        @DisplayName("Happy Path — returns paged device responses")
        void happyPath_returnsPaged() {
            // ARRANGE
            Page<Device> devicePage = new PageImpl<>(List.of(mockDevice));
            when(deviceRepository.findByTenantIdOrderByLastSeenAtDesc(
                    eq(TENANT_ID), any(Pageable.class)))
                    .thenReturn(devicePage);

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                Page<DeviceResponse> result = deviceService.getDevices(TENANT_ID, 0, 10);

                // ASSERT
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
                verify(deviceRepository).findByTenantIdOrderByLastSeenAtDesc(
                        eq(TENANT_ID), any(Pageable.class));
            }
        }

        @Test
        @DisplayName("Happy Path — empty tenant returns empty page")
        void happyPath_emptyPage() {
            // ARRANGE
            when(deviceRepository.findByTenantIdOrderByLastSeenAtDesc(
                    anyString(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            // ACT
            Page<DeviceResponse> result = deviceService.getDevices(TENANT_ID, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    // =========================================================================
    // getDevicesByStatus
    // =========================================================================

    @Nested
    @DisplayName("getDevicesByStatus")
    class GetDevicesByStatus {

        @Test
        @DisplayName("Happy Path — filters by status and returns page")
        void happyPath_filtersByStatus() {
            // ARRANGE
            Page<Device> page = new PageImpl<>(List.of(mockDevice));
            when(deviceRepository.findByTenantIdAndStatusOrderByLastSeenAtDesc(
                    eq(TENANT_ID), eq(DeviceStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(page);

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                Page<DeviceResponse> result = deviceService.getDevicesByStatus(
                        TENANT_ID, DeviceStatus.ACTIVE, 0, 10);

                // ASSERT
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
                verify(deviceRepository).findByTenantIdAndStatusOrderByLastSeenAtDesc(
                        eq(TENANT_ID), eq(DeviceStatus.ACTIVE), any(Pageable.class));
            }
        }
    }

    // =========================================================================
    // getDevice
    // =========================================================================

    @Nested
    @DisplayName("getDevice")
    class GetDevice {

        @Test
        @DisplayName("Happy Path — returns DeviceResponse for known device")
        void happyPath_returnsResponse() {
            // ARRANGE
            when(deviceRepository.findByDeviceIdAndTenantId(DEVICE_ID, TENANT_ID))
                    .thenReturn(Optional.of(mockDevice));

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                DeviceResponse mockResponse = mock(DeviceResponse.class);
                mocked.when(() -> DeviceResponse.fromEntity(mockDevice)).thenReturn(mockResponse);

                // ACT
                DeviceResponse result = deviceService.getDevice(TENANT_ID, DEVICE_ID);

                // ASSERT
                assertNotNull(result);
                verify(deviceRepository).findByDeviceIdAndTenantId(DEVICE_ID, TENANT_ID);
            }
        }

        @Test
        @DisplayName("Sad Path — device not found throws ResourceNotFoundException")
        void sadPath_notFound_throwsException() {
            // ARRANGE
            when(deviceRepository.findByDeviceIdAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> deviceService.getDevice(TENANT_ID, "wrong-id"));
        }
    }

    // =========================================================================
    // getDeviceEntity
    // =========================================================================

    @Nested
    @DisplayName("getDeviceEntity")
    class GetDeviceEntity {

        @Test
        @DisplayName("Happy Path — returns Optional<Device>")
        void happyPath_returnsOptional() {
            // ARRANGE
            when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(mockDevice));

            // ACT
            Optional<Device> result = deviceService.getDeviceEntity(DEVICE_ID);

            // ASSERT
            assertTrue(result.isPresent());
            assertEquals(DEVICE_ID, result.get().getDeviceId());
            verify(deviceRepository).findById(DEVICE_ID);
        }

        @Test
        @DisplayName("Sad Path — unknown id returns Optional.empty()")
        void sadPath_unknownId_returnsEmpty() {
            // ARRANGE
            when(deviceRepository.findById(anyString())).thenReturn(Optional.empty());

            // ACT
            Optional<Device> result = deviceService.getDeviceEntity("unknown");

            // ASSERT
            assertFalse(result.isPresent());
        }
    }

    // =========================================================================
    // updateDeviceStatus
    // =========================================================================

    @Nested
    @DisplayName("updateDeviceStatus")
    class UpdateDeviceStatus {

        @Test
        @DisplayName("Happy Path — status updated and saved")
        void happyPath_statusUpdated() {
            // ARRANGE
            DeviceStatusUpdateRequest req = new DeviceStatusUpdateRequest();
            req.setStatus(DeviceStatus.BLOCKED);

            when(deviceRepository.findByDeviceIdAndTenantId(DEVICE_ID, TENANT_ID))
                    .thenReturn(Optional.of(mockDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                DeviceResponse mockResponse = mock(DeviceResponse.class);
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mockResponse);

                // ACT
                DeviceResponse result = deviceService.updateDeviceStatus(TENANT_ID, DEVICE_ID, req);

                // ASSERT
                assertNotNull(result);
                assertEquals(DeviceStatus.BLOCKED, mockDevice.getStatus());
                verify(deviceRepository).save(any(Device.class));
            }
        }

        @Test
        @DisplayName("Sad Path — device not found throws ResourceNotFoundException")
        void sadPath_notFound_throwsException() {
            // ARRANGE
            DeviceStatusUpdateRequest req = new DeviceStatusUpdateRequest();
            req.setStatus(DeviceStatus.BLOCKED);

            when(deviceRepository.findByDeviceIdAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> deviceService.updateDeviceStatus(TENANT_ID, "bad-id", req));
            verify(deviceRepository, never()).save(any());
        }
    }

    // =========================================================================
    // updateLastSeen
    // =========================================================================

    @Nested
    @DisplayName("updateLastSeen")
    class UpdateLastSeen {

        @Test
        @DisplayName("Happy Path — INACTIVE device set to ACTIVE and saved")
        void happyPath_inactiveDeviceReactivated() {
            // ARRANGE
            mockDevice.setStatus(DeviceStatus.INACTIVE);
            when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(mockDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);

            // ACT
            deviceService.updateLastSeen(DEVICE_ID);

            // ASSERT
            assertEquals(DeviceStatus.ACTIVE, mockDevice.getStatus());
            verify(deviceRepository).save(mockDevice);
        }

        @Test
        @DisplayName("Happy Path — ACTIVE device timestamp updated without status change")
        void happyPath_activeDevice_timestampUpdated() {
            // ARRANGE
            mockDevice.setStatus(DeviceStatus.ACTIVE);
            when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(mockDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);

            // ACT
            deviceService.updateLastSeen(DEVICE_ID);

            // ASSERT
            assertEquals(DeviceStatus.ACTIVE, mockDevice.getStatus());
            verify(deviceRepository).save(mockDevice);
        }

        @Test
        @DisplayName("Sad Path — device not found; ifPresent is no-op, no save called")
        void sadPath_deviceNotFound_noSave() {
            // ARRANGE
            when(deviceRepository.findById(anyString())).thenReturn(Optional.empty());

            // ACT
            deviceService.updateLastSeen("missing-id");

            // ASSERT
            verify(deviceRepository, never()).save(any());
        }
    }

    // =========================================================================
    // getDeviceStats
    // =========================================================================

    @Nested
    @DisplayName("getDeviceStats")
    class GetDeviceStats {

        @Test
        @DisplayName("Happy Path — returns map with total/active/inactive/blocked counts")
        void happyPath_returnsStats() {
            // ARRANGE
            when(deviceRepository.countByTenantId(TENANT_ID)).thenReturn(100L);
            when(deviceRepository.countByTenantIdAndStatus(TENANT_ID, DeviceStatus.ACTIVE))
                    .thenReturn(70L);
            when(deviceRepository.countByTenantIdAndStatus(TENANT_ID, DeviceStatus.INACTIVE))
                    .thenReturn(20L);
            when(deviceRepository.countByTenantIdAndStatus(TENANT_ID, DeviceStatus.BLOCKED))
                    .thenReturn(10L);

            // ACT
            Map<String, Long> result = deviceService.getDeviceStats(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(100L, result.get("total"));
            assertEquals(70L,  result.get("active"));
            assertEquals(20L,  result.get("inactive"));
            assertEquals(10L,  result.get("blocked"));
            verify(deviceRepository).countByTenantId(TENANT_ID);
            verify(deviceRepository, times(3))
                    .countByTenantIdAndStatus(eq(TENANT_ID), any(DeviceStatus.class));
        }
    }

    // =========================================================================
    // getDevicesByType
    // =========================================================================

    @Nested
    @DisplayName("getDevicesByType")
    class GetDevicesByType {

        @Test
        @DisplayName("Happy Path — maps type rows to String→Long entries")
        void happyPath_mapsTypeRows() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{"DESKTOP", 40L});
            rows.add(new Object[]{"MOBILE",  20L});
            rows.add(new Object[]{null,       5L}); // null type → "Unknown"

            when(deviceRepository.countDevicesByType(TENANT_ID)).thenReturn(rows);

            // ACT
            Map<String, Long> result = deviceService.getDevicesByType(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(40L, result.get("DESKTOP"));
            assertEquals(20L, result.get("MOBILE"));
            assertEquals(5L,  result.get("Unknown"));
            verify(deviceRepository).countDevicesByType(TENANT_ID);
        }

        @Test
        @DisplayName("Happy Path — empty results returns empty map")
        void happyPath_emptyResults() {
            // ARRANGE
            when(deviceRepository.countDevicesByType(anyString()))
                    .thenReturn(new ArrayList<>());

            // ACT
            Map<String, Long> result = deviceService.getDevicesByType(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // getRecentDevices
    // =========================================================================

    @Nested
    @DisplayName("getRecentDevices")
    class GetRecentDevices {

        @Test
        @DisplayName("Happy Path — returns mapped DeviceResponse list")
        void happyPath_returnsMappedList() {
            // ARRANGE
            when(deviceRepository.findRecentDevices(TENANT_ID, 5))
                    .thenReturn(List.of(mockDevice));

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                List<DeviceResponse> result = deviceService.getRecentDevices(TENANT_ID, 5);

                // ASSERT
                assertNotNull(result);
                assertEquals(1, result.size());
                verify(deviceRepository).findRecentDevices(TENANT_ID, 5);
            }
        }

        @Test
        @DisplayName("Happy Path — empty list returned when no recent devices")
        void happyPath_emptyList() {
            // ARRANGE
            when(deviceRepository.findRecentDevices(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<DeviceResponse> result = deviceService.getRecentDevices(TENANT_ID, 5);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // getDevicesForUser
    // =========================================================================

    @Nested
    @DisplayName("getDevicesForUser")
    class GetDevicesForUser {

        @Test
        @DisplayName("Happy Path — returns devices for specific user")
        void happyPath_returnsUserDevices() {
            // ARRANGE
            when(deviceRepository.findByTenantIdAndUserNameOrderByLastSeenAtDesc(
                    TENANT_ID, USER_NAME))
                    .thenReturn(List.of(mockDevice));

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                List<DeviceResponse> result = deviceService.getDevicesForUser(TENANT_ID, USER_NAME);

                // ASSERT
                assertNotNull(result);
                assertEquals(1, result.size());
                verify(deviceRepository)
                        .findByTenantIdAndUserNameOrderByLastSeenAtDesc(TENANT_ID, USER_NAME);
            }
        }
    }

    // =========================================================================
    // markInactiveDevices
    // =========================================================================

    @Nested
    @DisplayName("markInactiveDevices")
    class MarkInactiveDevices {

        @Test
        @DisplayName("Happy Path — marks devices INACTIVE and returns count")
        void happyPath_marksInactive() {
            // ARRANGE
            Device d1 = Device.builder().deviceId("d1").status(DeviceStatus.ACTIVE).build();
            Device d2 = Device.builder().deviceId("d2").status(DeviceStatus.ACTIVE).build();

            when(deviceRepository.findInactiveDevices(eq(TENANT_ID), any(LocalDateTime.class)))
                    .thenReturn(List.of(d1, d2));
            when(deviceRepository.saveAll(anyList())).thenReturn(List.of(d1, d2));

            // ACT
            int count = deviceService.markInactiveDevices(TENANT_ID, 24);

            // ASSERT
            assertEquals(2, count);
            assertEquals(DeviceStatus.INACTIVE, d1.getStatus());
            assertEquals(DeviceStatus.INACTIVE, d2.getStatus());
            verify(deviceRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Happy Path — no inactive devices returns 0")
        void happyPath_noneInactive_returnsZero() {
            // ARRANGE
            when(deviceRepository.findInactiveDevices(anyString(), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());
            when(deviceRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            int count = deviceService.markInactiveDevices(TENANT_ID, 48);

            // ASSERT
            assertEquals(0, count);
        }
    }

    // =========================================================================
    // deactivateDevice
    // =========================================================================

    @Nested
    @DisplayName("deactivateDevice")
    class DeactivateDevice {

        @Test
        @DisplayName("Happy Path — sets status INACTIVE and returns response")
        void happyPath_deactivates() {
            // ARRANGE
            when(deviceRepository.findByDeviceIdAndTenantId(DEVICE_ID, TENANT_ID))
                    .thenReturn(Optional.of(mockDevice));
            when(deviceRepository.save(any(Device.class))).thenReturn(mockDevice);

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                DeviceResponse mockResponse = mock(DeviceResponse.class);
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mockResponse);

                // ACT
                DeviceResponse result = deviceService.deactivateDevice(TENANT_ID, DEVICE_ID);

                // ASSERT
                assertNotNull(result);
                assertEquals(DeviceStatus.INACTIVE, mockDevice.getStatus());
                verify(deviceRepository).save(any(Device.class));
            }
        }

        @Test
        @DisplayName("Sad Path — device not found throws ResourceNotFoundException")
        void sadPath_notFound_throwsException() {
            // ARRANGE
            when(deviceRepository.findByDeviceIdAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> deviceService.deactivateDevice(TENANT_ID, "bad-id"));
            verify(deviceRepository, never()).save(any());
        }
    }

    // =========================================================================
    // searchDevices
    // =========================================================================

    @Nested
    @DisplayName("searchDevices")
    class SearchDevices {

        @Test
        @DisplayName("Happy Path — with status uses searchDevicesByStatus")
        void happyPath_withStatus_usesFilteredSearch() {
            // ARRANGE
            Page<Device> page = new PageImpl<>(List.of(mockDevice));
            when(deviceRepository.searchDevicesByStatus(
                    eq(TENANT_ID), anyString(), eq(DeviceStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(page);

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                Page<DeviceResponse> result = deviceService.searchDevices(
                        TENANT_ID, "chrome", DeviceStatus.ACTIVE, 0, 10);

                // ASSERT
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
                verify(deviceRepository).searchDevicesByStatus(
                        eq(TENANT_ID), anyString(), eq(DeviceStatus.ACTIVE), any(Pageable.class));
                verify(deviceRepository, never()).searchDevices(anyString(), anyString(), any());
            }
        }

        @Test
        @DisplayName("Happy Path — null status uses unfiltered searchDevices")
        void happyPath_nullStatus_usesUnfilteredSearch() {
            // ARRANGE
            Page<Device> page = new PageImpl<>(List.of(mockDevice));
            when(deviceRepository.searchDevices(
                    eq(TENANT_ID), anyString(), any(Pageable.class)))
                    .thenReturn(page);

            try (MockedStatic<DeviceResponse> mocked = mockStatic(DeviceResponse.class)) {
                mocked.when(() -> DeviceResponse.fromEntity(any())).thenReturn(mock(DeviceResponse.class));

                // ACT
                Page<DeviceResponse> result = deviceService.searchDevices(
                        TENANT_ID, "chrome", null, 0, 10);

                // ASSERT
                assertNotNull(result);
                verify(deviceRepository).searchDevices(
                        eq(TENANT_ID), anyString(), any(Pageable.class));
                verify(deviceRepository, never())
                        .searchDevicesByStatus(anyString(), anyString(), any(), any());
            }
        }
    }
}