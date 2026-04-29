package com.secufusion.events.service;

import com.secufusion.events.dto.DeviceActivitySummaryDTO;
import com.secufusion.events.dto.TenantActivitySummaryDTO;
import com.secufusion.events.entity.Device;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivitySummaryServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private ActivitySummaryService activitySummaryService;

    private Device mockDevice;

    @BeforeEach
    void setUp() {
        mockDevice = new Device();
        mockDevice.setDeviceId("device-001");
        mockDevice.setDeviceName("Test Laptop");
        mockDevice.setDeviceType("LAPTOP");
        mockDevice.setUserName("john.doe");
        mockDevice.setTenantId("tenant-001");
        mockDevice.setFirstSeenAt(LocalDateTime.now().minusDays(30));
        mockDevice.setLastSeenAt(LocalDateTime.now());
    }

    // =====================================================
    // TEST GROUP 1: getDeviceActivitySummary - Happy Path
    // =====================================================

    @Test
    void getDeviceActivitySummary_ShouldReturnSummary_WhenDeviceExists() {

        // ARRANGE
        when(deviceRepository.findByDeviceIdAndTenantId("device-001", "tenant-001"))
                .thenReturn(Optional.of(mockDevice));

        when(eventRepository.countEventsByDeviceAndTimeRange(anyString(), any(), any()))
                .thenReturn(500L, 10L, 75L, 200L);

        List<Object[]> eventsByType = new ArrayList<>();
        eventsByType.add(new Object[]{"PAGE_VISIT", 150L});
        when(eventRepository.countEventsByTypeForDevice(anyString(), any(), any()))
                .thenReturn(eventsByType);

        when(eventRepository.countUniqueDomainsForDevice(anyString(), any(), any()))
                .thenReturn(25L);

        List<Object[]> topDomainsDevice = new ArrayList<>();
        topDomainsDevice.add(new Object[]{"google.com", 50L});
        when(eventRepository.getTopDomainsForDevice(anyString(), any(), any(), any()))
                .thenReturn(topDomainsDevice);

        when(eventRepository.sumDurationByDevice(anyString(), any(), any()))
                .thenReturn(3600L);

        when(eventRepository.countPolicyViolationsForDevice(anyString(), any(), any()))
                .thenReturn(2L, 5L);

        // ACT
        DeviceActivitySummaryDTO result =
                activitySummaryService.getDeviceActivitySummary("tenant-001", "device-001");

        // ASSERT
        assertNotNull(result);
        assertEquals("device-001", result.getDeviceId());
        assertEquals("Test Laptop", result.getDeviceName());
        assertEquals(500L, result.getTotalEvents());
        assertEquals(10L, result.getEventsToday());
        assertEquals(25L, result.getUniqueDomainsVisited());
        assertEquals(3600L, result.getTotalTimeSpentSeconds());
        assertEquals(2L, result.getPolicyViolationsToday());
        assertNotNull(result.getGeneratedAt());

        verify(deviceRepository).findByDeviceIdAndTenantId("device-001", "tenant-001");
        verify(eventRepository, times(4)).countEventsByDeviceAndTimeRange(anyString(), any(), any());
    }

    // =====================================================
    // TEST GROUP 2: getDeviceActivitySummary - Sad Path
    // =====================================================

    @Test
    void getDeviceActivitySummary_ShouldThrowException_WhenDeviceNotFound() {

        // ARRANGE
        when(deviceRepository.findByDeviceIdAndTenantId("wrong-id", "tenant-001"))
                .thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(ResourceNotFoundException.class, () ->
                activitySummaryService.getDeviceActivitySummary("tenant-001", "wrong-id")
        );

        verify(deviceRepository).findByDeviceIdAndTenantId("wrong-id", "tenant-001");
        verifyNoInteractions(eventRepository);
    }

    @Test
    void getDeviceActivitySummary_ShouldReturnEmptyDTO_WhenRepositoryThrowsException() {

        // ARRANGE
        when(deviceRepository.findByDeviceIdAndTenantId(anyString(), anyString()))
                .thenReturn(Optional.of(mockDevice));

        when(eventRepository.countEventsByDeviceAndTimeRange(anyString(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        // ACT
        DeviceActivitySummaryDTO result =
                activitySummaryService.getDeviceActivitySummary("tenant-001", "device-001");

        // ASSERT
        assertNotNull(result);
        assertEquals("device-001", result.getDeviceId());
        assertEquals(0L, result.getTotalEvents());
    }

    // =====================================================
    // TEST GROUP 3: getTenantActivitySummary - Happy Path
    // =====================================================

    @Test
    void getTenantActivitySummary_ShouldReturnSummary_WhenTenantExists() {

        // ARRANGE
        Map<String, Long> deviceStats = Map.of(
                "total", 50L, "active", 35L, "inactive", 10L, "blocked", 5L
        );
        Map<String, Long> devicesByType = Map.of("LAPTOP", 30L, "MOBILE", 20L);

        when(deviceService.getDeviceStats("tenant-001")).thenReturn(deviceStats);
        when(deviceService.getDevicesByType("tenant-001")).thenReturn(devicesByType);
        when(deviceService.getRecentDevices(eq("tenant-001"), eq(10))).thenReturn(Collections.emptyList());

        when(eventRepository.countEventsByTenantAndTimeRange(anyString(), any(), any()))
                .thenReturn(100L, 700L, 3000L);

        List<Object[]> eventsByTypeTenant = new ArrayList<>();
        eventsByTypeTenant.add(new Object[]{"PAGE_VISIT", 200L});
        when(eventRepository.countEventsByType(anyString(), any(), any()))
                .thenReturn(eventsByTypeTenant);

        when(eventRepository.countActiveUsers(anyString(), any(), any()))
                .thenReturn(15L, 40L);

        // ✅ topUsers properly passed to mock
        List<Object[]> topUsers = new ArrayList<>();
        topUsers.add(new Object[]{"john.doe", 100L, LocalDateTime.now()});
        when(eventRepository.getTopActiveUsers(anyString(), any(), any(), any()))
                .thenReturn(topUsers);

        when(eventRepository.countUniqueDomains(anyString(), any(), any()))
                .thenReturn(80L);

        List<Object[]> topDomainsTenant = new ArrayList<>();
        topDomainsTenant.add(new Object[]{"github.com", 100L});
        when(eventRepository.getTopDomains(anyString(), any(), any(), any()))
                .thenReturn(topDomainsTenant);

        // ✅ categoryStats with real data
        List<Object[]> categoryStats = new ArrayList<>();
        categoryStats.add(new Object[]{"SOCIAL", 30L});
        when(eventRepository.countEventsByCategory(anyString(), any(), any()))
                .thenReturn(categoryStats);

        when(eventRepository.countPolicyViolations(anyString(), any(), any()))
                .thenReturn(3L, 12L);

        // ✅ violations properly passed to mock
        List<Object[]> violations = new ArrayList<>();
        violations.add(new Object[]{"BLOCKED_SITE", 5L});
        when(eventRepository.countViolationsByType(anyString(), any(), any()))
                .thenReturn(violations);

        // ✅ recentEvents properly passed to mock
        List<Object[]> recentEvents = new ArrayList<>();
        recentEvents.add(new Object[]{
                "evt-1", "PAGE_VISIT", "https://google.com",
                "Google", "john.doe", "device-001", LocalDateTime.now()
        });
        when(eventRepository.findRecentEvents(anyString(), anyInt()))
                .thenReturn(recentEvents);

        // ACT
        TenantActivitySummaryDTO result =
                activitySummaryService.getTenantActivitySummary("tenant-001");

        // ASSERT
        assertNotNull(result);
        assertEquals("tenant-001", result.getTenantId());
        assertEquals(50L, result.getTotalDevices());
        assertEquals(35L, result.getActiveDevices());
        assertEquals(100L, result.getTotalEventsToday());
        assertEquals(700L, result.getTotalEventsThisWeek());
        assertEquals(15L, result.getActiveUsersToday());
        assertEquals(80L, result.getUniqueDomainsToday());
        assertEquals(3L, result.getViolationsToday());
        assertNotNull(result.getGeneratedAt());
    }

    // =====================================================
    // TEST GROUP 4: getTenantActivitySummary - Sad Path
    // =====================================================

    @Test
    void getTenantActivitySummary_ShouldReturnEmptyDTO_WhenExceptionOccurs() {

        // ARRANGE
        when(deviceService.getDeviceStats(anyString()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // ACT
        TenantActivitySummaryDTO result =
                activitySummaryService.getTenantActivitySummary("tenant-001");

        // ASSERT
        assertNotNull(result);
        assertEquals("tenant-001", result.getTenantId());
    }
}