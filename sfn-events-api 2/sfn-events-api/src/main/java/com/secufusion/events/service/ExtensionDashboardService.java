package com.secufusion.events.service;

import com.secufusion.events.dto.ExtensionDashboardDTO;
import com.secufusion.events.entity.ExtensionEvent;
import com.secufusion.events.entity.ExtensionEventType;
import com.secufusion.events.entity.InstalledExtension;
import com.secufusion.events.repository.ExtensionEventRepository;
import com.secufusion.events.repository.InstalledExtensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionDashboardService {

    private final InstalledExtensionRepository extensionRepository;
    private final ExtensionEventRepository eventRepository;

    // ==================== 1. OVERVIEW ====================

    @Transactional(readOnly = true)
    public ExtensionDashboardDTO.Overview getOverview(String tenantId) {
        log.debug("Getting extension dashboard overview for tenant={}", tenantId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last30d = now.minusDays(30);

        // Counts
        long uniqueExtensions = extensionRepository.countUniqueExtensions(tenantId);
        long totalInstallations = extensionRepository.countByTenantIdAndPolicyAction(tenantId, "ALLOW")
                + extensionRepository.countByTenantIdAndPolicyAction(tenantId, "WARN")
                + extensionRepository.countByTenantIdAndPolicyAction(tenantId, "BLOCK");

        // Risk distribution
        Map<String, Long> riskDistribution = new LinkedHashMap<>();
        for (Object[] row : extensionRepository.countByRiskLevel(tenantId)) {
            riskDistribution.put(row[0] != null ? row[0].toString() : "NONE", ((Number) row[1]).longValue());
        }

        // Policy distribution
        Map<String, Long> policyDistribution = new LinkedHashMap<>();
        for (Object[] row : extensionRepository.countByPolicyAction(tenantId)) {
            policyDistribution.put(row[0] != null ? row[0].toString() : "ALLOW", ((Number) row[1]).longValue());
        }

        // Status distribution
        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        for (Object[] row : extensionRepository.countByStatus(tenantId)) {
            statusDistribution.put(row[0] != null ? row[0].toString() : "UNKNOWN", ((Number) row[1]).longValue());
        }

        long activeInstallations = statusDistribution.getOrDefault("ACTIVE", 0L);

        // Last 30 days summary from extension events
        long newInstalls = eventRepository.countByTenantIdAndEventType(tenantId, ExtensionEventType.EXTENSION_INSTALLED)
                - countBeforePeriod(tenantId, ExtensionEventType.EXTENSION_INSTALLED, last30d);
        long uninstalls = eventRepository.countByTenantIdAndEventType(tenantId, ExtensionEventType.EXTENSION_UNINSTALLED)
                - countBeforePeriod(tenantId, ExtensionEventType.EXTENSION_UNINSTALLED, last30d);

        // Use direct since-based counts for last 30 days
        long blockedAttempts = eventRepository.countBlockEventsSince(tenantId, last30d);
        long warningsShown = eventRepository.countWarningEventsSince(tenantId, last30d);
        long warningsAcknowledged = eventRepository.countWarningAcknowledgedSince(tenantId, last30d);

        ExtensionDashboardDTO.Last30DaysSummary last30Days = ExtensionDashboardDTO.Last30DaysSummary.builder()
                .newInstallations(eventRepository.countByTenantIdAndEventTimestampAfter(tenantId, last30d)
                        > 0 ? countEventsSince(tenantId, ExtensionEventType.EXTENSION_INSTALLED, last30d) : 0)
                .uninstallations(countEventsSince(tenantId, ExtensionEventType.EXTENSION_UNINSTALLED, last30d))
                .blockedAttempts(blockedAttempts)
                .warningsShown(warningsShown)
                .warningsAcknowledged(warningsAcknowledged)
                .build();

        // Top high risk extensions (top 5)
        List<ExtensionDashboardDTO.InventoryItem> topHighRisk = buildInventoryItems(
                extensionRepository.getExtensionInventory(tenantId)).stream()
                .filter(i -> "HIGH".equals(i.getRiskLevel()) || "CRITICAL".equals(i.getRiskLevel()))
                .limit(5)
                .collect(Collectors.toList());

        // Recent events (last 10)
        List<ExtensionEvent> recentEventEntities = eventRepository.findRecentEvents(tenantId, now.minusDays(7));
        List<ExtensionDashboardDTO.RecentEvent> recentEvents = recentEventEntities.stream()
                .limit(10)
                .map(this::toRecentEvent)
                .collect(Collectors.toList());

        return ExtensionDashboardDTO.Overview.builder()
                .totalUniqueExtensions(uniqueExtensions)
                .totalInstallations(totalInstallations > 0 ? totalInstallations :
                        statusDistribution.values().stream().mapToLong(Long::longValue).sum())
                .activeInstallations(activeInstallations)
                .riskDistribution(riskDistribution)
                .policyDistribution(policyDistribution)
                .statusDistribution(statusDistribution)
                .last30Days(last30Days)
                .topHighRiskExtensions(topHighRisk)
                .recentEvents(recentEvents)
                .build();
    }

    private long countEventsSince(String tenantId, ExtensionEventType type, LocalDateTime since) {
        List<Object[]> counts = eventRepository.countEventsByType(tenantId, since);
        for (Object[] row : counts) {
            if (type.name().equals(row[0] != null ? row[0].toString() : "")) {
                return ((Number) row[1]).longValue();
            }
        }
        return 0;
    }

    private long countBeforePeriod(String tenantId, ExtensionEventType type, LocalDateTime since) {
        // Total minus since = before
        long total = eventRepository.countByTenantIdAndEventType(tenantId, type);
        long sincePeriod = countEventsSince(tenantId, type, since);
        return total - sincePeriod;
    }

    // ==================== 2. INVENTORY ====================

    @Transactional(readOnly = true)
    public Page<ExtensionDashboardDTO.InventoryItem> getInventory(String tenantId, String riskLevel,
                                                                    String policyAction, String search,
                                                                    int page, int size) {
        log.debug("Getting extension inventory for tenant={}", tenantId);

        List<ExtensionDashboardDTO.InventoryItem> allItems = buildInventoryItems(
                extensionRepository.getExtensionInventory(tenantId));

        // Apply filters
        List<ExtensionDashboardDTO.InventoryItem> filtered = allItems.stream()
                .filter(item -> riskLevel == null || riskLevel.equalsIgnoreCase(item.getRiskLevel()))
                .filter(item -> policyAction == null || policyAction.equalsIgnoreCase(item.getPolicyAction()))
                .filter(item -> search == null || search.isBlank()
                        || item.getExtensionName().toLowerCase().contains(search.toLowerCase())
                        || item.getExtensionId().toLowerCase().contains(search.toLowerCase()))
                .collect(Collectors.toList());

        int start = page * size;
        int end = Math.min(start + size, filtered.size());
        List<ExtensionDashboardDTO.InventoryItem> pageContent = start < filtered.size()
                ? filtered.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(pageContent, PageRequest.of(page, size), filtered.size());
    }

    private List<ExtensionDashboardDTO.InventoryItem> buildInventoryItems(List<Object[]> rows) {
        List<ExtensionDashboardDTO.InventoryItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(ExtensionDashboardDTO.InventoryItem.builder()
                    .extensionId(str(row[0]))
                    .extensionName(str(row[1]))
                    .latestVersion(str(row[2]))
                    .riskLevel(str(row[4]))
                    .riskScore(num(row[5]))
                    .policyAction(str(row[7]))
                    .installCount(lng(row[8]))
                    .activeInstallCount(lng(row[9]))
                    .deviceCount(lng(row[10]))
                    .userCount(lng(row[11]))
                    .firstSeenAt(toLocalDateTime(row[12]))
                    .lastSeenAt(toLocalDateTime(row[13]))
                    .isWhitelisted(row[14] != null && (Boolean) row[14])
                    .isBlacklisted(row[15] != null && (Boolean) row[15])
                    .build());
        }
        return items;
    }

    // ==================== 3. EXTENSION DETAIL ====================

    @Transactional(readOnly = true)
    public ExtensionDashboardDTO.ExtensionDetail getExtensionDetail(String tenantId, String extensionId) {
        log.debug("Getting extension detail for tenant={} extensionId={}", tenantId, extensionId);

        List<InstalledExtension> installations = extensionRepository
                .findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(tenantId, extensionId);

        if (installations.isEmpty()) {
            throw new com.secufusion.events.exception.ResourceNotFoundException(
                    "Extension not found: " + extensionId);
        }

        // Pick representative (latest, active preferred)
        InstalledExtension rep = installations.stream()
                .filter(ie -> ie.getStatus() != null && "ACTIVE".equals(ie.getStatus().name()))
                .findFirst()
                .orElse(installations.get(0));

        // Highest risk
        String highestRisk = installations.stream()
                .map(InstalledExtension::getRiskLevel)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(ExtensionDashboardService::riskOrd))
                .orElse(rep.getRiskLevel());

        int maxScore = installations.stream()
                .map(InstalledExtension::getRiskScore)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(rep.getRiskScore() != null ? rep.getRiskScore() : 0);

        // Strictest policy
        String strictestPolicy = installations.stream()
                .map(InstalledExtension::getPolicyAction)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(ExtensionDashboardService::policyOrd))
                .orElse(rep.getPolicyAction());

        // Merge permissions
        Set<String> allPermissions = new LinkedHashSet<>();
        Set<String> allHostPermissions = new LinkedHashSet<>();
        Set<String> allHighRiskPermissions = new LinkedHashSet<>();
        for (InstalledExtension ie : installations) {
            if (ie.getPermissions() != null) allPermissions.addAll(ie.getPermissions());
            if (ie.getHostPermissions() != null) allHostPermissions.addAll(ie.getHostPermissions());
            if (ie.getHighRiskPermissions() != null) allHighRiskPermissions.addAll(ie.getHighRiskPermissions());
        }

        // Installation list
        List<ExtensionDashboardDTO.InstallationInfo> installationInfos = installations.stream()
                .map(ie -> ExtensionDashboardDTO.InstallationInfo.builder()
                        .deviceId(ie.getDevice() != null ? ie.getDevice().getDeviceId() : null)
                        .deviceName(ie.getDevice() != null ? ie.getDevice().getDeviceName() : null)
                        .userName(ie.getUserId())
                        .version(ie.getVersion())
                        .status(ie.getStatus() != null ? ie.getStatus().name() : null)
                        .installedAt(ie.getInstalledAt())
                        .lastSeenAt(ie.getLastSeenAt())
                        .build())
                .collect(Collectors.toList());

        // Version history
        List<ExtensionDashboardDTO.VersionInfo> versionHistory = new ArrayList<>();
        for (Object[] row : extensionRepository.getVersionHistory(tenantId, extensionId)) {
            versionHistory.add(ExtensionDashboardDTO.VersionInfo.builder()
                    .version(str(row[1]))
                    .firstSeen(toLocalDateTime(row[2]))
                    .deviceCount(lng(row[3]))
                    .build());
        }

        // Event history (last 50)
        Page<ExtensionEvent> events = eventRepository.getTenantExtensionHistory(
                tenantId, extensionId, PageRequest.of(0, 50));
        List<ExtensionDashboardDTO.RecentEvent> eventHistory = events.getContent().stream()
                .map(this::toRecentEvent)
                .collect(Collectors.toList());

        long activeCount = installations.stream()
                .filter(ie -> ie.getStatus() != null && "ACTIVE".equals(ie.getStatus().name()))
                .count();

        boolean anyWhitelisted = installations.stream().anyMatch(ie -> Boolean.TRUE.equals(ie.getIsWhitelisted()));
        boolean anyBlacklisted = installations.stream().anyMatch(ie -> Boolean.TRUE.equals(ie.getIsBlacklisted()));

        return ExtensionDashboardDTO.ExtensionDetail.builder()
                .extensionId(rep.getExtensionId())
                .extensionName(rep.getExtensionName())
                .description(rep.getDescription())
                .homepageUrl(rep.getHomepageUrl())
                .storeUrl(rep.getStoreUrl())
                .iconUrl(rep.getIconUrl())
                .latestVersion(rep.getVersion())
                .riskLevel(highestRisk)
                .riskScore(maxScore)
                .policyAction(strictestPolicy)
                .isWhitelisted(anyWhitelisted)
                .isBlacklisted(anyBlacklisted)
                .permissions(new ArrayList<>(allPermissions))
                .hostPermissions(new ArrayList<>(allHostPermissions))
                .highRiskPermissions(allHighRiskPermissions.isEmpty() ? null : new ArrayList<>(allHighRiskPermissions))
                .totalInstallations(installations.size())
                .activeInstallations(activeCount)
                .deviceCount(installations.stream().map(ie -> ie.getDevice() != null ? ie.getDevice().getDeviceId() : "").distinct().count())
                .userCount(installations.stream().map(InstalledExtension::getUserId).filter(Objects::nonNull).distinct().count())
                .installations(installationInfos)
                .versionHistory(versionHistory)
                .eventHistory(eventHistory)
                .build();
    }

    // ==================== 4. TRENDS ====================

    @Transactional(readOnly = true)
    public ExtensionDashboardDTO.Trends getTrends(String tenantId, int days) {
        log.debug("Getting extension trends for tenant={} days={}", tenantId, days);

        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Daily activity
        List<ExtensionDashboardDTO.DailyActivity> dailyActivity = new ArrayList<>();
        for (Object[] row : eventRepository.getDailyActivityBreakdown(tenantId, since)) {
            dailyActivity.add(ExtensionDashboardDTO.DailyActivity.builder()
                    .date(toLocalDate(row[0]))
                    .installed(lng(row[1]))
                    .uninstalled(lng(row[2]))
                    .blocked(lng(row[3]))
                    .warned(lng(row[4]))
                    .build());
        }

        // Top new extensions (installed recently, sorted by install count)
        List<ExtensionDashboardDTO.InventoryItem> topNew = buildInventoryItems(
                extensionRepository.getExtensionInventory(tenantId)).stream()
                .filter(i -> i.getFirstSeenAt() != null && i.getFirstSeenAt().isAfter(since))
                .sorted(Comparator.comparingLong(ExtensionDashboardDTO.InventoryItem::getInstallCount).reversed())
                .limit(10)
                .collect(Collectors.toList());

        // Top removed extensions
        List<ExtensionDashboardDTO.RemovedExtension> topRemoved = new ArrayList<>();
        for (Object[] row : eventRepository.getTopRemovedExtensions(tenantId, since, 10)) {
            topRemoved.add(ExtensionDashboardDTO.RemovedExtension.builder()
                    .extensionId(str(row[0]))
                    .extensionName(str(row[1]))
                    .uninstallCount(lng(row[2]))
                    .build());
        }

        return ExtensionDashboardDTO.Trends.builder()
                .dailyActivity(dailyActivity)
                .topNewExtensions(topNew)
                .topRemovedExtensions(topRemoved)
                .build();
    }

    // ==================== 5. USER RISK PROFILES ====================

    @Transactional(readOnly = true)
    public Page<ExtensionDashboardDTO.UserRiskProfile> getUserRiskProfiles(String tenantId, int page, int size) {
        log.debug("Getting user risk profiles for tenant={}", tenantId);

        List<Object[]> rows = extensionRepository.getUserExtensionRiskProfiles(tenantId);

        List<ExtensionDashboardDTO.UserRiskProfile> profiles = new ArrayList<>();
        for (Object[] row : rows) {
            int riskScore = num(row[7]);
            profiles.add(ExtensionDashboardDTO.UserRiskProfile.builder()
                    .deviceUserId(str(row[0]))
                    .userName(str(row[1]))
                    .email(str(row[2]))
                    .displayName(str(row[3]))
                    .totalExtensions(lng(row[4]))
                    .highRiskExtensions(lng(row[5]))
                    .blockedExtensions(lng(row[6]))
                    .riskScore(riskScore)
                    .riskLevel(getRiskLevel(riskScore))
                    .lastActivityAt(toLocalDateTime(row[8]))
                    .build());
        }

        int start = page * size;
        int end = Math.min(start + size, profiles.size());
        List<ExtensionDashboardDTO.UserRiskProfile> pageContent = start < profiles.size()
                ? profiles.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(pageContent, PageRequest.of(page, size), profiles.size());
    }

    // ==================== 6. POLICY EFFECTIVENESS ====================

    @Transactional(readOnly = true)
    public ExtensionDashboardDTO.PolicyEffectiveness getPolicyEffectiveness(String tenantId, int days) {
        log.debug("Getting policy effectiveness for tenant={} days={}", tenantId, days);

        LocalDateTime since = LocalDateTime.now().minusDays(days);

        long blocked = eventRepository.countBlockEventsSince(tenantId, since);
        long warned = eventRepository.countWarningEventsSince(tenantId, since);
        long acknowledged = eventRepository.countWarningAcknowledgedSince(tenantId, since);
        double ackRate = warned > 0 ? Math.round(acknowledged * 10000.0 / warned) / 100.0 : 0;

        // Count allowed from installed_extensions policy distribution
        long allowed = extensionRepository.countByTenantIdAndPolicyAction(tenantId, "ALLOW");

        long whitelisted = extensionRepository.countWhitelistedExtensions(tenantId);
        long blacklisted = extensionRepository.countBlacklistedExtensions(tenantId);

        // Top blocked — try events first, fall back to installed_extensions policy
        List<ExtensionDashboardDTO.TopBlockedExtension> topBlocked = new ArrayList<>();
        List<Object[]> blockedRows = eventRepository.getMostBlockedWithUsers(tenantId, since, 10);
        if (blockedRows.isEmpty()) {
            blockedRows = extensionRepository.getTopBlockedByPolicy(tenantId, 10);
        }
        for (Object[] row : blockedRows) {
            topBlocked.add(ExtensionDashboardDTO.TopBlockedExtension.builder()
                    .extensionId(str(row[0]))
                    .extensionName(str(row[1]))
                    .blockCount(lng(row[2]))
                    .uniqueUsers(lng(row[3]))
                    .build());
        }

        // Top warned — try events first, fall back to installed_extensions policy
        List<ExtensionDashboardDTO.TopWarnedExtension> topWarned = new ArrayList<>();
        List<Object[]> warnedRows = eventRepository.getMostWarnedExtensions(tenantId, since, 10);
        if (warnedRows.isEmpty()) {
            warnedRows = extensionRepository.getTopWarnedByPolicy(tenantId, 10);
        }
        for (Object[] row : warnedRows) {
            topWarned.add(ExtensionDashboardDTO.TopWarnedExtension.builder()
                    .extensionId(str(row[0]))
                    .extensionName(str(row[1]))
                    .warnCount(lng(row[2]))
                    .acknowledgedCount(lng(row[3]))
                    .build());
        }

        // Total policy evaluations from installed extensions policy distribution
        long blockedPolicyCount = extensionRepository.countByTenantIdAndPolicyAction(tenantId, "BLOCK");
        long warnedPolicyCount = extensionRepository.countByTenantIdAndPolicyAction(tenantId, "WARN");
        long totalEvals = allowed + blockedPolicyCount + warnedPolicyCount;

        return ExtensionDashboardDTO.PolicyEffectiveness.builder()
                .totalPolicyEvaluations(totalEvals)
                .blockedCount(blockedPolicyCount > 0 ? blockedPolicyCount : blocked)
                .warnedCount(warnedPolicyCount > 0 ? warnedPolicyCount : warned)
                .allowedCount(allowed)
                .warningAcknowledgeRate(ackRate)
                .whitelistedCount(whitelisted)
                .blacklistedCount(blacklisted)
                .topBlockedExtensions(topBlocked)
                .topWarnedExtensions(topWarned)
                .build();
    }

    // ==================== 7. BULK ACTION ====================

    @Transactional
    public ExtensionDashboardDTO.BulkActionResult executeBulkAction(String tenantId,
                                                                      ExtensionDashboardDTO.BulkActionRequest request) {
        log.info("Executing bulk action={} for {} extensions in tenant={}",
                request.getAction(), request.getExtensionIds().size(), tenantId);

        List<String> extensionIds = request.getExtensionIds();
        String reason = request.getReason() != null ? request.getReason() : "Bulk action from dashboard";
        int updated;

        switch (request.getAction().toUpperCase()) {
            case "WHITELIST":
                updated = extensionRepository.bulkWhitelist(tenantId, extensionIds, reason);
                break;
            case "BLACKLIST":
                updated = extensionRepository.bulkBlacklist(tenantId, extensionIds, reason);
                break;
            case "BLOCK":
                updated = extensionRepository.bulkUpdatePolicyAction(tenantId, extensionIds, "BLOCK", reason);
                break;
            case "WARN":
                updated = extensionRepository.bulkUpdatePolicyAction(tenantId, extensionIds, "WARN", reason);
                break;
            case "ALLOW":
                updated = extensionRepository.bulkUpdatePolicyAction(tenantId, extensionIds, "ALLOW", reason);
                break;
            default:
                throw new IllegalArgumentException("Invalid action: " + request.getAction()
                        + ". Valid actions: WHITELIST, BLACKLIST, BLOCK, WARN, ALLOW");
        }

        return ExtensionDashboardDTO.BulkActionResult.builder()
                .totalRequested(extensionIds.size())
                .updatedCount(updated)
                .action(request.getAction().toUpperCase())
                .message(updated + " extension installations updated")
                .build();
    }

    // ==================== HELPERS ====================

    private ExtensionDashboardDTO.RecentEvent toRecentEvent(ExtensionEvent e) {
        return ExtensionDashboardDTO.RecentEvent.builder()
                .eventId(e.getPkExtensionEventId())
                .extensionId(e.getExtensionId())
                .extensionName(e.getExtensionName())
                .eventType(e.getEventType() != null ? e.getEventType().name() : null)
                .eventDescription(e.getEventDescription())
                .deviceName(e.getDevice() != null ? e.getDevice().getDeviceName() : null)
                .userName(e.getUserName())
                .policyAction(e.getPolicyAction())
                .riskLevel(e.getRiskLevel())
                .timestamp(e.getEventTimestamp())
                .build();
    }

    private static int riskOrd(String riskLevel) {
        if (riskLevel == null) return 0;
        switch (riskLevel) {
            case "CRITICAL": return 4;
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }

    private static int policyOrd(String policy) {
        if (policy == null) return 0;
        switch (policy) {
            case "BLOCK": return 2;
            case "WARN": return 1;
            default: return 0;
        }
    }

    private static String getRiskLevel(int score) {
        if (score >= 75) return "Critical";
        if (score >= 50) return "High";
        if (score >= 25) return "Medium";
        return "Low";
    }

    private static String str(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private static int num(Object obj) {
        return obj != null ? ((Number) obj).intValue() : 0;
    }

    private static long lng(Object obj) {
        return obj != null ? ((Number) obj).longValue() : 0;
    }

    private static LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Timestamp) return ((Timestamp) obj).toLocalDateTime();
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        return null;
    }

    private static LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Date) return ((Date) obj).toLocalDate();
        if (obj instanceof java.util.Date) return new Date(((java.util.Date) obj).getTime()).toLocalDate();
        if (obj instanceof LocalDate) return (LocalDate) obj;
        return null;
    }
}
