package com.secufusion.events.scheduler;

import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task to mark devices as inactive if they haven't been seen
 * within a configurable time threshold.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceInactivityScheduler {

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;

    @Value("24")
    private int inactivityThresholdHours;

    /**
     * Runs every hour to check for inactive devices.
     * Marks devices as INACTIVE if they haven't been seen within the threshold.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void markInactiveDevices() {
        log.info("[DEVICE-INACTIVITY] Starting inactive device check. Threshold: {} hours", inactivityThresholdHours);

        try {
            // Get all distinct tenant IDs
            List<String> tenantIds = deviceRepository.findDistinctTenantIds();
            log.debug("[DEVICE-INACTIVITY] Found {} tenants to process", tenantIds.size());

            int totalMarked = 0;
            for (String tenantId : tenantIds) {
                int marked = deviceService.markInactiveDevices(tenantId, inactivityThresholdHours);
                totalMarked += marked;
                if (marked > 0) {
                    log.info("[DEVICE-INACTIVITY] Marked {} devices inactive for tenant={}", marked, tenantId);
                }
            }

            log.info("[DEVICE-INACTIVITY] Completed. Total devices marked inactive: {}", totalMarked);
        } catch (Exception e) {
            log.error("[DEVICE-INACTIVITY] Error during inactive device check", e);
        }
    }
}
