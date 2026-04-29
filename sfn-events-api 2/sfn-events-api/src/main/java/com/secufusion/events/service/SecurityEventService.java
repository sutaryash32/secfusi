package com.secufusion.events.service;

import com.secufusion.events.dto.SecurityDashboardDTO;
import com.secufusion.events.dto.SecurityEventDTO;
import com.secufusion.events.dto.SecurityEventStatsDTO;
import com.secufusion.events.entity.Event;
import com.secufusion.events.entity.Incident;
import com.secufusion.events.entity.IncidentEvent;
import com.secufusion.events.repository.EventRepository;
import com.secufusion.events.repository.IncidentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for security event tracking and analytics.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityEventService {

    private final EventRepository eventRepository;
    private final IncidentEventRepository incidentEventRepository;

    /**
     * Get all security events for a tenant with pagination.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventDTO> getSecurityEvents(String tenantId, int page, int size) {
        log.debug("getSecurityEvents - tenantId={} page={} size={}", tenantId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> events = eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueOrderByTimeStampDesc(tenantId, pageable);
        Map<String, Incident> incidentMap = buildIncidentMap(events.getContent(), tenantId);
        return events.map(e -> convertToSecurityEventDTO(e, incidentMap));
    }

    /**
     * Get security events with filters.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventDTO> getSecurityEventsWithFilters(
            String tenantId,
            String severity,
            String threatType,
            String riskLevel,
            String actionTaken,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size) {

        log.debug("getSecurityEventsWithFilters - tenantId={} severity={} threatType={} riskLevel={} actionTaken={}",
                tenantId, severity, threatType, riskLevel, actionTaken);

        Pageable pageable = PageRequest.of(page, size);
        Page<Event> events = eventRepository.findSecurityEventsWithFilters(
                tenantId, severity, threatType, riskLevel, actionTaken, start, end, pageable);
        Map<String, Incident> incidentMap = buildIncidentMap(events.getContent(), tenantId);
        return events.map(e -> convertToSecurityEventDTO(e, incidentMap));
    }

    /**
     * Get security events by severity.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventDTO> getSecurityEventsBySeverity(String tenantId, String severity, int page, int size) {
        log.debug("getSecurityEventsBySeverity - tenantId={} severity={}", tenantId, severity);
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> events = eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndSeverityOrderByTimeStampDesc(
                tenantId, severity, pageable);
        Map<String, Incident> incidentMap = buildIncidentMap(events.getContent(), tenantId);
        return events.map(e -> convertToSecurityEventDTO(e, incidentMap));
    }

    /**
     * Get security events by threat type.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventDTO> getSecurityEventsByThreatType(String tenantId, String threatType, int page, int size) {
        log.debug("getSecurityEventsByThreatType - tenantId={} threatType={}", tenantId, threatType);
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> events = eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndThreatTypeOrderByTimeStampDesc(
                tenantId, threatType, pageable);
        Map<String, Incident> incidentMap = buildIncidentMap(events.getContent(), tenantId);
        return events.map(e -> convertToSecurityEventDTO(e, incidentMap));
    }

    /**
     * Get security events by risk level.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventDTO> getSecurityEventsByRiskLevel(String tenantId, String riskLevel, int page, int size) {
        log.debug("getSecurityEventsByRiskLevel - tenantId={} riskLevel={}", tenantId, riskLevel);
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> events = eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndRiskLevelOrderByTimeStampDesc(
                tenantId, riskLevel, pageable);
        Map<String, Incident> incidentMap = buildIncidentMap(events.getContent(), tenantId);
        return events.map(e -> convertToSecurityEventDTO(e, incidentMap));
    }

    /**
     * Get security events for a user.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventDTO> getSecurityEventsByUser(String tenantId, String userName, int page, int size) {
        log.debug("getSecurityEventsByUser - tenantId={} userName={}", tenantId, userName);
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> events = eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndUserNameOrderByTimeStampDesc(
                tenantId, userName, pageable);
        Map<String, Incident> incidentMap = buildIncidentMap(events.getContent(), tenantId);
        return events.map(e -> convertToSecurityEventDTO(e, incidentMap));
    }

    /**
     * Get security events for a device.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventDTO> getSecurityEventsByDevice(String deviceId, int page, int size) {
        log.debug("getSecurityEventsByDevice - deviceId={}", deviceId);
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findByDevice_DeviceIdAndIsSecurityEventTrueOrderByTimeStampDesc(deviceId, pageable)
                .map(e -> convertToSecurityEventDTO(e, Map.of()));
    }

    /**
     * Get security events within a time range.
     */
    @Transactional(readOnly = true)
    public List<SecurityEventDTO> getSecurityEventsByTimeRange(String tenantId, LocalDateTime start, LocalDateTime end) {
        log.debug("getSecurityEventsByTimeRange - tenantId={} start={} end={}", tenantId, start, end);
        List<Event> events = eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndTimeStampBetweenOrderByTimeStampDesc(
                tenantId, start, end);
        Map<String, Incident> incidentMap = buildIncidentMap(events, tenantId);
        return events.stream()
                .map(e -> convertToSecurityEventDTO(e, incidentMap))
                .collect(Collectors.toList());
    }

    /**
     * Get critical security events.
     */
    @Transactional(readOnly = true)
    public List<SecurityEventDTO> getCriticalSecurityEvents(String tenantId, LocalDateTime start, LocalDateTime end) {
        log.debug("getCriticalSecurityEvents - tenantId={} start={} end={}", tenantId, start, end);
        List<Event> events = eventRepository.findCriticalSecurityEvents(tenantId, start, end);
        Map<String, Incident> incidentMap = buildIncidentMap(events, tenantId);
        return events.stream()
                .map(e -> convertToSecurityEventDTO(e, incidentMap))
                .collect(Collectors.toList());
    }

    /**
     * Get security event statistics.
     */
    @Transactional(readOnly = true)
    public SecurityEventStatsDTO getSecurityEventStats(String tenantId, LocalDateTime start, LocalDateTime end) {
        log.debug("getSecurityEventStats - tenantId={} start={} end={}", tenantId, start, end);

        // Count by severity
        Map<String, Long> bySeverity = new HashMap<>();
        eventRepository.countSecurityEventsBySeverity(tenantId, start, end).forEach(row -> {
            String severity = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            bySeverity.put(severity, count);
        });

        // Count by threat type
        Map<String, Long> byThreatType = new HashMap<>();
        eventRepository.countSecurityEventsByThreatType(tenantId, start, end).forEach(row -> {
            String threatType = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            byThreatType.put(threatType, count);
        });

        // Count by risk level
        Map<String, Long> byRiskLevel = new HashMap<>();
        eventRepository.countSecurityEventsByRiskLevel(tenantId, start, end).forEach(row -> {
            String riskLevel = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            byRiskLevel.put(riskLevel, count);
        });

        // Count by action taken
        Map<String, Long> byActionTaken = new HashMap<>();
        eventRepository.countSecurityEventsByActionTaken(tenantId, start, end).forEach(row -> {
            String actionTaken = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            byActionTaken.put(actionTaken, count);
        });

        // Count by policy type
        Map<String, Long> byPolicyType = new HashMap<>();
        eventRepository.countSecurityEventsByPolicyType(tenantId, start, end).forEach(row -> {
            String policyType = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            byPolicyType.put(policyType, count);
        });

        // Top users with security events
        List<SecurityEventStatsDTO.TopUserDTO> topUsers = eventRepository.getTopUsersWithSecurityEvents(
                        tenantId, start, end, PageRequest.of(0, 10))
                .stream()
                .map(row -> SecurityEventStatsDTO.TopUserDTO.builder()
                        .userName(row[0] != null ? row[0].toString() : "unknown")
                        .eventCount(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Top threat types
        List<SecurityEventStatsDTO.TopThreatDTO> topThreats = eventRepository.getTopThreatTypes(
                        tenantId, start, end, PageRequest.of(0, 10))
                .stream()
                .map(row -> SecurityEventStatsDTO.TopThreatDTO.builder()
                        .threatType(row[0] != null ? row[0].toString() : "unknown")
                        .eventCount(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Daily trends
        List<SecurityEventStatsDTO.DailySecurityTrendDTO> dailyTrends = eventRepository.getDailySecurityEventTrends(
                        tenantId, start, end)
                .stream()
                .map(row -> SecurityEventStatsDTO.DailySecurityTrendDTO.builder()
                        .date(row[0] != null ? row[0].toString() : "")
                        .totalEvents(((Number) row[1]).longValue())
                        .criticalCount(((Number) row[2]).longValue())
                        .highCount(((Number) row[3]).longValue())
                        .mediumCount(((Number) row[4]).longValue())
                        .lowCount(((Number) row[5]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Recent critical events
        List<Event> criticalEvents = eventRepository.findCriticalSecurityEvents(tenantId, start, end)
                .stream().limit(10).collect(Collectors.toList());
        Map<String, Incident> criticalIncidentMap = buildIncidentMap(criticalEvents, tenantId);
        List<SecurityEventDTO> recentCritical = criticalEvents.stream()
                .map(e -> convertToSecurityEventDTO(e, criticalIncidentMap))
                .collect(Collectors.toList());

        // Calculate totals
        long total = eventRepository.countSecurityEventsByTimeRange(tenantId, start, end);
        long critical = bySeverity.getOrDefault("critical", 0L);
        long high = bySeverity.getOrDefault("high", 0L);
        long medium = bySeverity.getOrDefault("medium", 0L);
        long low = bySeverity.getOrDefault("low", 0L);

        return SecurityEventStatsDTO.builder()
                .totalSecurityEvents(total)
                .criticalCount(critical)
                .highCount(high)
                .mediumCount(medium)
                .lowCount(low)
                .bySeverity(bySeverity)
                .byThreatType(byThreatType)
                .byRiskLevel(byRiskLevel)
                .byActionTaken(byActionTaken)
                .byPolicyType(byPolicyType)
                .topUsersWithSecurityEvents(topUsers)
                .topThreatTypes(topThreats)
                .dailyTrends(dailyTrends)
                .recentCriticalEvents(recentCritical)
                .build();
    }

    /**
     * Get count of security events.
     */
    @Transactional(readOnly = true)
    public long countSecurityEvents(String tenantId) {
        return eventRepository.countSecurityEvents(tenantId);
    }

    /**
     * Get count of security events within a time range.
     */
    @Transactional(readOnly = true)
    public long countSecurityEventsByTimeRange(String tenantId, LocalDateTime start, LocalDateTime end) {
        return eventRepository.countSecurityEventsByTimeRange(tenantId, start, end);
    }

    /**
     * Get security dashboard data for UI.
     * Uses date range filtering with start and end dates.
     */
    @Transactional(readOnly = true)
    public SecurityDashboardDTO getSecurityDashboard(String tenantId,
                                                      LocalDateTime start, LocalDateTime end,
                                                      int page, int size,
                                                      String eventTypeFilter, String searchTerm) {
        log.debug("getSecurityDashboard - tenantId={} start={} end={} page={} size={} eventType={} search={}",
                tenantId, start, end, page, size, eventTypeFilter, searchTerm);

        // Calculate previous period for change percentage (same duration as selected range)
        long periodDuration = java.time.Duration.between(start, end).toHours();
        LocalDateTime prevStart = start.minusHours(periodDuration);

        // Get summary stats
        SecurityDashboardDTO.SummaryStats summary = calculateSummaryStats(tenantId, start, end, prevStart);

        // Get event type breakdown
        List<SecurityDashboardDTO.EventTypeCount> eventsByType = getEventTypeBreakdown(tenantId, start, end);

        // Get recent events with pagination
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> eventsPage = getFilteredSecurityEvents(tenantId, start, end, eventTypeFilter, searchTerm, pageable);

        Map<String, Incident> dashboardIncidentMap = buildIncidentMap(eventsPage.getContent(), tenantId);
        List<SecurityDashboardDTO.RecentEventDTO> recentEvents = eventsPage.getContent().stream()
                .map(e -> convertToRecentEventDTO(e, dashboardIncidentMap))
                .collect(Collectors.toList());

        // Build pagination info
        SecurityDashboardDTO.PaginationInfo pagination = SecurityDashboardDTO.PaginationInfo.builder()
                .page(page)
                .size(size)
                .totalElements(eventsPage.getTotalElements())
                .totalPages(eventsPage.getTotalPages())
                .build();

        return SecurityDashboardDTO.builder()
                .summary(summary)
                .eventsByType(eventsByType)
                .recentEvents(recentEvents)
                .pagination(pagination)
                .period(null)  // No longer using period-based filtering
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Calculate summary statistics.
     */
    private SecurityDashboardDTO.SummaryStats calculateSummaryStats(String tenantId, LocalDateTime start,
                                                                     LocalDateTime end, LocalDateTime prevStart) {
        // Current period counts
        long totalEvents = eventRepository.countSecurityEventsByTimeRange(tenantId, start, end);
        long criticalEvents = eventRepository.countByTenantIdAndSeverityAndTimeRange(tenantId, "critical", start, end);
        long highEvents = eventRepository.countByTenantIdAndSeverityAndTimeRange(tenantId, "high", start, end);
        long mediumEvents = eventRepository.countByTenantIdAndSeverityAndTimeRange(tenantId, "medium", start, end);
        long lowEvents = eventRepository.countByTenantIdAndSeverityAndTimeRange(tenantId, "low", start, end);

        // Previous period count for change calculation
        long prevTotalEvents = eventRepository.countSecurityEventsByTimeRange(tenantId, prevStart, start);
        double changePercentage = prevTotalEvents > 0 ?
                ((double) (totalEvents - prevTotalEvents) / prevTotalEvents) * 100 : 0.0;

        return SecurityDashboardDTO.SummaryStats.builder()
                .totalEvents(totalEvents)
                .totalEventsChange(Math.round(changePercentage * 10.0) / 10.0)  // Round to 1 decimal
                .criticalEvents(criticalEvents)
                .highPriorityEvents(highEvents)
                .mediumLowEvents(mediumEvents + lowEvents)
                .build();
    }

    /**
     * Get event type breakdown for sidebar.
     */
    private List<SecurityDashboardDTO.EventTypeCount> getEventTypeBreakdown(String tenantId,
                                                                             LocalDateTime start, LocalDateTime end) {
        List<SecurityDashboardDTO.EventTypeCount> result = new ArrayList<>();

        // Count security threats (severity = critical or high)
        long securityThreats = eventRepository.countByTenantIdAndSeverityInAndTimeRange(
                tenantId, List.of("critical", "high"), start, end);
        result.add(SecurityDashboardDTO.EventTypeCount.builder()
                .type("SECURITY_THREAT")
                .label("Security Threat")
                .count(securityThreats)
                .color("#EF4444")  // Red
                .build());

        // Count policy violations
        long policyViolations = eventRepository.countPolicyViolationsByTimeRange(tenantId, start, end);
        result.add(SecurityDashboardDTO.EventTypeCount.builder()
                .type("POLICY_VIOLATION")
                .label("Policy Violation")
                .count(policyViolations)
                .color("#F59E0B")  // Orange
                .build());

        // Count DLP alerts (threatType contains 'dlp' or actionTaken = 'blocked' for file operations)
        long dlpAlerts = eventRepository.countDlpAlertsByTimeRange(tenantId, start, end);
        result.add(SecurityDashboardDTO.EventTypeCount.builder()
                .type("DLP_ALERT")
                .label("DLP Alert")
                .count(dlpAlerts)
                .color("#3B82F6")  // Blue
                .build());

        // Count compliance events
        long complianceEvents = eventRepository.countComplianceEventsByTimeRange(tenantId, start, end);
        result.add(SecurityDashboardDTO.EventTypeCount.builder()
                .type("COMPLIANCE")
                .label("Compliance")
                .count(complianceEvents)
                .color("#10B981")  // Green
                .build());

        return result;
    }

    /**
     * Get filtered security events.
     */
    private Page<Event> getFilteredSecurityEvents(String tenantId, LocalDateTime start, LocalDateTime end,
                                                   String eventTypeFilter, String searchTerm, Pageable pageable) {
        if (searchTerm != null && !searchTerm.isBlank()) {
            return eventRepository.searchSecurityEvents(tenantId, searchTerm, start, end, pageable);
        } else if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
            return switch (eventTypeFilter) {
                case "SECURITY_THREAT" -> eventRepository.findSecurityThreats(tenantId, start, end, pageable);
                case "POLICY_VIOLATION" -> eventRepository.findPolicyViolations(tenantId, start, end, pageable);
                case "DLP_ALERT" -> eventRepository.findDlpAlerts(tenantId, start, end, pageable);
                case "COMPLIANCE" -> eventRepository.findComplianceEvents(tenantId, start, end, pageable);
                default -> eventRepository.findSecurityEventsByTimeRange(tenantId, start, end, pageable);
            };
        }
        return eventRepository.findSecurityEventsByTimeRange(tenantId, start, end, pageable);
    }

    /**
     * Convert Event to RecentEventDTO for dashboard.
     */
    private SecurityDashboardDTO.RecentEventDTO convertToRecentEventDTO(Event event, Map<String, Incident> incidentMap) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd");

        String typeLabel = getEventTypeLabel(event);
        String type = getEventType(event);

        SecurityDashboardDTO.RecentEventDTO.RecentEventDTOBuilder builder = SecurityDashboardDTO.RecentEventDTO.builder()
                .eventId(event.getPkEventId())
                .time(event.getTimeStamp())
                .timeDisplay(event.getTimeStamp() != null ? event.getTimeStamp().format(timeFormatter) : "")
                .dateDisplay(event.getTimeStamp() != null ? event.getTimeStamp().format(dateFormatter) : "")
                .type(type)
                .typeLabel(typeLabel)
                .deviceId(event.getDevice() != null ? event.getDevice().getDeviceId() : null)
                .deviceName(event.getDevice() != null ? event.getDevice().getDeviceName() : "Unknown Device")
                .severity(event.getSeverity() != null ? event.getSeverity().toLowerCase() : "medium")
                .severityLabel(capitalizeFirst(event.getSeverity()))
                .details(generateEventDetails(event))
                .url(shortenUrl(event.getDomain()))
                .fullUrl(event.getUrl())
                .userName(event.getUserName())
                .actionTaken(event.getActionTaken())
                .threatType(event.getThreatType())
                .policyName(event.getPolicyName());

        // Incident info
        Incident incident = incidentMap.get(event.getPkEventId());
        if (incident != null) {
            builder.incidentId(incident.getPkIncidentId())
                    .incidentNumber(incident.getIncidentNumber())
                    .incidentStatus(incident.getStatus() != null ? incident.getStatus().name() : null)
                    .hasIncident(true);
        } else {
            builder.hasIncident(false);
        }

        return builder.build();
    }

    /**
     * Get event type for UI categorization.
     */
    private String getEventType(Event event) {
        if ("critical".equalsIgnoreCase(event.getSeverity()) || "high".equalsIgnoreCase(event.getSeverity())) {
            return "SECURITY_THREAT";
        } else if (Boolean.TRUE.equals(event.getIsPolicyViolation())) {
            return "POLICY_VIOLATION";
        } else if (event.getThreatType() != null && event.getThreatType().toLowerCase().contains("dlp")) {
            return "DLP_ALERT";
        } else if (event.getComplianceImpact() != null) {
            return "COMPLIANCE";
        }
        return "SECURITY_THREAT";
    }

    /**
     * Get event type label for display.
     */
    private String getEventTypeLabel(Event event) {
        String type = getEventType(event);
        return switch (type) {
            case "SECURITY_THREAT" -> "Security Threat";
            case "POLICY_VIOLATION" -> "Policy Violation";
            case "DLP_ALERT" -> "Data Leak Prevention";
            case "COMPLIANCE" -> "Compliance";
            default -> "Security Event";
        };
    }

    /**
     * Generate event details text.
     */
    private String generateEventDetails(Event event) {
        if (event.getThreatType() != null) {
            return switch (event.getThreatType().toLowerCase()) {
                case "phishing" -> "Phishing attempt";
                case "malware" -> "Malware detected";
                case "csp_violation" -> "CSP violation";
                case "xss" -> "XSS attempt";
                default -> event.getThreatType();
            };
        } else if (Boolean.TRUE.equals(event.getIsPolicyViolation())) {
            return event.getPolicyName() != null ? "Policy: " + event.getPolicyName() : "Policy violation";
        } else if (Boolean.TRUE.equals(event.getIsBlocked())) {
            return "Blocked action";
        }
        return "Security event";
    }

    /**
     * Shorten URL for display.
     */
    private String shortenUrl(String url) {
        if (url == null) return "";
        // Remove protocol and www
        String shortened = url.replaceFirst("^(https?://)?(www\\.)?", "");
        // Truncate if too long
        if (shortened.length() > 30) {
            shortened = shortened.substring(0, 27) + "...";
        }
        return shortened;
    }

    /**
     * Capitalize first letter.
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return "Medium";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Get event detail by ID for modal view.
     */
    @Transactional(readOnly = true)
    public SecurityDashboardDTO.EventDetailDTO getEventDetail(String tenantId, String eventId) {
        log.debug("getEventDetail - tenantId={} eventId={}", tenantId, eventId);

        return eventRepository.findByPkEventIdAndTenant_TenantID(eventId, tenantId)
                .map(event -> {
                    SecurityDashboardDTO.EventDetailDTO dto = convertToEventDetailDTO(event);
                    // Look up incident for this single event
                    incidentEventRepository.findFirstByEvent_PkEventIdAndTenantIdOrderByLinkedAtDesc(eventId, tenantId)
                            .ifPresent(ie -> {
                                Incident incident = ie.getIncident();
                                dto.setIncidentId(incident.getPkIncidentId());
                                dto.setIncidentNumber(incident.getIncidentNumber());
                                dto.setIncidentStatus(incident.getStatus() != null ? incident.getStatus().name() : null);
                                dto.setIncidentPriority(incident.getPriority() != null ? incident.getPriority().name() : null);
                                dto.setIncidentTitle(incident.getTitle());
                                dto.setHasIncident(true);
                            });
                    if (dto.getHasIncident() == null) {
                        dto.setHasIncident(false);
                    }
                    return dto;
                })
                .orElse(null);
    }

    /**
     * Get filter options for dashboard.
     */
    @Transactional(readOnly = true)
    public SecurityDashboardDTO.FilterOptions getFilterOptions(String tenantId) {
        log.debug("getFilterOptions - tenantId={}", tenantId);

        List<String> eventTypes = List.of("SECURITY_THREAT", "POLICY_VIOLATION", "DLP_ALERT", "COMPLIANCE");
        List<String> severityLevels = List.of("critical", "high", "medium", "low");
        List<String> riskLevels = List.of("Critical", "High", "Medium", "Low");
        List<String> actionTypes = List.of("blocked", "warned", "allowed");

        // Get distinct devices for tenant
        List<String> devices = eventRepository.findDistinctDeviceNamesByTenantId(tenantId);

        // Get distinct users for tenant
        List<String> users = eventRepository.findDistinctUserNamesByTenantId(tenantId);

        return SecurityDashboardDTO.FilterOptions.builder()
                .eventTypes(eventTypes)
                .severityLevels(severityLevels)
                .riskLevels(riskLevels)
                .actionTypes(actionTypes)
                .devices(devices)
                .users(users)
                .build();
    }

    /**
     * Convert Event to EventDetailDTO for modal view.
     * Includes ALL possible data from Event, Device, and Auditable entities.
     */
    private SecurityDashboardDTO.EventDetailDTO convertToEventDetailDTO(Event event) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a");
        DateTimeFormatter instantFormatter = DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a")
                .withZone(java.time.ZoneId.systemDefault());

        SecurityDashboardDTO.EventDetailDTO.EventDetailDTOBuilder builder = SecurityDashboardDTO.EventDetailDTO.builder();

        // ==================== BASIC INFORMATION ====================
        builder.eventId(event.getPkEventId())
                .eventType(event.getEventType() != null ? event.getEventType().name() : getEventType(event))
                .eventTypeLabel(getEventTypeLabel(event))
                .timestamp(event.getTimeStamp() != null ? event.getTimeStamp().format(formatter) : null)
                .url(event.getUrl())
                .domain(event.getDomain())
                .title(event.getTitle())
                .category(event.getCategory())
                .sessionId(null)  // Not tracked currently
                .durationSeconds(event.getDurationSeconds());

        // ==================== EVENT CLASSIFICATION ====================
        builder.isSecurityEvent(Boolean.TRUE.equals(event.getIsSecurityEvent()))
                .isPolicyViolation(Boolean.TRUE.equals(event.getIsPolicyViolation()))
                .isBlocked(Boolean.TRUE.equals(event.getIsBlocked()))
                .riskLevel(event.getRiskLevel())
                .complianceImpact(event.getComplianceImpact())
                .processingStatus(event.getProcessingStatus() != null ? event.getProcessingStatus() : "Pending")
                .mitreMapping(event.getMitreMapping());

        // ==================== THREAT DETAILS ====================
        builder.threatType(event.getThreatType())
                .threatLevel(event.getThreatLevel())
                .severity(event.getSeverity())
                .actionTaken(event.getActionTaken());

        // ==================== POLICY DETAILS ====================
        builder.policyRuleId(event.getPolicyRuleId())
                .policyName(event.getPolicyName())
                .policyType(event.getPolicyType())
                .filterType(event.getFilterType())
                .patternType(event.getPatternType())
                .matchedPattern(event.getMatchedPattern());

        // ==================== FILE OPERATION DETAILS ====================
        builder.fileOperationType(event.getFileOperationType() != null ? event.getFileOperationType().name() : null)
                .fileName(event.getFileName())
                .fileSize(event.getFileSize())
                .fileType(event.getFileType());

        // ==================== DEVICE INFORMATION ====================
        if (event.getDevice() != null) {
            builder.deviceId(event.getDevice().getDeviceId())
                    .deviceName(event.getDevice().getDeviceName())
                    .deviceType(event.getDevice().getDeviceType() != null ? event.getDevice().getDeviceType() : event.getDeviceType())
                    .browserType(event.getDevice().getBrowserType() != null ? event.getDevice().getBrowserType() : event.getBrowserType())
                    .osInfo(event.getDevice().getOsInfo())
                    .extensionVersion(event.getDevice().getExtensionVersion())
                    .deviceFingerprint(event.getDevice().getDeviceFingerprint())
                    .deviceStatus(event.getDevice().getStatus() != null ? event.getDevice().getStatus().name() : null)
                    .userAgent(event.getDevice().getUserAgent());

            // Device timestamps
            if (event.getDevice().getFirstSeenAt() != null) {
                builder.deviceFirstSeenAt(event.getDevice().getFirstSeenAt().format(formatter));
            }
            if (event.getDevice().getLastSeenAt() != null) {
                builder.deviceLastSeenAt(event.getDevice().getLastSeenAt().format(formatter));
            }
        } else {
            // Fallback to event-level device info if device is not linked
            builder.deviceType(event.getDeviceType())
                    .browserType(event.getBrowserType());
        }

        // ==================== NETWORK INFORMATION ====================
        builder.ipAddress(event.getIpAddress())
                .location(event.getLocation());

        // ==================== USER INFORMATION ====================
        builder.userName(event.getUserName())
                .userEmail(null);  // Would need to fetch from IAM service if required

        // Tenant info
        if (event.getTenant() != null) {
            builder.tenantId(event.getTenant().getTenantID());
        }

        // ==================== DEVICE USER INFORMATION ====================
        if (event.getDeviceUser() != null) {
            builder.deviceUserId(event.getDeviceUser().getPkDeviceUserId())
                    .deviceUserEmail(event.getDeviceUser().getEmail())
                    .deviceUserStatus(event.getDeviceUser().getStatus())
                    .linkedToPortalUser(event.getDeviceUser().getPortalUserId() != null);
        }

        // ==================== AUDIT INFORMATION ====================
        if (event.getCreatedAt() != null) {
            builder.createdAt(instantFormatter.format(event.getCreatedAt()));
        }
        if (event.getUpdatedAt() != null) {
            builder.updatedAt(instantFormatter.format(event.getUpdatedAt()));
        }
        builder.createdBy(event.getCreatedBy())
                .updatedBy(event.getUpdatedBy());

        // ==================== RAW DATA ====================
        builder.details(event.getDetails());

        return builder.build();
    }

    /**
     * Convert Event entity to SecurityEventDTO with incident information.
     */
    private SecurityEventDTO convertToSecurityEventDTO(Event event, Map<String, Incident> incidentMap) {
        SecurityEventDTO.SecurityEventDTOBuilder builder = SecurityEventDTO.builder()
                .eventId(event.getPkEventId())
                .url(event.getUrl())
                .timeStamp(event.getTimeStamp() != null ? event.getTimeStamp().toString() : null)
                .userName(event.getUserName())
                .deviceId(event.getDevice() != null ? event.getDevice().getDeviceId() : null)
                .eventType(event.getEventType() != null ? event.getEventType().name() : null)
                .severity(event.getSeverity())
                .threatType(event.getThreatType())
                .threatLevel(event.getThreatLevel())
                .actionTaken(event.getActionTaken())
                .riskLevel(event.getRiskLevel())
                .policyName(event.getPolicyName())
                .policyType(event.getPolicyType())
                .filterType(event.getFilterType())
                .patternType(event.getPatternType())
                .matchedPattern(event.getMatchedPattern())
                .domain(event.getDomain())
                .title(event.getTitle())
                .ipAddress(event.getIpAddress())
                .location(event.getLocation())
                .browserType(event.getBrowserType())
                .deviceType(event.getDeviceType())
                .complianceImpact(event.getComplianceImpact())
                .processingStatus(event.getProcessingStatus())
                .mitreMapping(event.getMitreMapping());

        // Add device info if device is present
        if (event.getDevice() != null) {
            builder.deviceName(event.getDevice().getDeviceName())
                    .osInfo(event.getDevice().getOsInfo())
                    .deviceStatus(event.getDevice().getStatus() != null ?
                            event.getDevice().getStatus().name() : null);
        }

        // Add device user info if present
        if (event.getDeviceUser() != null) {
            builder.deviceUserId(event.getDeviceUser().getPkDeviceUserId())
                    .deviceUserEmail(event.getDeviceUser().getEmail())
                    .deviceUserStatus(event.getDeviceUser().getStatus())
                    .linkedToPortalUser(event.getDeviceUser().getPortalUserId() != null);
        }

        // Add incident info
        Incident incident = incidentMap.get(event.getPkEventId());
        if (incident != null) {
            builder.incidentId(incident.getPkIncidentId())
                    .incidentNumber(incident.getIncidentNumber())
                    .incidentStatus(incident.getStatus() != null ? incident.getStatus().name() : null)
                    .incidentPriority(incident.getPriority() != null ? incident.getPriority().name() : null)
                    .hasIncident(true);
        } else {
            builder.hasIncident(false);
        }

        return builder.build();
    }

    /**
     * Build a map of eventId -> Incident for batch incident lookups.
     * Uses a single batch query to efficiently fetch incident links for a list of events.
     */
    private Map<String, Incident> buildIncidentMap(List<Event> events, String tenantId) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<String> eventIds = events.stream()
                .map(Event::getPkEventId)
                .collect(Collectors.toList());

        List<IncidentEvent> incidentEvents = incidentEventRepository.findByEventIdsAndTenantId(eventIds, tenantId);

        Map<String, Incident> incidentMap = new HashMap<>();
        for (IncidentEvent ie : incidentEvents) {
            // Use the first (most recent) incident link per event
            incidentMap.putIfAbsent(ie.getEvent().getPkEventId(), ie.getIncident());
        }

        return incidentMap;
    }
}
