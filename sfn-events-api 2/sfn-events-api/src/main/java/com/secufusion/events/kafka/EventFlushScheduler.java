package com.secufusion.events.kafka;

import com.secufusion.events.repository.EventInboxRepository;
import com.secufusion.events.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduler that periodically flushes buffered tenant events by delegating to {@code EventService}.
 *
 * <p>Runs on a fixed rate schedule. The scheduler reads the current set of buffered tenant ids
 * from {@code EventInboxRepository} and requests a sync for each tenant.
 *
 * <p>Detailed logging and robust exception handling are added to ensure failures for individual
 * tenants do not stop the overall scheduled job.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class EventFlushScheduler {

    private final EventInboxRepository inboxRepository;
    private final EventService eventService;

    @Scheduled(fixedRateString = "#{${kafka.schedule.time:20} * 60 * 1000}")
    public void flushAllTenants() {
        Instant start = Instant.now();
        log.info("Scheduled flushAllTenants started at {}", start);

        List<String> tenants;
        try {
            tenants = inboxRepository.findTenantsWithPendingEvents();
            log.debug("Retrieved tenant list: size={}", tenants == null ? 0 : tenants.size());
        } catch (Throwable t) {
            log.error("Unexpected error while fetching tenants with pending events. Aborting this run.", t);
            return;
        }

        if (tenants == null || tenants.isEmpty()) {
            log.info("No tenants with pending events found. Completed run in {} ms.",
                    Duration.between(start, Instant.now()).toMillis());
            return;
        }

        List<String> failedTenants = new ArrayList<>();
        int successCount = 0;

        for (String tenantId : tenants) {
            if (tenantId == null || tenantId.trim().isEmpty()) {
                log.warn("Skipping invalid tenant id: '{}'", tenantId);
                failedTenants.add(String.valueOf(tenantId));
                continue;
            }

            Instant tenantStart = Instant.now();
            try {
                log.debug("Flushing tenant '{}'", tenantId);
                eventService.flushTenant(tenantId);
                successCount++;
                log.info("Successfully flushed tenant '{}' in {} ms",
                        tenantId, Duration.between(tenantStart, Instant.now()).toMillis());
            } catch (Throwable t) {
                log.error("Failed to flush tenant '{}'. Error will be recorded and processing will continue.", tenantId, t);
                failedTenants.add(tenantId);

                try {
                    // Optional: Add any tenant-level failure handling here, e.g., mark in repository.
                    // inboxRepository.markTenantFlushFailed(tenantId, t.getMessage());
                } catch (Throwable nested) {
                    log.warn("Additional error while handling failure for tenant '{}'", tenantId, nested);
                }
            }
        }

        long totalMs = Duration.between(start, Instant.now()).toMillis();
        log.info("Scheduled flushAllTenants completed in {} ms. attempted={}, succeeded={}, failed={}",
                totalMs, tenants.size(), successCount, failedTenants.size());

        if (!failedTenants.isEmpty()) {
            log.warn("Tenants that failed during this run: {}", String.join(", ", failedTenants));
        }
    }
}