package com.secufusion.events.scheduler;

import com.secufusion.events.entity.ExtensionApiKey;
import com.secufusion.events.repository.ExtensionApiKeyRepository;
import com.secufusion.events.service.ExtensionApiKeyConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ExtensionApiKeyScheduler
 *
 * Scheduled jobs for Extension API Key maintenance.
 * - Auto-expire keys that have passed their expiry date
 * - Send notifications for keys expiring soon (future enhancement)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExtensionApiKeyScheduler {

    private final ExtensionApiKeyRepository apiKeyRepository;
    private final ExtensionApiKeyConfigurationService configService;

    /**
     * Auto-expire stale API keys
     * Runs every hour, starting 1 minute after application startup
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000) // 1 hour interval, 1 minute initial delay
    @Transactional
    public void expireStaleKeys() {
        // Check if auto-disable is enabled
        if (!configService.isAutoDisableAfterExpiry()) {
            log.debug("[API-KEY-SCHEDULER] Auto-disable is disabled, skipping expiry check");
            return;
        }

        log.info("[API-KEY-SCHEDULER] Running auto-expiry job");

        LocalDateTime now = LocalDateTime.now();
        List<ExtensionApiKey> expiredKeys = apiKeyRepository.findExpiredActiveKeys(now);

        if (expiredKeys.isEmpty()) {
            log.info("[API-KEY-SCHEDULER] No expired keys found");
            return;
        }

        log.info("[API-KEY-SCHEDULER] Found {} expired keys to disable", expiredKeys.size());

        int disabledCount = 0;
        for (ExtensionApiKey key : expiredKeys) {
            try {
                key.setStatus("EXPIRED");
                key.setUpdatedBy("SYSTEM_AUTO_EXPIRE");
                apiKeyRepository.save(key);
                disabledCount++;

                log.info("[API-KEY-SCHEDULER] Expired key: keyId={}, prefix={}, tenant={}",
                    key.getPkExtensionApiKeyId(), key.getKeyPrefix(), key.getTenantId());

            } catch (Exception e) {
                log.error("[API-KEY-SCHEDULER] Failed to expire key: keyId={}",
                    key.getPkExtensionApiKeyId(), e);
            }
        }

        log.info("[API-KEY-SCHEDULER] Auto-expiry job completed: disabled {}/{} keys",
            disabledCount, expiredKeys.size());
    }

    /**
     * Log warning for keys expiring soon
     * Runs every 6 hours, starting 5 minutes after application startup
     */
    @Scheduled(fixedDelay = 21_600_000, initialDelay = 300_000) // 6 hours interval, 5 minutes initial delay
    @Transactional(readOnly = true)
    public void warnExpiringKeys() {
        log.info("[API-KEY-SCHEDULER] Running expiry warning check");

        LocalDateTime now = LocalDateTime.now();
        int warningDays = configService.getExpiryWarningThresholdDays();
        LocalDateTime threshold = now.plusDays(warningDays);

        List<ExtensionApiKey> expiringKeys = apiKeyRepository.findKeysExpiringSoon(now, threshold);

        if (expiringKeys.isEmpty()) {
            log.info("[API-KEY-SCHEDULER] No keys expiring soon");
            return;
        }

        log.warn("[API-KEY-SCHEDULER] {} keys expiring within {} days", expiringKeys.size(), warningDays);

        for (ExtensionApiKey key : expiringKeys) {
            long daysRemaining = java.time.Duration.between(now, key.getExpiresAt()).toDays();
            log.warn("[API-KEY-SCHEDULER] Key expiring soon: keyId={}, prefix={}, tenant={}, daysRemaining={}",
                key.getPkExtensionApiKeyId(), key.getKeyPrefix(), key.getTenantId(), daysRemaining);

            // TODO: Send notification to owner email
            // Future enhancement: integrate with notification service
        }

        log.info("[API-KEY-SCHEDULER] Expiry warning check completed");
    }

    /**
     * Cleanup old rotation history
     * Runs once per day, starting 10 minutes after application startup
     * Keeps last 90 days of rotation history
     */
    @Scheduled(fixedDelay = 86_400_000, initialDelay = 600_000) // 24 hours interval, 10 minutes initial delay
    @Transactional
    public void cleanupOldRotationHistory() {
        log.info("[API-KEY-SCHEDULER] Running rotation history cleanup");

        // TODO: Implement rotation history cleanup
        // Delete rotation records older than 90 days
        // Future enhancement based on requirements

        log.info("[API-KEY-SCHEDULER] Rotation history cleanup completed");
    }
}
