package com.secufusion.events.service;

import com.secufusion.events.dto.BrowserAnalyticsDTO;
import com.secufusion.events.entity.FileOperationType;
import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Service for Browser Usage Analytics.
 * Provides file operations stats, daily trends, and domain analytics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, noRollbackFor = Exception.class)
public class BrowserAnalyticsService {

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;

    /**
     * Get browser usage analytics for a tenant with predefined period.
     *
     * @param tenantId Tenant ID
     * @param period   Analytics period: "7_DAYS", "30_DAYS", "90_DAYS"
     * @return BrowserAnalyticsDTO with all analytics data
     */
    public BrowserAnalyticsDTO getBrowserAnalytics(String tenantId, String period) {
        log.info("Getting browser analytics for tenant={} period={}", tenantId, period);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = calculateStartDate(period, now);

        return getBrowserAnalyticsForDateRange(tenantId, period, startDate, now);
    }

    /**
     * Get browser usage analytics for a tenant with custom date range.
     *
     * @param tenantId  Tenant ID
     * @param startDate Start date (inclusive)
     * @param endDate   End date (inclusive)
     * @return BrowserAnalyticsDTO with all analytics data
     */
    public BrowserAnalyticsDTO getBrowserAnalytics(String tenantId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting browser analytics for tenant={} from={} to={}", tenantId, startDate, endDate);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        String period = "CUSTOM";

        return getBrowserAnalyticsForDateRange(tenantId, period, start, end);
    }

    /**
     * Internal method to get browser analytics for a date range.
     */
    private BrowserAnalyticsDTO getBrowserAnalyticsForDateRange(
            String tenantId, String period, LocalDateTime startDate, LocalDateTime endDate) {

        try {
            // File Operations Stats
            long totalDownloads = eventRepository.countFileOperations(
                    tenantId, FileOperationType.DOWNLOAD, startDate, endDate);
            long totalUploads = eventRepository.countFileOperations(
                    tenantId, FileOperationType.UPLOAD, startDate, endDate);
            long blockedDownloads = eventRepository.countBlockedFileOperations(
                    tenantId, FileOperationType.DOWNLOAD, startDate, endDate);
            long blockedUploads = eventRepository.countBlockedFileOperations(
                    tenantId, FileOperationType.UPLOAD, startDate, endDate);

            // Daily Activity Trends
            List<BrowserAnalyticsDTO.DailyTrendDTO> dailyTrends = getDailyActivityTrends(
                    tenantId, startDate, endDate);

            // Top Accessed Domains
            Long totalVisits = eventRepository.countTotalDomainVisits(tenantId, startDate, endDate);
            long totalDomainVisits = totalVisits != null ? totalVisits : 0L;
            List<BrowserAnalyticsDTO.DomainAccessDTO> topDomains = getTopAccessedDomains(
                    tenantId, startDate, endDate, totalDomainVisits, 20);

            // Communication Platforms (placeholder - can be extended)
            List<BrowserAnalyticsDTO.PlatformUsageDTO> platforms = getCommunicationPlatforms(
                    tenantId, startDate, endDate);

            return BrowserAnalyticsDTO.builder()
                    .tenantId(tenantId)
                    .period(period)
                    .startDate(startDate)
                    .endDate(endDate)
                    .totalDownloads(totalDownloads)
                    .totalUploads(totalUploads)
                    .blockedDownloads(blockedDownloads)
                    .blockedUploads(blockedUploads)
                    .dailyActivityTrends(dailyTrends)
                    .topAccessedDomains(topDomains)
                    .totalDomainVisits(totalDomainVisits)
                    .communicationPlatforms(platforms)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get browser analytics for tenant={}: {}", tenantId, e.getMessage(), e);
            return BrowserAnalyticsDTO.empty(tenantId, period);
        }
    }

    /**
     * Calculate start date based on period.
     */
    private LocalDateTime calculateStartDate(String period, LocalDateTime now) {
        return switch (period) {
            case "7_DAYS" -> now.minusDays(7);
            case "30_DAYS" -> now.minusDays(30);
            case "90_DAYS" -> now.minusDays(90);
            default -> now.minusDays(30); // Default to 30 days
        };
    }

    /**
     * Get daily activity trends combining events, devices, and file operations.
     */
    private List<BrowserAnalyticsDTO.DailyTrendDTO> getDailyActivityTrends(
            String tenantId, LocalDateTime start, LocalDateTime end) {

        // Get event trends
        List<Object[]> eventTrends = eventRepository.getDailyEventTrends(tenantId, start, end);
        Map<String, BrowserAnalyticsDTO.DailyTrendDTO> trendMap = new LinkedHashMap<>();

        for (Object[] row : eventTrends) {
            String date = formatDate(row[0]);
            if (date != null) {
                trendMap.put(date, BrowserAnalyticsDTO.DailyTrendDTO.builder()
                        .date(date)
                        .events(((Number) row[1]).longValue())
                        .activeDevices(((Number) row[2]).longValue())
                        .downloads(0L)
                        .uploads(0L)
                        .violations(0L)
                        .build());
            }
        }

        // Get file operation trends and merge
        List<Object[]> fileOpTrends = eventRepository.getDailyFileOperationTrends(tenantId, start, end);
        for (Object[] row : fileOpTrends) {
            String date = formatDate(row[0]);
            if (date != null) {
                BrowserAnalyticsDTO.DailyTrendDTO existing = trendMap.get(date);
                if (existing != null) {
                    existing.setDownloads(row[1] != null ? ((Number) row[1]).longValue() : 0L);
                    existing.setUploads(row[2] != null ? ((Number) row[2]).longValue() : 0L);
                    existing.setViolations(row[3] != null ? ((Number) row[3]).longValue() : 0L);
                } else {
                    trendMap.put(date, BrowserAnalyticsDTO.DailyTrendDTO.builder()
                            .date(date)
                            .events(0L)
                            .activeDevices(0L)
                            .downloads(row[1] != null ? ((Number) row[1]).longValue() : 0L)
                            .uploads(row[2] != null ? ((Number) row[2]).longValue() : 0L)
                            .violations(row[3] != null ? ((Number) row[3]).longValue() : 0L)
                            .build());
                }
            }
        }

        // Fill in missing dates with zeros
        List<BrowserAnalyticsDTO.DailyTrendDTO> result = new ArrayList<>();
        LocalDate currentDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        while (!currentDate.isAfter(endDate)) {
            String dateStr = currentDate.toString();
            BrowserAnalyticsDTO.DailyTrendDTO trend = trendMap.get(dateStr);
            if (trend != null) {
                result.add(trend);
            } else {
                result.add(BrowserAnalyticsDTO.DailyTrendDTO.builder()
                        .date(dateStr)
                        .events(0L)
                        .activeDevices(0L)
                        .downloads(0L)
                        .uploads(0L)
                        .violations(0L)
                        .build());
            }
            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    /**
     * Get top accessed domains with ranking and percentage.
     */
    private List<BrowserAnalyticsDTO.DomainAccessDTO> getTopAccessedDomains(
            String tenantId, LocalDateTime start, LocalDateTime end,
            long totalVisits, int limit) {

        List<Object[]> results = eventRepository.getTopDomainsWithCategory(
                tenantId, start, end, PageRequest.of(0, limit));

        List<BrowserAnalyticsDTO.DomainAccessDTO> domains = new ArrayList<>();
        int rank = 1;

        for (Object[] row : results) {
            String domain = row[0] != null ? row[0].toString() : "unknown";
            String category = row[1] != null ? row[1].toString() : null;
            long visits = ((Number) row[2]).longValue();
            double percentage = totalVisits > 0 ? (visits * 100.0) / totalVisits : 0.0;

            domains.add(BrowserAnalyticsDTO.DomainAccessDTO.builder()
                    .rank(rank++)
                    .domain(domain)
                    .visits(visits)
                    .percentage(Math.round(percentage * 100.0) / 100.0) // Round to 2 decimals
                    .category(category)
                    .build());
        }

        return domains;
    }

    /**
     * Get communication platforms usage.
     * This is a placeholder that can be extended to detect platforms like Slack, Teams, Zoom.
     */
    private List<BrowserAnalyticsDTO.PlatformUsageDTO> getCommunicationPlatforms(
            String tenantId, LocalDateTime start, LocalDateTime end) {

        // Communication platform domains to track
        Map<String, String> platformDomains = Map.of(
                "slack.com", "Slack",
                "teams.microsoft.com", "Microsoft Teams",
                "zoom.us", "Zoom",
                "meet.google.com", "Google Meet",
                "discord.com", "Discord",
                "webex.com", "Webex"
        );

        List<BrowserAnalyticsDTO.PlatformUsageDTO> platforms = new ArrayList<>();

        // Get top domains and filter for communication platforms
        List<Object[]> results = eventRepository.getTopDomainsWithCategory(
                tenantId, start, end, PageRequest.of(0, 100));

        for (Object[] row : results) {
            String domain = row[0] != null ? row[0].toString().toLowerCase() : "";

            for (Map.Entry<String, String> entry : platformDomains.entrySet()) {
                if (domain.contains(entry.getKey())) {
                    long visits = ((Number) row[2]).longValue();
                    platforms.add(BrowserAnalyticsDTO.PlatformUsageDTO.builder()
                            .platformName(entry.getValue())
                            .totalSessions(visits)
                            .activeUsers(0L) // Would need distinct user count query
                            .totalDuration(0L) // Would need duration aggregation
                            .build());
                    break;
                }
            }
        }

        return platforms;
    }

    /**
     * Format date from SQL result to string.
     * Handles various date/time types including Instant and OffsetDateTime.
     */
    private String formatDate(Object dateObj) {
        if (dateObj == null) return null;
        if (dateObj instanceof Date) {
            return ((Date) dateObj).toLocalDate().toString();
        }
        if (dateObj instanceof LocalDate) {
            return dateObj.toString();
        }
        if (dateObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate().toString();
        }
        if (dateObj instanceof java.time.Instant) {
            return java.time.LocalDateTime.ofInstant((java.time.Instant) dateObj,
                    java.time.ZoneId.systemDefault()).toLocalDate().toString();
        }
        if (dateObj instanceof java.time.OffsetDateTime) {
            return ((java.time.OffsetDateTime) dateObj).toLocalDate().toString();
        }
        if (dateObj instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) dateObj).toLocalDate().toString();
        }
        return dateObj.toString();
    }
}
