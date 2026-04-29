package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserActivityService Tests")
class UserActivityServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private DeviceUserRepository deviceUserRepository;
    @Mock private InstalledExtensionRepository installedExtensionRepository;
    @Mock private EventRepository eventRepository;
    @Mock private ExtensionEventRepository extensionEventRepository;

    @InjectMocks
    private UserActivityService service;

    private static final String TENANT_ID = "t1";
    private static final String USER_ID = "u1";
    private static final String DEVICE_USER_ID = "du1";

    private User testUser;
    private Device testDevice;
    private DeviceUser testDeviceUser;

    @BeforeEach
    void setUp() {
        // User
        testUser = new User();
        testUser.setPkUserId(USER_ID);
        testUser.setUserName("testuser");
        testUser.setEmail("user@example.com");
        testUser.setTenant(new Tenant());
        testUser.getTenant().setTenantID(TENANT_ID);

        // Device
        testDevice = new Device();
        testDevice.setDeviceId("dev-1");
        testDevice.setTenantId(TENANT_ID);
        testDevice.setDeviceName("Test Device");
        testDevice.setDeviceType("Desktop");
        testDevice.setBrowserType("Chrome");
        testDevice.setStatus(DeviceStatus.ACTIVE);
        testDevice.setLastSeenAt(LocalDateTime.now());
        testDevice.setUserName("testuser");
        testDevice.setLinkedUserId(USER_ID);

        // DeviceUser
        testDeviceUser = new DeviceUser();
        testDeviceUser.setPkDeviceUserId(DEVICE_USER_ID);
        testDeviceUser.setTenantId(TENANT_ID);
        testDeviceUser.setEmail("deviceuser@example.com");
        testDeviceUser.setUserName("deviceuser");
        testDeviceUser.setDisplayName("Device User");
        testDeviceUser.setStatus("ACTIVE");
        testDeviceUser.setLastSeenAt(LocalDateTime.now());
        testDeviceUser.setPortalUserId(null);
    }

    // ==================== getUserList ====================
    @Nested
    @DisplayName("getUserList")
    class GetUserListTests {

        @Test
        @DisplayName("Happy Path – returns enriched user page")
        void happyPath() {
            // ARRANGE
            Page<User> userPage = new PageImpl<>(List.of(testUser));
            when(userRepository.findByTenant_TenantIDOrderByUserNameAsc(eq(TENANT_ID), any(Pageable.class)))
                    .thenReturn(userPage);

            // BATCH 1: devices
            when(deviceRepository.findAllDevicesForUsers(eq(TENANT_ID), anyList(), anyList()))
                    .thenReturn(List.of(testDevice));

            // BATCH 2-4: event counts, security event counts, last event times (as Object[] lists)
            List<Object[]> eventRows = new ArrayList<>();
            eventRows.add(new Object[]{"testuser", 15L});
            when(eventRepository.countEventsByUserNames(eq(TENANT_ID), anyList())).thenReturn(eventRows);

            List<Object[]> secRows = new ArrayList<>();
            secRows.add(new Object[]{"testuser", 5L});
            when(eventRepository.countSecurityEventsByUserNames(eq(TENANT_ID), anyList())).thenReturn(secRows);

            List<Object[]> lastRows = new ArrayList<>();
            lastRows.add(new Object[]{"testuser", Timestamp.valueOf(LocalDateTime.now().minusDays(1))});
            when(eventRepository.findLastEventTimesByUserNames(eq(TENANT_ID), anyList())).thenReturn(lastRows);

            // BATCH 5-6: extension counts
            List<Object[]> extCountRows = new ArrayList<>();
            extCountRows.add(new Object[]{USER_ID, 10L});
            when(installedExtensionRepository.countExtensionsByUserIds(eq(TENANT_ID), anyList())).thenReturn(extCountRows);

            List<Object[]> highRiskRows = new ArrayList<>();
            highRiskRows.add(new Object[]{USER_ID, 2L});
            when(installedExtensionRepository.countHighRiskExtensionsByUserIds(eq(TENANT_ID), anyList())).thenReturn(highRiskRows);

            // ACT
            Page<UserListDto> result = service.getUserList(TENANT_ID, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            UserListDto dto = result.getContent().get(0);
            assertEquals(USER_ID, dto.getUserId());
            assertEquals(1, dto.getDeviceCount());
            assertEquals(10, dto.getExtensionCount());
            assertEquals(2, dto.getHighRiskExtensionCount());
            assertEquals(15L, dto.getEventCount());
            assertEquals(5L, dto.getSecurityEventCount());
            assertNotNull(dto.getLastActivityAt());
        }

        @Test
        @DisplayName("Happy Path – empty page returns empty list")
        void happyPath_emptyPage() {
            Page<User> emptyPage = new PageImpl<>(Collections.emptyList());
            when(userRepository.findByTenant_TenantIDOrderByUserNameAsc(eq(TENANT_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            Page<UserListDto> result = service.getUserList(TENANT_ID, 0, 10);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== searchUsers ====================
    @Nested
    @DisplayName("searchUsers")
    class SearchUsersTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<User> userPage = new PageImpl<>(List.of(testUser));
            when(userRepository.searchUsers(eq(TENANT_ID), eq("test"), any(Pageable.class)))
                    .thenReturn(userPage);

            // Stubs for enrichment (similar to getUserList)
            when(deviceRepository.findAllDevicesForUsers(anyString(), anyList(), anyList())).thenReturn(Collections.emptyList());
            when(eventRepository.countEventsByUserNames(anyString(), anyList())).thenReturn(new ArrayList<>());
            when(eventRepository.countSecurityEventsByUserNames(anyString(), anyList())).thenReturn(new ArrayList<>());
            when(eventRepository.findLastEventTimesByUserNames(anyString(), anyList())).thenReturn(new ArrayList<>());
            when(installedExtensionRepository.countExtensionsByUserIds(anyString(), anyList())).thenReturn(new ArrayList<>());
            when(installedExtensionRepository.countHighRiskExtensionsByUserIds(anyString(), anyList())).thenReturn(new ArrayList<>());

            Page<UserListDto> result = service.searchUsers(TENANT_ID, "test", 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getUserWithDevices ====================
    @Nested
    @DisplayName("getUserWithDevices")
    class GetUserWithDevicesTests {

        @Test
        @DisplayName("Happy Path – returns detailed user info")
        void happyPath() {
            when(userRepository.findByPkUserIdAndTenant_TenantID(USER_ID, TENANT_ID))
                    .thenReturn(Optional.of(testUser));
            when(deviceRepository.findAllUserDevices(TENANT_ID, USER_ID, "testuser"))
                    .thenReturn(List.of(testDevice));

            List<InstalledExtension> extensions = new ArrayList<>();
            InstalledExtension ext = new InstalledExtension();
            ext.setRiskLevel("HIGH");
            ext.setPolicyAction("BLOCK");
            extensions.add(ext);
            when(installedExtensionRepository.findByTenantIdAndUserIdOrderByExtensionNameAsc(TENANT_ID, USER_ID))
                    .thenReturn(extensions);

            when(eventRepository.countByTenantIdAndUserName(TENANT_ID, "testuser")).thenReturn(50L);
            when(eventRepository.countSecurityEventsByUserName(TENANT_ID, "testuser")).thenReturn(10L);
            when(eventRepository.countPolicyViolationsByUserName(TENANT_ID, "testuser")).thenReturn(3L);
            when(eventRepository.findLastEventTimeByUserName(TENANT_ID, "testuser"))
                    .thenReturn(LocalDateTime.now().minusHours(1));

            UserWithDevicesDto result = service.getUserWithDevices(TENANT_ID, USER_ID);

            assertNotNull(result);
            assertEquals(USER_ID, result.getUserId());
            assertEquals(1, result.getDevices().size());
            assertEquals(1, result.getTotalExtensions());
            assertEquals(1, result.getHighRiskExtensions());
            assertEquals(1, result.getBlockedExtensions());
            assertEquals(50L, result.getTotalEvents());
            assertEquals(10L, result.getSecurityEvents());
            assertEquals(3L, result.getPolicyViolations());
        }

        @Test
        @DisplayName("Sad Path – user not found")
        void sadPath_userNotFound() {
            when(userRepository.findByPkUserIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getUserWithDevices(TENANT_ID, "bad"));
        }
    }

    // ==================== getUserDevices ====================
    @Nested
    @DisplayName("getUserDevices")
    class GetUserDevicesTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(userRepository.findByPkUserIdAndTenant_TenantID(USER_ID, TENANT_ID))
                    .thenReturn(Optional.of(testUser));
            when(deviceRepository.findAllUserDevices(TENANT_ID, USER_ID, "testuser"))
                    .thenReturn(List.of(testDevice));

            List<DeviceResponse> result = service.getUserDevices(TENANT_ID, USER_ID);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Sad Path – user not found")
        void sadPath_userNotFound() {
            when(userRepository.findByPkUserIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getUserDevices(TENANT_ID, "bad"));
        }
    }

    // ==================== getUserExtensions ====================
    @Nested
    @DisplayName("getUserExtensions")
    class GetUserExtensionsTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(userRepository.findByPkUserIdAndTenant_TenantID(USER_ID, TENANT_ID))
                    .thenReturn(Optional.of(testUser));
            when(installedExtensionRepository.findByTenantIdAndUserIdOrderByExtensionNameAsc(TENANT_ID, USER_ID))
                    .thenReturn(Collections.emptyList());

            List<InstalledExtensionDto> result = service.getUserExtensions(TENANT_ID, USER_ID);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Sad Path – user not found")
        void sadPath_userNotFound() {
            when(userRepository.findByPkUserIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getUserExtensions(TENANT_ID, "bad"));
        }
    }

    // ==================== getUserExtensionEvents ====================
    @Nested
    @DisplayName("getUserExtensionEvents")
    class GetUserExtensionEventsTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<ExtensionEvent> page = new PageImpl<>(Collections.emptyList());
            when(extensionEventRepository.findByTenantIdAndUserIdOrderByEventTimestampDesc(eq(TENANT_ID), eq(USER_ID), any(Pageable.class)))
                    .thenReturn(page);

            Page<ExtensionEventDto> result = service.getUserExtensionEvents(TENANT_ID, USER_ID, 0, 10);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== getUserStats ====================
    @Nested
    @DisplayName("getUserStats")
    class GetUserStatsTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(userRepository.countByTenant_TenantID(TENANT_ID)).thenReturn(100L);
            when(userRepository.countActiveUsers(TENANT_ID)).thenReturn(80L);
            when(userRepository.countUsersWithDevices(TENANT_ID)).thenReturn(60L);
            when(userRepository.countUsersWithHighRiskExtensions(TENANT_ID)).thenReturn(5L);

            Map<String, Object> stats = service.getUserStats(TENANT_ID);
            assertEquals(100L, stats.get("totalUsers"));
            assertEquals(80L, stats.get("activeUsers"));
            assertEquals(60L, stats.get("usersWithDevices"));
            assertEquals(5L, stats.get("usersWithHighRiskExtensions"));
        }
    }

    // ==================== getDeviceUserList ====================
    @Nested
    @DisplayName("getDeviceUserList")
    class GetDeviceUserListTests {

        @Test
        @DisplayName("Happy Path – returns enriched device user page")
        void happyPath() {
            Page<DeviceUser> duPage = new PageImpl<>(List.of(testDeviceUser));
            when(deviceUserRepository.findByTenantIdOrderByLastSeenAtDesc(eq(TENANT_ID), any(Pageable.class)))
                    .thenReturn(duPage);

            // Batch enrichment mocks
            List<Object[]> deviceCountRows = new ArrayList<>();
            deviceCountRows.add(new Object[]{DEVICE_USER_ID, 2L});
            when(deviceRepository.countDevicesByDeviceUserIds(TENANT_ID, List.of(DEVICE_USER_ID)))
                    .thenReturn(deviceCountRows);

            List<Object[]> eventCountRows = new ArrayList<>();
            eventCountRows.add(new Object[]{DEVICE_USER_ID, 10L});
            when(eventRepository.countEventsByDeviceUserIds(TENANT_ID, List.of(DEVICE_USER_ID)))
                    .thenReturn(eventCountRows);

            List<Object[]> secEventRows = new ArrayList<>();
            secEventRows.add(new Object[]{DEVICE_USER_ID, 3L});
            when(eventRepository.countSecurityEventsByDeviceUserIds(TENANT_ID, List.of(DEVICE_USER_ID)))
                    .thenReturn(secEventRows);

            Page<DeviceUserDTO> result = service.getDeviceUserList(TENANT_ID, 0, 10);
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            DeviceUserDTO dto = result.getContent().get(0);
            assertEquals(DEVICE_USER_ID, dto.getDeviceUserId());
            assertEquals(2L, dto.getDeviceCount());
            assertEquals(10L, dto.getEventCount());
            assertEquals(3L, dto.getSecurityEventCount());
        }

        @Test
        @DisplayName("Happy Path – empty page")
        void happyPath_emptyPage() {
            Page<DeviceUser> emptyPage = new PageImpl<>(Collections.emptyList());
            when(deviceUserRepository.findByTenantIdOrderByLastSeenAtDesc(eq(TENANT_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            Page<DeviceUserDTO> result = service.getDeviceUserList(TENANT_ID, 0, 10);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== searchDeviceUsers ====================
    @Nested
    @DisplayName("searchDeviceUsers")
    class SearchDeviceUsersTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<DeviceUser> duPage = new PageImpl<>(List.of(testDeviceUser));
            when(deviceUserRepository.searchUsers(eq(TENANT_ID), eq("test"), any(Pageable.class)))
                    .thenReturn(duPage);

            // Stub batch counts with empty lists (using new ArrayList<>())
            when(deviceRepository.countDevicesByDeviceUserIds(anyString(), anyList())).thenReturn(new ArrayList<>());
            when(eventRepository.countEventsByDeviceUserIds(anyString(), anyList())).thenReturn(new ArrayList<>());
            when(eventRepository.countSecurityEventsByDeviceUserIds(anyString(), anyList())).thenReturn(new ArrayList<>());

            Page<DeviceUserDTO> result = service.searchDeviceUsers(TENANT_ID, "test", 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getDeviceUser ====================
    @Nested
    @DisplayName("getDeviceUser")
    class GetDeviceUserTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(deviceUserRepository.findById(DEVICE_USER_ID)).thenReturn(Optional.of(testDeviceUser));
            when(deviceRepository.countByTenantIdAndDeviceUser_PkDeviceUserId(TENANT_ID, DEVICE_USER_ID)).thenReturn(2L);
            when(eventRepository.countByTenant_TenantIDAndDeviceUser_PkDeviceUserId(TENANT_ID, DEVICE_USER_ID)).thenReturn(10L);
            when(eventRepository.countSecurityEventsByDeviceUserId(TENANT_ID, DEVICE_USER_ID)).thenReturn(3L);

            DeviceUserDTO result = service.getDeviceUser(TENANT_ID, DEVICE_USER_ID);
            assertNotNull(result);
            assertEquals(DEVICE_USER_ID, result.getDeviceUserId());
            assertEquals(2L, result.getDeviceCount());
            assertEquals(10L, result.getEventCount());
            assertEquals(3L, result.getSecurityEventCount());
        }

        @Test
        @DisplayName("Sad Path – device user not found")
        void sadPath_notFound() {
            when(deviceUserRepository.findById(anyString())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getDeviceUser(TENANT_ID, "bad"));
        }
    }

    // ==================== getDeviceUserByEmail ====================
    @Nested
    @DisplayName("getDeviceUserByEmail")
    class GetDeviceUserByEmailTests {

        @Test
        @DisplayName("Happy Path – found")
        void happyPath() {
            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, "deviceuser@example.com"))
                    .thenReturn(Optional.of(testDeviceUser));
            when(deviceRepository.countByTenantIdAndDeviceUser_PkDeviceUserId(anyString(), anyString())).thenReturn(0L);
            when(eventRepository.countByTenant_TenantIDAndDeviceUser_PkDeviceUserId(anyString(), anyString())).thenReturn(0L);
            when(eventRepository.countSecurityEventsByDeviceUserId(anyString(), anyString())).thenReturn(0L);

            Optional<DeviceUserDTO> result = service.getDeviceUserByEmail(TENANT_ID, "deviceuser@example.com");
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Happy Path – not found")
        void happyPath_notFound() {
            when(deviceUserRepository.findByTenantIdAndEmail(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertFalse(service.getDeviceUserByEmail(TENANT_ID, "unknown@example.com").isPresent());
        }
    }

    // ==================== getDeviceUserDevices ====================
    @Nested
    @DisplayName("getDeviceUserDevices")
    class GetDeviceUserDevicesTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(deviceUserRepository.findById(DEVICE_USER_ID)).thenReturn(Optional.of(testDeviceUser));
            Device dev = new Device();
            dev.setDeviceId("dev-du");
            when(deviceRepository.findByTenantIdAndDeviceUser_PkDeviceUserIdOrderByLastSeenAtDesc(TENANT_ID, DEVICE_USER_ID))
                    .thenReturn(List.of(dev));

            List<DeviceResponse> result = service.getDeviceUserDevices(TENANT_ID, DEVICE_USER_ID);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Sad Path – device user not found")
        void sadPath_notFound() {
            when(deviceUserRepository.findById(anyString())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getDeviceUserDevices(TENANT_ID, "bad"));
        }
    }

    // ==================== getDeviceUserEvents ====================
    @Nested
    @DisplayName("getDeviceUserEvents")
    class GetDeviceUserEventsTests {

        @Test
        @DisplayName("Happy Path – returns paged events")
        void happyPath() {
            Event event = new Event();
            event.setPkEventId("ev1");
            event.setUrl("https://example.com");
            event.setTimeStamp(LocalDateTime.now());
            event.setEventType(EventType.WEBSITE_VISIT);
            event.setUserName("testuser");
            event.setDevice(new Device());
            event.getDevice().setDeviceId("dev-1");
            Page<Event> page = new PageImpl<>(List.of(event));
            when(eventRepository.findByTenant_TenantIDAndDeviceUser_PkDeviceUserIdOrderByTimeStampDesc(
                    eq(TENANT_ID), eq(DEVICE_USER_ID), any(Pageable.class))).thenReturn(page);

            Page<EventDto> result = service.getDeviceUserEvents(TENANT_ID, DEVICE_USER_ID, 0, 10);
            assertEquals(1, result.getTotalElements());
            assertEquals("ev1", result.getContent().get(0).getId());
        }
    }

    // ==================== getDeviceUserStats ====================
    @Nested
    @DisplayName("getDeviceUserStats")
    class GetDeviceUserStatsTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(deviceUserRepository.countByTenantId(TENANT_ID)).thenReturn(50L);
            when(deviceUserRepository.countByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(30L);
            when(deviceUserRepository.countByTenantIdAndStatus(TENANT_ID, "BLOCKED")).thenReturn(5L);
            when(deviceUserRepository.countLinkedToPortal(TENANT_ID)).thenReturn(10L);
            when(deviceUserRepository.countExtensionOnlyUsers(TENANT_ID)).thenReturn(15L);
            when(deviceUserRepository.findActiveUsersSince(eq(TENANT_ID), any(LocalDateTime.class)))
                    .thenReturn(List.of(testDeviceUser));
            when(deviceUserRepository.findActiveUsersSince(eq(TENANT_ID), any(LocalDateTime.class)))
                    .thenReturn(List.of(testDeviceUser)); // could differentiate, but simulates

            Map<String, Object> stats = service.getDeviceUserStats(TENANT_ID);
            assertEquals(50L, stats.get("totalDeviceUsers"));
            assertEquals(30L, stats.get("activeDeviceUsers"));
            assertEquals(5L, stats.get("blockedDeviceUsers"));
            assertEquals(10L, stats.get("linkedToPortal"));
            assertEquals(15L, stats.get("extensionOnlyUsers"));
            assertEquals(1, stats.get("activeInLast24Hours"));
            assertEquals(1, stats.get("activeInLast7Days"));
        }
    }

    // ==================== getDeviceUserDetails ====================
    @Nested
    @DisplayName("getDeviceUserDetails")
    class GetDeviceUserDetailsTests {

        @Test
        @DisplayName("Happy Path – comprehensive details")
        void happyPath() {
            when(deviceUserRepository.findById(DEVICE_USER_ID)).thenReturn(Optional.of(testDeviceUser));

            // Devices
            when(deviceRepository.findByTenantIdAndDeviceUser_PkDeviceUserIdOrderByLastSeenAtDesc(
                    eq(TENANT_ID), eq(DEVICE_USER_ID))).thenReturn(List.of(testDevice));

            // Per-device event counts
            List<Object[]> deviceCountRows = new ArrayList<>();
            deviceCountRows.add(new Object[]{"dev-1", 5L});
            when(eventRepository.countEventsByDeviceIds(anyList())).thenReturn(deviceCountRows);

            // Comprehensive counts
            List<Object[]> comprehensiveCounts = new ArrayList<>();
            comprehensiveCounts.add(new Object[]{24L, 3L, 10L, 15L, 4L, 2L, 6L, 4L, 1L});
            when(eventRepository.getComprehensiveCountsForDeviceUser(
                    eq(TENANT_ID), eq(DEVICE_USER_ID), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(comprehensiveCounts);

            // Activity breakdown by event type
            List<Object[]> typeRows = new ArrayList<>();
            typeRows.add(new Object[]{"WEBSITE_VISIT", 10L});
            when(eventRepository.countEventsByTypeForDeviceUser(TENANT_ID, DEVICE_USER_ID)).thenReturn(typeRows);

            // Top domains
            when(eventRepository.getTopDomainsForDeviceUser(TENANT_ID, DEVICE_USER_ID, 10))
                    .thenReturn(Collections.emptyList());

            // Recent events
            when(eventRepository.findTop20ByDeviceUser_PkDeviceUserIdOrderByTimeStampDesc(DEVICE_USER_ID))
                    .thenReturn(Collections.emptyList());

            // Extensions (empty)
            when(installedExtensionRepository.findByDeviceDeviceIdInOrderByExtensionNameAsc(anyList()))
                    .thenReturn(Collections.emptyList());

            // File operation counts
            List<Object[]> fileCountRows = new ArrayList<>();
            fileCountRows.add(new Object[]{2L, 1L, 0L, 0L, 0L, 0L});
            when(eventRepository.getFileOperationCountsForDeviceUser(TENANT_ID, DEVICE_USER_ID)).thenReturn(fileCountRows);

            // Recent file operations
            when(eventRepository.findRecentFileOperationsByDeviceUserId(TENANT_ID, DEVICE_USER_ID, 10))
                    .thenReturn(Collections.emptyList());

            // Location stats
            when(eventRepository.getLocationStatsForDeviceUser(TENANT_ID, DEVICE_USER_ID))
                    .thenReturn(Collections.emptyList());

            // Recent policy violations
            when(eventRepository.findRecentPolicyViolationsByDeviceUserId(TENANT_ID, DEVICE_USER_ID, 10))
                    .thenReturn(Collections.emptyList());

            // Recent security events
            when(eventRepository.findRecentSecurityEventsByDeviceUserId(TENANT_ID, DEVICE_USER_ID, 10))
                    .thenReturn(Collections.emptyList());

            // ACT
            DeviceUserDetailsDTO result = service.getDeviceUserDetails(TENANT_ID, DEVICE_USER_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(DEVICE_USER_ID, result.getDeviceUserId());
            assertEquals(1, result.getDevices().size());
            assertEquals(24L, result.getTotalEvents());
            assertEquals(4L, result.getSecurityEvents());
            assertEquals(6L, result.getPolicyViolations());
            assertEquals(1L, result.getBlockedOperations());
            assertNotNull(result.getRiskAssessment());
            assertTrue(result.getRiskAssessment().getRiskLevel().length() > 0);
        }

        @Test
        @DisplayName("Sad Path – device user not found")
        void sadPath_notFound() {
            when(deviceUserRepository.findById(anyString())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getDeviceUserDetails(TENANT_ID, "bad"));
        }
    }
}