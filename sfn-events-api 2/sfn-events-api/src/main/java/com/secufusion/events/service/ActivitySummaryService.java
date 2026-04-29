package com.secufusion.events.service;

import com.secufusion.events.dto.DeviceActivitySummaryDTO;
import com.secufusion.events.dto.DeviceResponse;
import com.secufusion.events.dto.TenantActivitySummaryDTO;
import com.secufusion.events.entity.Device;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ActivitySummaryService {

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;

    /**
     * Get activity summary for a specific device.
     */
    public DeviceActivitySummaryDTO getDeviceActivitySummary(String tenantId, String deviceId) {
        log.info("Getting activity summary for device={} tenant={}", deviceId, tenantId);

        Device device = deviceRepository.findByDeviceIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeek = startOfDay.minusDays(7);
        LocalDateTime startOfMonth = startOfDay.minusDays(30);

        try {
            // Event counts
            long totalEvents = eventRepository.countEventsByDeviceAndTimeRange(deviceId,
                    device.getFirstSeenAt(), now);
            long eventsToday = eventRepository.countEventsByDeviceAndTimeRange(deviceId,
                    startOfDay, now);
            long eventsThisWeek = eventRepository.countEventsByDeviceAndTimeRange(deviceId,
                    startOfWeek, now);
            long eventsThisMonth = eventRepository.countEventsByDeviceAndTimeRange(deviceId,
                    startOfMonth, now);

            // Events by type
            Map<String, Long> eventsByType = getEventsByTypeForDevice(deviceId, startOfMonth, now);

            // Domain stats
            long uniqueDomains = eventRepository.countUniqueDomainsForDevice(deviceId, startOfMonth, now);
            Map<String, Long> topDomains = getTopDomainsForDevice(deviceId, startOfMonth, now, 10);

            // Duration
            long totalTimeSpent = eventRepository.sumDurationByDevice(deviceId, startOfMonth, now);

            // Policy violations
            long violationsToday = eventRepository.countPolicyViolationsForDevice(deviceId, startOfDay, now);
            long violationsThisWeek = eventRepository.countPolicyViolationsForDevice(deviceId, startOfWeek, now);

            return DeviceActivitySummaryDTO.builder()
                    .deviceId(deviceId)
                    .deviceName(device.getDeviceName())
                    .deviceType(device.getDeviceType())
                    .userName(device.getUserName())
                    .totalEvents(totalEvents)
                    .eventsToday(eventsToday)
                    .eventsThisWeek(eventsThisWeek)
                    .eventsThisMonth(eventsThisMonth)
                    .eventsByType(eventsByType)
                    .uniqueDomainsVisited(uniqueDomains)
                    .topDomains(topDomains)
                    .totalTimeSpentSeconds(totalTimeSpent)
                    .policyViolationsToday(violationsToday)
                    .policyViolationsThisWeek(violationsThisWeek)
                    .firstActivityAt(device.getFirstSeenAt())
                    .lastActivityAt(device.getLastSeenAt())
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get device activity summary for {}: {}", deviceId, e.getMessage());
            return DeviceActivitySummaryDTO.empty(deviceId);
        }
    }

    /**
     * Get activity summary for a tenant.
     */
    public TenantActivitySummaryDTO getTenantActivitySummary(String tenantId) {
        log.info("Getting activity summary for tenant={}", tenantId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeek = startOfDay.minusDays(7);
        LocalDateTime startOfMonth = startOfDay.minusDays(30);

        try {
            // Device stats
            Map<String, Long> deviceStats = deviceService.getDeviceStats(tenantId);
            Map<String, Long> devicesByType = deviceService.getDevicesByType(tenantId);

            // Event counts
            long eventsToday = eventRepository.countEventsByTenantAndTimeRange(tenantId,
                    startOfDay, now);
            long eventsThisWeek = eventRepository.countEventsByTenantAndTimeRange(tenantId,
                    startOfWeek, now);
            long eventsThisMonth = eventRepository.countEventsByTenantAndTimeRange(tenantId,
                    startOfMonth, now);

            // Events by type
            Map<String, Long> eventsByType = getEventsByTypeForTenant(tenantId, startOfMonth, now);

            // Active users
            long activeUsersToday = eventRepository.countActiveUsers(tenantId, startOfDay, now);
            long activeUsersThisWeek = eventRepository.countActiveUsers(tenantId, startOfWeek, now);

            // Top users
            List<TenantActivitySummaryDTO.TopUserDTO> topUsers = getTopActiveUsers(tenantId,
                    startOfWeek, now, 10);

            // Domain stats
            long uniqueDomains = eventRepository.countUniqueDomains(tenantId, startOfDay, now);
            Map<String, Long> topDomains = getTopDomains(tenantId, startOfWeek, now, 10);

            // Category stats
            Map<String, Long> domainsByCategory = getCategoryStats(tenantId, startOfWeek, now);

            // Policy violations
            long violationsToday = eventRepository.countPolicyViolations(tenantId, startOfDay, now);
            long violationsThisWeek = eventRepository.countPolicyViolations(tenantId, startOfWeek, now);
            Map<String, Long> violationsByType = getViolationsByType(tenantId, startOfWeek, now);

            // Recent events and devices
            List<TenantActivitySummaryDTO.RecentEventDTO> recentEvents =
                    getRecentEvents(tenantId, 10);
            List<DeviceResponse> recentDevices = deviceService.getRecentDevices(tenantId, 10);

            return TenantActivitySummaryDTO.builder()
                    .tenantId(tenantId)
                    .totalDevices(deviceStats.getOrDefault("total", 0L))
                    .activeDevices(deviceStats.getOrDefault("active", 0L))
                    .inactiveDevices(deviceStats.getOrDefault("inactive", 0L))
                    .blockedDevices(deviceStats.getOrDefault("blocked", 0L))
                    .devicesByType(devicesByType)
                    .totalEventsToday(eventsToday)
                    .totalEventsThisWeek(eventsThisWeek)
                    .totalEventsThisMonth(eventsThisMonth)
                    .eventsByType(eventsByType)
                    .activeUsersToday(activeUsersToday)
                    .activeUsersThisWeek(activeUsersThisWeek)
                    .topActiveUsers(topUsers)
                    .uniqueDomainsToday(uniqueDomains)
                    .topDomains(topDomains)
                    .domainsByCategory(domainsByCategory)
                    .violationsToday(violationsToday)
                    .violationsThisWeek(violationsThisWeek)
                    .violationsByType(violationsByType)
                    .recentEvents(recentEvents)
                    .recentDevices(recentDevices)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get tenant activity summary for {}: {}", tenantId, e.getMessage());
            return TenantActivitySummaryDTO.empty(tenantId);
        }
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Long> getEventsByTypeForDevice(String deviceId,
                                                       LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = eventRepository.countEventsByTypeForDevice(deviceId, start, end);
        return results.stream().collect(Collectors.toMap(
                row -> row[0] != null ? row[0].toString() : "UNKNOWN",
                row -> ((Number) row[1]).longValue(),
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    private Map<String, Long> getEventsByTypeForTenant(String tenantId,
                                                       LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = eventRepository.countEventsByType(tenantId, start, end);
        return results.stream().collect(Collectors.toMap(
                row -> row[0] != null ? row[0].toString() : "UNKNOWN",
                row -> ((Number) row[1]).longValue(),
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    private Map<String, Long> getTopDomainsForDevice(String deviceId, LocalDateTime start,
                                                     LocalDateTime end, int limit) {
        List<Object[]> results = eventRepository.getTopDomainsForDevice(deviceId, start, end,
                PageRequest.of(0, limit));
        Map<String, Long> domains = new LinkedHashMap<>();
        for (Object[] row : results) {
            if (row[0] != null) {
                domains.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        return domains;
    }

    private Map<String, Long> getTopDomains(String tenantId, LocalDateTime start,
                                            LocalDateTime end, int limit) {
        List<Object[]> results = eventRepository.getTopDomains(tenantId, start, end,
                PageRequest.of(0, limit));
        Map<String, Long> domains = new LinkedHashMap<>();
        for (Object[] row : results) {
            if (row[0] != null) {
                domains.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        return domains;
    }

    private List<TenantActivitySummaryDTO.TopUserDTO> getTopActiveUsers(String tenantId,
                                                                        LocalDateTime start, LocalDateTime end, int limit) {
        List<Object[]> results = eventRepository.getTopActiveUsers(tenantId, start, end,
                PageRequest.of(0, limit));
        return results.stream()
                .map(row -> TenantActivitySummaryDTO.TopUserDTO.builder()
                        .userName(row[0] != null ? row[0].toString() : null)
                        .eventCount(((Number) row[1]).longValue())
                        .lastActivityAt(convertToLocalDateTime(row[2]))
                        .build())
                .toList();
    }

    private Map<String, Long> getCategoryStats(String tenantId, LocalDateTime start,
                                               LocalDateTime end) {
        List<Object[]> results = eventRepository.countEventsByCategory(tenantId, start, end);
        Map<String, Long> categories = new LinkedHashMap<>();
        for (Object[] row : results) {
            if (row[0] != null) {
                categories.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        return categories;
    }

    private Map<String, Long> getViolationsByType(String tenantId, LocalDateTime start,
                                                  LocalDateTime end) {
        List<Object[]> results = eventRepository.countViolationsByType(tenantId, start, end);
        return results.stream().collect(Collectors.toMap(
                row -> row[0] != null ? row[0].toString() : "UNKNOWN",
                row -> ((Number) row[1]).longValue(),
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    private List<TenantActivitySummaryDTO.RecentEventDTO> getRecentEvents(String tenantId,
                                                                          int limit) {
        List<Object[]> results = eventRepository.findRecentEvents(tenantId, limit);
        return results.stream()
                .map(row -> TenantActivitySummaryDTO.RecentEventDTO.builder()
                        .eventId(row[0] != null ? row[0].toString() : null)
                        .eventType(row[1] != null ? row[1].toString() : null)
                        .url(row[2] != null ? row[2].toString() : null)
                        .title(row[3] != null ? row[3].toString() : null)
                        .userName(row[4] != null ? row[4].toString() : null)
                        .deviceId(row[5] != null ? row[5].toString() : null)
                        .timestamp(convertToLocalDateTime(row[6]))
                        .build())
                .toList();
    }

    /**
     * Convert various timestamp types to LocalDateTime.
     * Handles Instant, OffsetDateTime, java.sql.Timestamp, and LocalDateTime.
     */
    private LocalDateTime convertToLocalDateTime(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof LocalDateTime) {
            return (LocalDateTime) timestamp;
        } else if (timestamp instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timestamp).toLocalDateTime();
        } else if (timestamp instanceof Instant) {
            return LocalDateTime.ofInstant((Instant) timestamp, ZoneId.systemDefault());
        } else if (timestamp instanceof OffsetDateTime) {
            return ((OffsetDateTime) timestamp).toLocalDateTime();
        } else if (timestamp instanceof ZonedDateTime) {
            return ((ZonedDateTime) timestamp).toLocalDateTime();
        }
        return null;
    }
}
