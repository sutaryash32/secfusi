package com.secufusion.events.controller;

import com.secufusion.events.dto.BrowserAnalyticsDTO;
import com.secufusion.events.dto.ResponseDto;
import com.secufusion.events.service.BrowserAnalyticsService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller for Browser Usage Analytics.
 * Provides endpoints for file operations stats, daily activity trends,
 * and domain access analytics.
 */
@RestController
@RequestMapping("/api/events/analytics/browser")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Browser Analytics", description = "Browser usage analytics endpoints")
public class BrowserAnalyticsController {

    private final BrowserAnalyticsService browserAnalyticsService;
    private final JwtUtl jwtUtl;

    /**
     * Get comprehensive browser usage analytics with predefined period.
     *
     * @param request HTTP request for tenant extraction
     * @param period  Analytics period: "7_DAYS", "30_DAYS", "90_DAYS" (default: "30_DAYS")
     * @return BrowserAnalyticsDTO with file operations, trends, and domain stats
     */
    @GetMapping
    @Operation(
            summary = "Get browser usage analytics",
            description = "Returns comprehensive browser usage analytics including file downloads/uploads, " +
                    "blocked operations (DLP), daily activity trends, and top accessed domains. " +
                    "Use period parameter for predefined ranges or startDate/endDate for custom range."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid period or date parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<BrowserAnalyticsDTO>> getBrowserAnalytics(
            HttpServletRequest request,
            @Parameter(description = "Analytics period: 7_DAYS, 30_DAYS, 90_DAYS (ignored if startDate/endDate provided)")
            @RequestParam(defaultValue = "30_DAYS") String period,
            @Parameter(description = "Start date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            BrowserAnalyticsDTO analytics;

            // Use custom date range if both dates are provided
            if (startDate != null && endDate != null) {
                log.info("getBrowserAnalytics - entry. tenantId={} startDate={} endDate={}",
                        tenantId, startDate, endDate);

                // Validate date range
                if (startDate.isAfter(endDate)) {
                    log.warn("getBrowserAnalytics - invalid date range. startDate={} endDate={}", startDate, endDate);
                    return ResponseEntity.badRequest()
                            .body(new ResponseDto<>(null, "startDate cannot be after endDate"));
                }

                // Limit date range to 1 year max
                if (startDate.plusYears(1).isBefore(endDate)) {
                    log.warn("getBrowserAnalytics - date range exceeds 1 year. startDate={} endDate={}", startDate, endDate);
                    return ResponseEntity.badRequest()
                            .body(new ResponseDto<>(null, "Date range cannot exceed 1 year"));
                }

                analytics = browserAnalyticsService.getBrowserAnalytics(tenantId, startDate, endDate);
                log.info("getBrowserAnalytics - success. tenantId={} startDate={} endDate={}",
                        tenantId, startDate, endDate);
            } else {
                log.info("getBrowserAnalytics - entry. tenantId={} period={}", tenantId, period);

                // Validate period
                if (!isValidPeriod(period)) {
                    log.warn("getBrowserAnalytics - invalid period. tenantId={} period={}", tenantId, period);
                    return ResponseEntity.badRequest()
                            .body(new ResponseDto<>(null, "Invalid period. Use: 7_DAYS, 30_DAYS, or 90_DAYS"));
                }

                analytics = browserAnalyticsService.getBrowserAnalytics(tenantId, period);
                log.info("getBrowserAnalytics - success. tenantId={} period={}", tenantId, period);
            }

            return ResponseEntity.ok(new ResponseDto<>(analytics, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getBrowserAnalytics - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get file operations statistics only.
     */
    @GetMapping("/file-operations")
    @Operation(
            summary = "Get file operations statistics",
            description = "Returns file download/upload counts and blocked operations stats"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<FileOperationsStats>> getFileOperationsStats(
            HttpServletRequest request,
            @Parameter(description = "Analytics period: 7_DAYS, 30_DAYS, 90_DAYS")
            @RequestParam(defaultValue = "30_DAYS") String period,
            @Parameter(description = "Start date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getFileOperationsStats - entry. tenantId={} period={} startDate={} endDate={}",
                tenantId, period, startDate, endDate);

        try {
            BrowserAnalyticsDTO analytics = getAnalytics(tenantId, period, startDate, endDate);

            FileOperationsStats stats = new FileOperationsStats(
                    analytics.getTotalDownloads(),
                    analytics.getTotalUploads(),
                    analytics.getBlockedDownloads(),
                    analytics.getBlockedUploads()
            );

            log.info("getFileOperationsStats - success. tenantId={}", tenantId);
            return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getFileOperationsStats - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get daily activity trends only.
     */
    @GetMapping("/trends")
    @Operation(
            summary = "Get daily activity trends",
            description = "Returns daily event counts and active devices for charting"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trends retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<java.util.List<BrowserAnalyticsDTO.DailyTrendDTO>>> getDailyTrends(
            HttpServletRequest request,
            @Parameter(description = "Analytics period: 7_DAYS, 30_DAYS, 90_DAYS")
            @RequestParam(defaultValue = "30_DAYS") String period,
            @Parameter(description = "Start date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getDailyTrends - entry. tenantId={} period={} startDate={} endDate={}",
                tenantId, period, startDate, endDate);

        try {
            BrowserAnalyticsDTO analytics = getAnalytics(tenantId, period, startDate, endDate);

            log.info("getDailyTrends - success. tenantId={} dataPoints={}",
                    tenantId, analytics.getDailyActivityTrends() != null ?
                            analytics.getDailyActivityTrends().size() : 0);
            return ResponseEntity.ok(new ResponseDto<>(
                    analytics.getDailyActivityTrends(),
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getDailyTrends - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get top accessed domains only.
     */
    @GetMapping("/domains")
    @Operation(
            summary = "Get top accessed domains",
            description = "Returns ranked list of most visited domains with visit counts and percentages"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Domains retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<java.util.List<BrowserAnalyticsDTO.DomainAccessDTO>>> getTopDomains(
            HttpServletRequest request,
            @Parameter(description = "Analytics period: 7_DAYS, 30_DAYS, 90_DAYS")
            @RequestParam(defaultValue = "30_DAYS") String period,
            @Parameter(description = "Start date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for custom range (ISO format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getTopDomains - entry. tenantId={} period={} startDate={} endDate={}",
                tenantId, period, startDate, endDate);

        try {
            BrowserAnalyticsDTO analytics = getAnalytics(tenantId, period, startDate, endDate);

            log.info("getTopDomains - success. tenantId={} domainCount={}",
                    tenantId, analytics.getTopAccessedDomains() != null ?
                            analytics.getTopAccessedDomains().size() : 0);
            return ResponseEntity.ok(new ResponseDto<>(
                    analytics.getTopAccessedDomains(),
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getTopDomains - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Helper method to get analytics based on period or date range.
     */
    private BrowserAnalyticsDTO getAnalytics(String tenantId, String period,
                                              LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return browserAnalyticsService.getBrowserAnalytics(tenantId, startDate, endDate);
        }
        return browserAnalyticsService.getBrowserAnalytics(tenantId, period);
    }

    /**
     * Validate period parameter.
     */
    private boolean isValidPeriod(String period) {
        return period != null && (
                period.equals("7_DAYS") ||
                        period.equals("30_DAYS") ||
                        period.equals("90_DAYS")
        );
    }

    /**
     * Simple record for file operations statistics.
     */
    public record FileOperationsStats(
            long totalDownloads,
            long totalUploads,
            long blockedDownloads,
            long blockedUploads
    ) {}
}
