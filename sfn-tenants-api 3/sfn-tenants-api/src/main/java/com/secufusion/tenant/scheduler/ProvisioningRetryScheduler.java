package com.secufusion.tenant.scheduler;

import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that retries stuck tenant provisioning.
 * <p>
 * Picks up tenants that have been in a non-terminal state (CREATING, CREATED_LOCAL,
 * REALM_CREATED, CLIENT_CREATED, USER_CREATED, FAILED) for more than 2 minutes
 * and resumes their provisioning.
 * <p>
 * This handles:
 * - Server restarts during provisioning
 * - Background thread crashes
 * - Transient Keycloak failures
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProvisioningRetryScheduler {

    private final TenantRepository tenantRepository;
    private final TenantService tenantService;

    /**
     * Runs every 5 minutes. Picks up tenants stuck for > 2 minutes and retries.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void retryStuckProvisioning() {

        Instant cutoff = Instant.now().minus(2, ChronoUnit.MINUTES);
        List<Tenant> stuck = tenantRepository.findStuckProvisioningTenants(cutoff);

        if (stuck.isEmpty()) {
            return;
        }

        log.info("[RETRY-SCHEDULER] Found {} stuck tenant(s) to retry", stuck.size());

        for (Tenant tenant : stuck) {
            try {
                log.info("[RETRY-SCHEDULER] Retrying provisioning for tenant '{}' (status={})",
                        tenant.getTenantName(), tenant.getStatus());

                tenantService.retryProvisioning(tenant.getTenantID());

                log.info("[RETRY-SCHEDULER] Retry submitted for tenant '{}'", tenant.getTenantName());
            } catch (Exception e) {
                log.warn("[RETRY-SCHEDULER] Could not retry tenant '{}': {}",
                        tenant.getTenantName(), e.getMessage());
            }
        }
    }
}
