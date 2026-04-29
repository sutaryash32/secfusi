package com.secufusion.events.service;

import com.secufusion.events.dto.BrowserAnalyticsDTO;
import com.secufusion.events.entity.FileOperationType;
import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrowserAnalyticsService Tests")
class BrowserAnalyticsServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private BrowserAnalyticsService browserAnalyticsService;

    // ── Common test data ──────────────────────────────────────────────────────
    private static final String TENANT_ID   = "tenant-001";
    private static final String PERIOD_7    = "7_DAYS";
    private static final String PERIOD_30   = "30_DAYS";
    private static final String PERIOD_90   = "90_DAYS";
    private static final String PERIOD_CUSTOM = "CUSTOM";

    private List<Object[]> eventTrendRows;
    private List<Object[]> fileOpTrendRows;
    private List<Object[]> domainRows;

    @BeforeEach
    void setUp() {
        // One event-trend row: date=today, events=50, activeDevices=10
        eventTrendRows = new ArrayList<>();
        eventTrendRows.add(new Object[]{
                Date.valueOf(LocalDate.now()), 50L, 10L
        });

        // One file-op trend row: date=today, downloads=5, uploads=3, violations=1
        fileOpTrendRows = new ArrayList<>();
        fileOpTrendRows.add(new Object[]{
                Date.valueOf(LocalDate.now()), 5L, 3L, 1L
        });

        // One domain row: domain, category, visits
        domainRows = new ArrayList<>();
        domainRows.add(new Object[]{"slack.com", "COMMUNICATION", 200L});
    }

    // =========================================================================
    // getBrowserAnalytics(tenantId, period)
    // =========================================================================

    @Nested
    @DisplayName("getBrowserAnalytics(tenantId, period)")
    class GetBrowserAnalyticsByPeriod {

        // ── Happy paths ───────────────────────────────────────────────────────

        @Test
        @DisplayName("Happy Path — 7_DAYS returns populated DTO")
        void happyPath_7Days() {
            // ARRANGE
            when(eventRepository.countFileOperations(
                    eq(TENANT_ID), eq(FileOperationType.DOWNLOAD), any(), any()))
                    .thenReturn(100L);
            when(eventRepository.countFileOperations(
                    eq(TENANT_ID), eq(FileOperationType.UPLOAD), any(), any()))
                    .thenReturn(50L);
            when(eventRepository.countBlockedFileOperations(
                    eq(TENANT_ID), eq(FileOperationType.DOWNLOAD), any(), any()))
                    .thenReturn(10L);
            when(eventRepository.countBlockedFileOperations(
                    eq(TENANT_ID), eq(FileOperationType.UPLOAD), any(), any()))
                    .thenReturn(5L);
            when(eventRepository.getDailyEventTrends(eq(TENANT_ID), any(), any()))
                    .thenReturn(eventTrendRows);
            when(eventRepository.getDailyFileOperationTrends(eq(TENANT_ID), any(), any()))
                    .thenReturn(fileOpTrendRows);
            when(eventRepository.countTotalDomainVisits(eq(TENANT_ID), any(), any()))
                    .thenReturn(1000L);
            when(eventRepository.getTopDomainsWithCategory(
                    eq(TENANT_ID), any(), any(), any(PageRequest.class)))
                    .thenReturn(domainRows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getTenantId());
            assertEquals(PERIOD_7,  result.getPeriod());
            assertEquals(100L, result.getTotalDownloads());
            assertEquals(50L,  result.getTotalUploads());
            assertEquals(10L,  result.getBlockedDownloads());
            assertEquals(5L,   result.getBlockedUploads());
            assertEquals(1000L, result.getTotalDomainVisits());
            assertNotNull(result.getDailyActivityTrends());
            assertFalse(result.getDailyActivityTrends().isEmpty());
            assertNotNull(result.getTopAccessedDomains());
            assertNotNull(result.getCommunicationPlatforms());
            assertNotNull(result.getGeneratedAt());

            verify(eventRepository).countFileOperations(
                    eq(TENANT_ID), eq(FileOperationType.DOWNLOAD), any(), any());
            verify(eventRepository).countFileOperations(
                    eq(TENANT_ID), eq(FileOperationType.UPLOAD), any(), any());
            verify(eventRepository).countTotalDomainVisits(eq(TENANT_ID), any(), any());
        }

        @Test
        @DisplayName("Happy Path — 30_DAYS returns populated DTO")
        void happyPath_30Days() {
            // ARRANGE
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(200L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(20L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(eventTrendRows);
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(fileOpTrendRows);
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(500L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(domainRows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_30);

            // ASSERT
            assertNotNull(result);
            assertEquals(PERIOD_30, result.getPeriod());
            assertEquals(200L, result.getTotalDownloads());

            verify(eventRepository, times(2))
                    .countFileOperations(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Happy Path — 90_DAYS returns populated DTO")
        void happyPath_90Days() {
            // ARRANGE
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(300L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(30L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(eventTrendRows);
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(fileOpTrendRows);
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(900L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(domainRows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_90);

            // ASSERT
            assertNotNull(result);
            assertEquals(PERIOD_90, result.getPeriod());
            assertEquals(300L, result.getTotalDownloads());
        }

        @Test
        @DisplayName("Happy Path — unknown period defaults to 30-day window")
        void happyPath_unknownPeriodDefaultsTo30Days() {
            // ARRANGE
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(null);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(new ArrayList<>());

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, "UNKNOWN_PERIOD");

            // ASSERT
            assertNotNull(result);
            assertEquals("UNKNOWN_PERIOD", result.getPeriod());
            assertEquals(0L, result.getTotalDomainVisits()); // null → 0L fallback
        }

        @Test
        @DisplayName("Happy Path — null totalDomainVisits is coerced to 0")
        void happyPath_nullTotalDomainVisitsCoercedToZero() {
            // ARRANGE
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(null);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(new ArrayList<>());

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertEquals(0L, result.getTotalDomainVisits());
        }

        // ── Sad path ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("Sad Path — repository throws → returns empty fallback DTO")
        void sadPath_repositoryThrows_returnsFallbackDTO() {
            // ARRANGE
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("DB connection lost"));

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            // BrowserAnalyticsDTO.empty() must still carry tenantId and period
            assertEquals(TENANT_ID, result.getTenantId());
        }
    }

    // =========================================================================
    // getBrowserAnalytics(tenantId, startDate, endDate) — custom range
    // =========================================================================

    @Nested
    @DisplayName("getBrowserAnalytics(tenantId, startDate, endDate)")
    class GetBrowserAnalyticsByDateRange {

        private LocalDate startDate;
        private LocalDate endDate;

        @BeforeEach
        void rangeSetup() {
            startDate = LocalDate.now().minusDays(14);
            endDate   = LocalDate.now();
        }

        // ── Happy path ────────────────────────────────────────────────────────

        @Test
        @DisplayName("Happy Path — custom range returns DTO with CUSTOM period")
        void happyPath_customRange() {
            // ARRANGE
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(75L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(7L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(eventTrendRows);
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(fileOpTrendRows);
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(400L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(domainRows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, startDate, endDate);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID,     result.getTenantId());
            assertEquals(PERIOD_CUSTOM, result.getPeriod());
            assertEquals(75L,  result.getTotalDownloads());
            assertEquals(7L,   result.getBlockedDownloads());
            assertEquals(400L, result.getTotalDomainVisits());
            assertNotNull(result.getStartDate());
            assertNotNull(result.getEndDate());

            verify(eventRepository, times(2))
                    .countFileOperations(any(), any(), any(), any());
            verify(eventRepository, times(2))
                    .countBlockedFileOperations(any(), any(), any(), any());
        }

        // ── Sad path ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("Sad Path — repository throws on custom range → fallback DTO")
        void sadPath_customRange_repositoryThrows() {
            // ARRANGE
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Timeout"));

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, startDate, endDate);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getTenantId());
        }
    }

    // =========================================================================
    // getDailyActivityTrends (via public method) — covers merge / fill logic
    // =========================================================================

    @Nested
    @DisplayName("getDailyActivityTrends — merge and gap-fill logic")
    class DailyActivityTrends {

        @Test
        @DisplayName("Happy Path — file-op date not in event map creates new entry")
        void happyPath_fileOpDateNotInEventMap_createsNewEntry() {
            // ARRANGE — event-trend row uses YESTERDAY, file-op row uses TODAY
            List<Object[]> yesterdayEvents = new ArrayList<>();
            yesterdayEvents.add(new Object[]{
                    Date.valueOf(LocalDate.now().minusDays(1)), 10L, 5L
            });

            List<Object[]> todayFileOps = new ArrayList<>();
            todayFileOps.add(new Object[]{
                    Date.valueOf(LocalDate.now()), 3L, 2L, 0L
            });

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(yesterdayEvents);
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(todayFileOps);
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(new ArrayList<>());

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            List<BrowserAnalyticsDTO.DailyTrendDTO> trends = result.getDailyActivityTrends();
            assertNotNull(trends);
            // 7-day window must have at least 7 entries (gap-filled)
            assertTrue(trends.size() >= 7);
        }

        @Test
        @DisplayName("Happy Path — file-op date IS in event map merges downloads/uploads/violations")
        void happyPath_fileOpMergedIntoExistingEntry() {
            // ARRANGE — both rows share the same date
            List<Object[]> sameDay = new ArrayList<>();
            sameDay.add(new Object[]{Date.valueOf(LocalDate.now()), 20L, 8L});

            List<Object[]> fileOps = new ArrayList<>();
            fileOps.add(new Object[]{Date.valueOf(LocalDate.now()), 6L, 4L, 2L});

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(sameDay);
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(fileOps);
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(new ArrayList<>());

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            List<BrowserAnalyticsDTO.DailyTrendDTO> trends = result.getDailyActivityTrends();
            assertNotNull(trends);
            // Merged entry for today must carry the file-op data
            BrowserAnalyticsDTO.DailyTrendDTO today = trends.stream()
                    .filter(t -> t.getDate().equals(LocalDate.now().toString()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(today);
            assertEquals(20L, today.getEvents());
            assertEquals(6L,  today.getDownloads());
            assertEquals(4L,  today.getUploads());
            assertEquals(2L,  today.getViolations());
        }

        @Test
        @DisplayName("Happy Path — null fields in file-op row default to 0")
        void happyPath_nullFieldsInFileOpRowDefaultToZero() {
            // ARRANGE — file-op row has nulls in downloads/uploads/violations columns
            List<Object[]> fileOpsWithNulls = new ArrayList<>();
            fileOpsWithNulls.add(new Object[]{
                    Date.valueOf(LocalDate.now()), null, null, null
            });

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(fileOpsWithNulls);
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(new ArrayList<>());

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            BrowserAnalyticsDTO.DailyTrendDTO today = result.getDailyActivityTrends()
                    .stream()
                    .filter(t -> t.getDate().equals(LocalDate.now().toString()))
                    .findFirst().orElse(null);
            assertNotNull(today);
            assertEquals(0L, today.getDownloads());
            assertEquals(0L, today.getUploads());
            assertEquals(0L, today.getViolations());
        }

        @Test
        @DisplayName("Happy Path — missing dates are gap-filled with zero entries")
        void happyPath_missingDatesFilledWithZeros() {
            // ARRANGE — empty trend lists so all dates must be gap-filled
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(new ArrayList<>());

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            List<BrowserAnalyticsDTO.DailyTrendDTO> trends = result.getDailyActivityTrends();
            // All 8 entries (day 0..7) must be zero-filled
            assertTrue(trends.size() >= 7);
            trends.forEach(t -> {
                assertEquals(0L, t.getEvents());
                assertEquals(0L, t.getDownloads());
                assertEquals(0L, t.getUploads());
                assertEquals(0L, t.getViolations());
            });
        }
    }

    // =========================================================================
    // getTopAccessedDomains (via public method) — rank / percentage logic
    // =========================================================================

    @Nested
    @DisplayName("getTopAccessedDomains — rank and percentage calculation")
    class TopAccessedDomains {

        @Test
        @DisplayName("Happy Path — domains ranked and percentage calculated correctly")
        void happyPath_domainsRankedWithPercentage() {
            // ARRANGE
            List<Object[]> multiDomains = new ArrayList<>();
            multiDomains.add(new Object[]{"google.com",  "SEARCH",        500L});
            multiDomains.add(new Object[]{"github.com",  "DEVELOPMENT",   300L});
            multiDomains.add(new Object[]{"slack.com",   "COMMUNICATION", 200L});

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(1000L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(multiDomains);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            List<BrowserAnalyticsDTO.DomainAccessDTO> domains = result.getTopAccessedDomains();
            assertEquals(3, domains.size());

            BrowserAnalyticsDTO.DomainAccessDTO first = domains.get(0);
            assertEquals(1,           first.getRank());
            assertEquals("google.com", first.getDomain());
            assertEquals(500L,         first.getVisits());
            assertEquals(50.0,         first.getPercentage()); // 500/1000 * 100
            assertEquals("SEARCH",     first.getCategory());

            assertEquals(2, domains.get(1).getRank());
            assertEquals(3, domains.get(2).getRank());
        }

        @Test
        @DisplayName("Happy Path — null domain/category values default to 'unknown'/null")
        void happyPath_nullDomainAndCategoryDefaultValues() {
            // ARRANGE
            List<Object[]> rowsWithNulls = new ArrayList<>();
            rowsWithNulls.add(new Object[]{null, null, 100L});

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(100L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(rowsWithNulls);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            BrowserAnalyticsDTO.DomainAccessDTO domain = result.getTopAccessedDomains().get(0);
            assertEquals("unknown", domain.getDomain());
            assertNull(domain.getCategory());
            assertEquals(100.0, domain.getPercentage());
        }

        @Test
        @DisplayName("Happy Path — zero totalVisits gives 0.0 percentage (no divide-by-zero)")
        void happyPath_zeroTotalVisits_percentageIsZero() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{"example.com", "OTHER", 50L});

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(null); // → coerced to 0
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertEquals(0.0,
                    result.getTopAccessedDomains().get(0).getPercentage());
        }
    }

    // =========================================================================
    // getCommunicationPlatforms (via public method) — platform domain matching
    // =========================================================================

    @Nested
    @DisplayName("getCommunicationPlatforms — platform domain matching")
    class CommunicationPlatforms {

        @Test
        @DisplayName("Happy Path — known platform domains are detected and mapped")
        void happyPath_knownPlatformDomainsDetected() {
            // ARRANGE
            List<Object[]> platformRows = new ArrayList<>();
            platformRows.add(new Object[]{"slack.com",             "COMM", 300L});
            platformRows.add(new Object[]{"teams.microsoft.com",   "COMM", 200L});
            platformRows.add(new Object[]{"zoom.us",               "COMM", 150L});
            platformRows.add(new Object[]{"meet.google.com",       "COMM", 100L});
            platformRows.add(new Object[]{"discord.com",           "COMM",  80L});
            platformRows.add(new Object[]{"webex.com",             "COMM",  60L});

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(890L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(platformRows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            List<BrowserAnalyticsDTO.PlatformUsageDTO> platforms =
                    result.getCommunicationPlatforms();
            assertNotNull(platforms);
            assertFalse(platforms.isEmpty());
            // At least one known platform must be detected
            boolean hasSlack = platforms.stream()
                    .anyMatch(p -> "Slack".equals(p.getPlatformName()));
            assertTrue(hasSlack);
        }

        @Test
        @DisplayName("Happy Path — unrecognised domain produces no platform entry")
        void happyPath_unknownDomain_noPlatformEntry() {
            // ARRANGE
            List<Object[]> unknownDomain = new ArrayList<>();
            unknownDomain.add(new Object[]{"unknown-site.com", "OTHER", 50L});

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(50L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(unknownDomain);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getCommunicationPlatforms().isEmpty());
        }

        @Test
        @DisplayName("Happy Path — null domain string in row is handled safely")
        void happyPath_nullDomainInRow_handledSafely() {
            // ARRANGE
            List<Object[]> rowWithNullDomain = new ArrayList<>();
            rowWithNullDomain.add(new Object[]{null, "OTHER", 50L});

            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyEventTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(rowWithNullDomain);

            // ACT + ASSERT — must not throw NPE
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);
            assertNotNull(result);
        }
    }

    // =========================================================================
    // formatDate — covers every type branch for full coverage
    // =========================================================================

    @Nested
    @DisplayName("formatDate — all type branches via trend rows")
    class FormatDate {

        private void setupFileOpAndCountMocks() {
            when(eventRepository.countFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.countBlockedFileOperations(any(), any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getDailyFileOperationTrends(any(), any(), any()))
                    .thenReturn(new ArrayList<>());
            when(eventRepository.countTotalDomainVisits(any(), any(), any()))
                    .thenReturn(0L);
            when(eventRepository.getTopDomainsWithCategory(any(), any(), any(), any()))
                    .thenReturn(new ArrayList<>());
        }

        @Test
        @DisplayName("Branch — java.sql.Date in event trend row")
        void branch_sqlDate() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{Date.valueOf(LocalDate.now()), 1L, 1L});
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getDailyActivityTrends().stream()
                    .anyMatch(t -> t.getEvents() == 1L));
        }

        @Test
        @DisplayName("Branch — LocalDate in event trend row")
        void branch_localDate() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{LocalDate.now(), 2L, 1L});
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getDailyActivityTrends().stream()
                    .anyMatch(t -> t.getEvents() == 2L));
        }

        @Test
        @DisplayName("Branch — java.sql.Timestamp in event trend row")
        void branch_sqlTimestamp() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{
                    Timestamp.valueOf(LocalDateTime.now()), 3L, 1L
            });
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getDailyActivityTrends().stream()
                    .anyMatch(t -> t.getEvents() == 3L));
        }

        @Test
        @DisplayName("Branch — java.time.Instant in event trend row")
        void branch_instant() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{Instant.now(), 4L, 1L});
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getDailyActivityTrends().stream()
                    .anyMatch(t -> t.getEvents() == 4L));
        }

        @Test
        @DisplayName("Branch — java.time.OffsetDateTime in event trend row")
        void branch_offsetDateTime() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{OffsetDateTime.now(), 5L, 1L});
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getDailyActivityTrends().stream()
                    .anyMatch(t -> t.getEvents() == 5L));
        }

        @Test
        @DisplayName("Branch — java.time.LocalDateTime in event trend row")
        void branch_localDateTime() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{LocalDateTime.now(), 6L, 1L});
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.getDailyActivityTrends().stream()
                    .anyMatch(t -> t.getEvents() == 6L));
        }

        @Test
        @DisplayName("Branch — toString fallback for unrecognised date type")
        void branch_unknownType_toStringFallback() {
            // ARRANGE — a plain String that doesn't match any instanceof branch
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{"not-a-real-date-type", 7L, 1L});
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT — the service calls dateObj.toString() and puts it in the map;
            // the gap-fill loop won't find "not-a-real-date-type" in the date range
            // so the result list will be gap-filled zeros, but must NOT throw
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT
            assertNotNull(result);
        }

        @Test
        @DisplayName("Branch — null dateObj returns null and row is skipped")
        void branch_nullDateObj_rowSkipped() {
            // ARRANGE
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{null, 8L, 1L});
            setupFileOpAndCountMocks();
            when(eventRepository.getDailyEventTrends(any(), any(), any())).thenReturn(rows);

            // ACT
            BrowserAnalyticsDTO result = browserAnalyticsService
                    .getBrowserAnalytics(TENANT_ID, PERIOD_7);

            // ASSERT — row silently skipped, no events=8 in output
            assertNotNull(result);
            result.getDailyActivityTrends().forEach(t ->
                    assertNotEquals(8L, t.getEvents()));
        }
    }
}