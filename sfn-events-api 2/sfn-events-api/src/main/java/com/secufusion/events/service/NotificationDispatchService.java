package com.secufusion.events.service;

import com.secufusion.events.entity.Notification;
import com.secufusion.events.entity.NotificationPreference;
import com.secufusion.events.entity.User;
import com.secufusion.events.event.IncidentNotificationEvent;
import com.secufusion.events.repository.NotificationPreferenceRepository;
import com.secufusion.events.repository.NotificationRepository;
import com.secufusion.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationEmailService notificationEmailService;

    /**
     * Dispatches notifications directly to the DB and sends emails.
     * Called from IncidentNotificationListener on a background thread (after DB commit).
     */
    public void dispatch(IncidentNotificationEvent event) {
        log.info("dispatch - type={} tenant={} severity={} actorUserId={}",
                event.getType(), event.getTenantId(), event.getSeverity(), event.getActorUserId());

        List<User> targetUsers = resolveTargetUsers(event);

        if (targetUsers.isEmpty()) {
            log.warn("dispatch - no target users resolved for type={} tenant={}",
                    event.getType(), event.getTenantId());
            return;
        }

        // Exclude actor (don't notify yourself)
        // actorUserId from JWT is keycloakUserId — resolve to pkUserId for consistent comparison
        String actorKeycloakId = event.getActorUserId();
        if (actorKeycloakId != null) {
            String actorPkUserId = resolveActorPkUserId(actorKeycloakId, event.getTenantId());
            if (actorPkUserId != null) {
                targetUsers.removeIf(u -> actorPkUserId.equals(u.getPkUserId()));
            }
        }

        if (targetUsers.isEmpty()) {
            log.debug("dispatch - all users filtered (actor only).");
            return;
        }

        // Bulk preference lookup (2 queries instead of N)
        String tenantId = event.getTenantId();
        String category = mapTypeToCategory(event.getType());
        Map<String, NotificationPreference> prefByUserId = buildPreferenceMap(tenantId, category);

        // Build batch lists per channel based on user preferences
        List<Notification> inAppBatch = new ArrayList<>();
        List<User> emailTargets = new ArrayList<>();
        Instant now = Instant.now();

        for (User user : targetUsers) {
            NotificationPreference pref = prefByUserId.get(user.getPkUserId());

            // No preference → all channels enabled (default behavior)
            boolean inAppEnabled = (pref == null) || pref.isInAppEnabled();
            boolean emailEnabled = (pref == null) || pref.isEmailEnabled();

            // Check minimum severity filter
            if (pref != null && pref.getMinSeverity() != null) {
                int eventRank = severityRank(event.getSeverity());
                int minRank = severityRank(pref.getMinSeverity());
                if (eventRank > minRank) {
                    continue; // Skip this user — severity too low
                }
            }

            if (inAppEnabled) {
                inAppBatch.add(buildNotification(event, user, now));
            }

            if (emailEnabled) {
                emailTargets.add(user);
            }
        }

        // Batch save IN_APP notifications
        if (!inAppBatch.isEmpty()) {
            saveNotifications(inAppBatch);
        }

        // Send emails asynchronously — each email runs in its own thread so
        // slow SMTP connections don't block the dispatch or each other
        for (User user : emailTargets) {
            CompletableFuture.runAsync(() -> {
                try {
                    notificationEmailService.sendNotificationEmail(event, user);
                } catch (Exception e) {
                    log.error("dispatch - email failed for user={}", user.getEmail(), e);
                }
            });
        }

        log.info("dispatch - completed. type={} inApp={} email={}",
                event.getType(), inAppBatch.size(), emailTargets.size());
    }

    @Transactional
    protected void saveNotifications(List<Notification> notifications) {
        notificationRepository.saveAll(notifications);
        log.debug("dispatch - persisted {} in-app notifications", notifications.size());
    }

    // ==================== TARGET USER RESOLUTION ====================

    private List<User> resolveTargetUsers(IncidentNotificationEvent event) {
        // Priority 1: specific user IDs (pkUserId — the frontend sends DB primary keys)
        if (event.getTargetUserIds() != null && !event.getTargetUserIds().isEmpty()) {
            List<Serializable> ids = new ArrayList<>(event.getTargetUserIds());
            List<User> users = new ArrayList<>(userRepository.findAllById(ids));
            log.debug("resolveTargetUsers - resolved {} users by pkUserId", users.size());
            return users;
        }

        // Priority 2: all users in the tenant
        List<User> allUsers = userRepository.findByTenant_TenantIDOrderByUserNameAsc(event.getTenantId());
        log.debug("resolveTargetUsers - resolved {} users (all tenant users)", allUsers.size());
        return new ArrayList<>(allUsers);
    }

    private String resolveActorPkUserId(String keycloakUserId, String tenantId) {
        return userRepository.findByKeycloakUserIdAndTenant_TenantID(keycloakUserId, tenantId)
                .map(User::getPkUserId)
                .orElse(null);
    }

    // ==================== PREFERENCE RESOLUTION (bulk-optimized) ====================

    /**
     * Build userId → preference map using 2 bulk queries.
     * Category-specific prefs override default (category=null) prefs.
     * Empty map = no preferences set = all channels enabled for everyone.
     */
    private Map<String, NotificationPreference> buildPreferenceMap(String tenantId, String category) {
        Map<String, NotificationPreference> prefMap = new HashMap<>();

        // Load default preferences (category=null)
        preferenceRepository.findByFkTenantIdAndCategoryIsNull(tenantId)
                .forEach(p -> prefMap.put(p.getFkUserId(), p));

        // Override with category-specific preferences
        if (category != null) {
            preferenceRepository.findByFkTenantIdAndCategory(tenantId, category)
                    .forEach(p -> prefMap.put(p.getFkUserId(), p));
        }

        return prefMap;
    }

    private String mapTypeToCategory(String type) {
        if (type == null) return null;
        if (type.startsWith("INCIDENT_")) return "INCIDENT";
        if (type.startsWith("LOGIN_") || type.startsWith("ACCOUNT_") || type.startsWith("SUSPICIOUS_")) return "SECURITY";
        if (type.startsWith("USER_") || type.startsWith("ROLE_")) return "USER_MANAGEMENT";
        if (type.startsWith("SYSTEM_")) return "SYSTEM";
        return null;
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

    // ==================== NOTIFICATION BUILDER ====================

    private Notification buildNotification(IncidentNotificationEvent event, User user, Instant now) {
        return Notification.builder()
                .fkTenantId(event.getTenantId())
                .fkUserId(user.getPkUserId())
                .type(event.getType())
                .severity(event.getSeverity() != null ? event.getSeverity() : "INFO")
                .title(event.getNotificationTitle())
                .message(event.getMessage())
                .sourceService("sfn-events-api")
                .sourceEntityId(event.getIncidentId())
                .sourceEntityType("INCIDENT")
                .channels("EMAIL,IN_APP")
                .isRead(false)
                .metadata(event.getMetadata())
                .createdAt(now)
                .build();
    }
}
