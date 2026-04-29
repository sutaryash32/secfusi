package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.Notification;
import com.secufusion.tenant.entity.NotificationPreference;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.NotificationPreferenceRepository;
import com.secufusion.tenant.repository.NotificationRepository;
import com.secufusion.tenant.repository.UserRepository;
import com.secufusion.tenant.util.NotificationWsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final NotificationWsPublisher wsPublisher;
    private final NotificationEmailService notificationEmailService;
    private final WebhookService webhookService;

    private static final DateTimeFormatter INSTANT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    // ==================== DISPATCH (called by Kafka consumer) ====================

    public void dispatch(NotificationEvent event) {
        log.info("dispatch - type={} tenant={} severity={} actorUserId={}",
                event.getType(), event.getTenantId(), event.getSeverity(), event.getActorUserId());

        List<User> targetUsers = resolveTargetUsers(event);

        if (targetUsers.isEmpty()) {
            log.warn("dispatch - no target users resolved for type={} tenant={}",
                    event.getType(), event.getTenantId());
            wsPublisher.broadcastToTenant(event);
            return;
        }

        // Fix 1: Skip the actor (don't notify yourself)
        String actorUserId = event.getActorUserId();
        if (actorUserId != null) {
            targetUsers.removeIf(u -> actorUserId.equals(u.getKeycloakUserId()));
        }

        if (targetUsers.isEmpty()) {
            log.debug("dispatch - all users filtered (actor only). Broadcasting to tenant WS.");
            wsPublisher.broadcastToTenant(event);
            return;
        }

        String tenantId = event.getTenantId();
        String category = mapTypeToCategory(event.getType());

        // Fix 5: Bulk preference lookup (1-2 queries instead of N)
        Map<String, NotificationPreference> prefByUserId = buildPreferenceMap(tenantId, category);

        Set<String> requestedChannels = event.getChannels() != null
                ? new HashSet<>(event.getChannels())
                : Set.of("EMAIL", "WEBSOCKET", "IN_APP");

        // Fix 3: Batch IN_APP persistence
        List<Notification> inAppBatch = new ArrayList<>();

        for (User user : targetUsers) {
            Set<String> enabledChannels = resolveChannelsFromMap(
                    event, user.getPkUserId(), prefByUserId, requestedChannels);

            if (enabledChannels.isEmpty()) {
                continue;
            }

            if (enabledChannels.contains("IN_APP")) {
                inAppBatch.add(buildNotification(event, user));
            }

            if (enabledChannels.contains("WEBSOCKET")) {
                wsPublisher.sendToUser(event, user.getKeycloakUserId());
            }

            // Fix 4: Email is sent async (non-blocking)
            if (enabledChannels.contains("EMAIL")) {
                notificationEmailService.sendNotificationEmail(event, user);
            }
        }

        // Batch save all IN_APP notifications at once (own transaction)
        if (!inAppBatch.isEmpty()) {
            saveInAppNotifications(inAppBatch);
        }

        // Always broadcast to tenant WS channel for dashboard widgets
        wsPublisher.broadcastToTenant(event);

        // Deliver to configured webhooks only if at least one user has webhooks enabled
        boolean anyWebhookEnabled = targetUsers.stream().anyMatch(u -> {
            NotificationPreference p = prefByUserId.get(u.getPkUserId());
            return p == null || p.isWebhookEnabled();
        });
        if (anyWebhookEnabled) {
            webhookService.deliverToWebhooks(event);
        }
    }

    @Transactional
    protected void saveInAppNotifications(List<Notification> notifications) {
        notificationRepository.saveAll(notifications);
        log.debug("dispatch - persisted {} in-app notifications", notifications.size());
    }

    // ==================== TARGET USER RESOLUTION ====================

    private List<User> resolveTargetUsers(NotificationEvent event) {
        // Priority 1: specific user IDs (pkUserId — the frontend sends DB primary keys)
        if (event.getTargetUserIds() != null && !event.getTargetUserIds().isEmpty()) {
            List<Serializable> ids = new ArrayList<>(event.getTargetUserIds());
            return new ArrayList<>(userRepository.findAllById(ids));
        }

        // Priority 1.5: role-based targeting
        if (event.getTargetRole() != null && !event.getTargetRole().isBlank()) {
            List<User> roleUsers = userRepository.findByTenantIdAndRoleName(
                    event.getTenantId(), event.getTargetRole());
            if (!roleUsers.isEmpty()) {
                log.debug("resolveTargetUsers - resolved {} users by role '{}'",
                        roleUsers.size(), event.getTargetRole());
                return new ArrayList<>(roleUsers);
            }
            log.warn("resolveTargetUsers - no users found for role '{}' in tenant {}. Falling through to broadcast.",
                    event.getTargetRole(), event.getTenantId());
        }

        // Priority 2: all users in tenant
        return new ArrayList<>(userRepository.findByTenant_TenantID(event.getTenantId()));
    }

    // ==================== CHANNEL RESOLUTION (bulk-optimized) ====================

    /**
     * Build a map of userId → preference for the given category.
     * Falls back to default preferences (category=null) for users without category-specific prefs.
     * Uses 2 bulk queries instead of N individual queries.
     */
    private Map<String, NotificationPreference> buildPreferenceMap(String tenantId, String category) {
        Map<String, NotificationPreference> prefMap = new HashMap<>();

        // Load default preferences (category=null) first
        preferenceRepository.findByFkTenantIdAndCategoryIsNull(tenantId)
                .forEach(p -> prefMap.put(p.getFkUserId(), p));

        // Override with category-specific preferences
        if (category != null) {
            preferenceRepository.findByFkTenantIdAndCategory(tenantId, category)
                    .forEach(p -> prefMap.put(p.getFkUserId(), p));
        }

        return prefMap;
    }

    private Set<String> resolveChannelsFromMap(NotificationEvent event, String userId,
                                                Map<String, NotificationPreference> prefMap,
                                                Set<String> requestedChannels) {
        NotificationPreference p = prefMap.get(userId);

        // No preference → all requested channels enabled (default)
        if (p == null) {
            return requestedChannels;
        }

        // Check minimum severity
        if (p.getMinSeverity() != null) {
            int eventRank = severityRank(event.getSeverity());
            int minRank = severityRank(p.getMinSeverity());
            if (eventRank > minRank) {
                return Collections.emptySet();
            }
        }

        // Filter channels based on user preference
        Set<String> enabled = new HashSet<>();
        if (p.isEmailEnabled() && requestedChannels.contains("EMAIL")) enabled.add("EMAIL");
        if (p.isWebsocketEnabled() && requestedChannels.contains("WEBSOCKET")) enabled.add("WEBSOCKET");
        if (p.isInAppEnabled() && requestedChannels.contains("IN_APP")) enabled.add("IN_APP");
        return enabled;
    }

    private int severityRank(String severity) {
        return switch (severity != null ? severity.toUpperCase() : "INFO") {
            case "CRITICAL" -> 1;
            case "HIGH" -> 2;
            case "MEDIUM" -> 3;
            case "LOW" -> 4;
            case "INFO" -> 5;
            default -> 5;
        };
    }

    // ==================== PERSISTENCE ====================

    private Notification buildNotification(NotificationEvent event, User user) {
        return Notification.builder()
                .fkTenantId(event.getTenantId())
                .fkUserId(user.getPkUserId())
                .type(event.getType())
                .severity(event.getSeverity() != null ? event.getSeverity() : "INFO")
                .title(event.getTitle())
                .message(event.getMessage())
                .sourceService(event.getSourceService())
                .sourceEntityId(event.getSourceEntityId())
                .sourceEntityType(event.getSourceEntityType())
                .channels(event.getChannels() != null ? String.join(",", event.getChannels()) : null)
                .isRead(false)
                .metadata(event.getMetadata())
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();
    }

    private String mapTypeToCategory(String type) {
        if (type == null) return null;
        if (type.startsWith("INCIDENT_")) return "INCIDENT";
        if (type.startsWith("LOGIN_") || type.startsWith("ACCOUNT_") || type.startsWith("SUSPICIOUS_")) return "SECURITY";
        if (type.startsWith("USER_") || type.startsWith("ROLE_")) return "USER_MANAGEMENT";
        if (type.startsWith("SYSTEM_")) return "SYSTEM";
        return null;
    }

    // ==================== REST API METHODS ====================

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getUserNotifications(String tenantId, String userId, Pageable pageable) {
        return notificationRepository
                .findByFkTenantIdAndFkUserIdOrderByCreatedAtDesc(tenantId, userId, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public UnreadCountDTO getUnreadCount(String tenantId, String userId) {
        long total = notificationRepository.countByFkTenantIdAndFkUserIdAndIsReadFalse(tenantId, userId);

        Map<String, Long> byType = new LinkedHashMap<>();
        notificationRepository.countUnreadByType(tenantId, userId).forEach(row ->
                byType.put((String) row[0], ((Number) row[1]).longValue()));

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        notificationRepository.countUnreadBySeverity(tenantId, userId).forEach(row ->
                bySeverity.put((String) row[0], ((Number) row[1]).longValue()));

        return UnreadCountDTO.builder()
                .totalUnread(total)
                .byType(byType)
                .bySeverity(bySeverity)
                .build();
    }

    @Transactional
    public NotificationDTO markAsRead(String tenantId, String userId, String notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (!n.getFkTenantId().equals(tenantId) || !n.getFkUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }

        n.setRead(true);
        n.setReadAt(Instant.now());
        return convertToDTO(notificationRepository.save(n));
    }

    @Transactional
    public int markAllAsRead(String tenantId, String userId) {
        return notificationRepository.markAllAsRead(tenantId, userId, Instant.now());
    }

    // ==================== PREFERENCES ====================

    @Transactional(readOnly = true)
    public List<NotificationPreferenceDTO> getPreferences(String tenantId, String userId) {
        return preferenceRepository.findByFkTenantIdAndFkUserId(tenantId, userId)
                .stream()
                .map(this::convertToPreferenceDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<NotificationPreferenceDTO> updatePreferences(String tenantId, String userId,
                                                               List<NotificationPreferenceDTO> prefs) {
        List<NotificationPreferenceDTO> results = new ArrayList<>();

        for (NotificationPreferenceDTO dto : prefs) {
            Optional<NotificationPreference> existing = (dto.getCategory() != null)
                    ? preferenceRepository.findByFkTenantIdAndFkUserIdAndCategory(tenantId, userId, dto.getCategory())
                    : preferenceRepository.findByFkTenantIdAndFkUserIdAndCategoryIsNull(tenantId, userId);

            NotificationPreference pref;
            if (existing.isPresent()) {
                pref = existing.get();
                pref.setEmailEnabled(dto.isEmailEnabled());
                pref.setWebsocketEnabled(dto.isWebsocketEnabled());
                pref.setInAppEnabled(dto.isInAppEnabled());
                pref.setMinSeverity(dto.getMinSeverity());
            } else {
                pref = NotificationPreference.builder()
                        .fkTenantId(tenantId)
                        .fkUserId(userId)
                        .category(dto.getCategory())
                        .emailEnabled(dto.isEmailEnabled())
                        .websocketEnabled(dto.isWebsocketEnabled())
                        .inAppEnabled(dto.isInAppEnabled())
                        .minSeverity(dto.getMinSeverity())
                        .build();
            }

            pref = preferenceRepository.save(pref);
            results.add(convertToPreferenceDTO(pref));
        }

        return results;
    }

    // ==================== CONVERTERS ====================

    private NotificationDTO convertToDTO(Notification n) {
        return NotificationDTO.builder()
                .notificationId(n.getPkNotificationId())
                .type(n.getType())
                .severity(n.getSeverity())
                .title(n.getTitle())
                .message(n.getMessage())
                .sourceService(n.getSourceService())
                .sourceEntityId(n.getSourceEntityId())
                .sourceEntityType(n.getSourceEntityType())
                .isRead(n.isRead())
                .readAt(formatInstant(n.getReadAt()))
                .createdAt(formatInstant(n.getCreatedAt()))
                .metadata(n.getMetadata())
                .build();
    }

    private NotificationPreferenceDTO convertToPreferenceDTO(NotificationPreference p) {
        return NotificationPreferenceDTO.builder()
                .preferenceId(p.getPkPreferenceId())
                .category(p.getCategory())
                .emailEnabled(p.isEmailEnabled())
                .websocketEnabled(p.isWebsocketEnabled())
                .inAppEnabled(p.isInAppEnabled())
                .minSeverity(p.getMinSeverity())
                .build();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? INSTANT_FORMATTER.format(instant) : null;
    }
}
