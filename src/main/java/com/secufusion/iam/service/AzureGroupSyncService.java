package com.secufusion.iam.service;

import com.secufusion.iam.dto.AzureResourceDto;
import com.secufusion.iam.dto.AzureSyncStatus;
import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AzureGroupSyncService
 *
 * Asynchronous service for synchronizing Azure AD groups from Microsoft Graph API.
 * Tracks sync status per tenant to prevent duplicate syncs and provide progress updates.
 *
 * Flow:
 * 1. Check if sync already in progress for tenant
 * 2. Start async sync in background
 * 3. Use AzureGraphService to fetch Azure AD groups
 * 4. Create or update EventsGroup records for each Azure group
 * 5. Set authorized=false by default (admin must authorize)
 *
 * Integration: Uses IAM module's AzureGraphService for Microsoft Graph API access
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AzureGroupSyncService {

    private final EventsGroupService eventsGroupService;
    private final TenantRepository tenantRepository;
    private final AzureGraphService azureGraphService;

    private static final String SYNC_USER = "AZURE_SYNC_SERVICE";

    // Cooldown period: 10 minutes between syncs
    private static final long SYNC_COOLDOWN_MINUTES = 10;

    // In-memory cache of sync status per tenant
    private final Map<String, AzureSyncStatus> syncStatusCache = new ConcurrentHashMap<>();

    /**
     * Get current sync status for a tenant
     *
     * @param tenantId Tenant ID
     * @return Current sync status or null if no sync has been performed
     */
    public AzureSyncStatus getSyncStatus(String tenantId) {
        return syncStatusCache.get(tenantId);
    }

    /**
     * Check if sync is currently in progress for a tenant
     *
     * @param tenantId Tenant ID
     * @return true if sync in progress, false otherwise
     */
    public boolean isSyncInProgress(String tenantId) {
        AzureSyncStatus status = syncStatusCache.get(tenantId);
        return status != null && status.getStatus() == AzureSyncStatus.Status.IN_PROGRESS;
    }

    /**
     * Check if tenant can sync (cooldown period check)
     * Returns true if:
     * - No previous sync exists, OR
     * - Previous sync was more than 10 minutes ago
     *
     * @param tenantId Tenant ID
     * @return true if can sync, false if in cooldown
     */
    public boolean canSync(String tenantId) {
        AzureSyncStatus status = syncStatusCache.get(tenantId);

        // No previous sync - can sync
        if (status == null) {
            return true;
        }

        // Currently in progress - cannot sync
        if (status.getStatus() == AzureSyncStatus.Status.IN_PROGRESS) {
            return false;
        }

        // Failed sync - can retry immediately (no cooldown for failures)
        if (status.getStatus() == AzureSyncStatus.Status.FAILED) {
            log.debug("Previous sync failed for tenant: {}. Can retry immediately.", tenantId);
            return true;
        }

        // Successful sync - check cooldown period
        if (status.getStatus() == AzureSyncStatus.Status.COMPLETED && status.getCompletedAt() != null) {
            Instant cooldownExpiry = status.getCompletedAt().plusSeconds(SYNC_COOLDOWN_MINUTES * 60);
            boolean isAfterCooldown = Instant.now().isAfter(cooldownExpiry);

            if (!isAfterCooldown) {
                log.debug("Tenant {} is in cooldown period. Last sync at: {}, can sync again at: {}",
                        tenantId, status.getCompletedAt(), cooldownExpiry);
            }

            return isAfterCooldown;
        }

        // Default: allow sync
        return true;
    }

    /**
     * Get remaining cooldown time in seconds
     *
     * @param tenantId Tenant ID
     * @return Remaining seconds in cooldown, or 0 if can sync
     */
    public long getRemainingCooldownSeconds(String tenantId) {
        AzureSyncStatus status = syncStatusCache.get(tenantId);

        // No status or failed sync - no cooldown
        if (status == null || status.getStatus() == AzureSyncStatus.Status.FAILED) {
            return 0;
        }

        // Only COMPLETED syncs have cooldown
        if (status.getStatus() == AzureSyncStatus.Status.COMPLETED && status.getCompletedAt() != null) {
            Instant cooldownExpiry = status.getCompletedAt().plusSeconds(SYNC_COOLDOWN_MINUTES * 60);
            long remainingSeconds = cooldownExpiry.getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(0, remainingSeconds);
        }

        return 0;
    }

    /**
     * Initiate async Azure group sync for a tenant
     *
     * @param tenantId Tenant ID
     * @return Sync status
     * @throws IllegalStateException if sync already in progress, in cooldown, or tenant not configured for Azure
     */
    public AzureSyncStatus initiateSync(String tenantId) {
        log.info("Initiating Azure group sync for tenant: {}", tenantId);

        // Check if sync already in progress
        if (isSyncInProgress(tenantId)) {
            log.warn("Sync already in progress for tenant: {}", tenantId);
            return syncStatusCache.get(tenantId);
        }

        // Check cooldown period
        if (!canSync(tenantId)) {
            long remainingSeconds = getRemainingCooldownSeconds(tenantId);
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;
            throw new IllegalStateException(
                String.format("Sync cooldown period active. Please wait %d minute(s) and %d second(s) before syncing again.",
                    minutes, seconds)
            );
        }

        // Fetch tenant details
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // Validate tenant has Azure SSO configured
        if (tenant.getAuthProviderConfig() == null ||
            !"AZURE".equalsIgnoreCase(tenant.getAuthProviderConfig().getSsoType())) {
            throw new IllegalStateException("Tenant does not have Azure SSO configured");
        }

        if (tenant.getAzureTenantId() == null || tenant.getAzureTenantId().isBlank()) {
            throw new IllegalStateException("Azure Tenant ID is missing");
        }

        // Create initial status
        AzureSyncStatus status = AzureSyncStatus.builder()
                .tenantId(tenantId)
                .status(AzureSyncStatus.Status.IN_PROGRESS)
                .startedAt(Instant.now())
                .message("Sync in progress. Please wait...")
                .build();

        syncStatusCache.put(tenantId, status);

        // Start async sync
        performAsyncSync(tenant);

        return status;
    }

    /**
     * Perform async sync in background thread
     *
     * @param tenant Tenant entity
     */
    @Async
    @Transactional
    public void performAsyncSync(Tenant tenant) {
        String tenantId = tenant.getTenantID();
        log.info("Starting async Azure group sync for tenant: {}", tenantId);

        int newGroups = 0;
        int updatedGroups = 0;

        try {
            // Fetch groups from Microsoft Graph API
            List<AzureResourceDto> azureGroups = azureGraphService.searchTenantGroups(
                    tenant,
                    null, // No search term - fetch all groups
                    tenant.getAzureTenantId()
            );

            log.info("Fetched {} Azure groups for tenant: {}", azureGroups.size(), tenantId);

            // Get existing groups before sync
            List<EventsGroup> existingGroups = eventsGroupService.getGroupsByType(
                    tenantId,
                    EventsGroup.GroupType.AZURE_GROUP
            );
            Set<String> existingAzureIds = new HashSet<>();
            for (EventsGroup group : existingGroups) {
                if (group.getAzureGroupId() != null) {
                    existingAzureIds.add(group.getAzureGroupId());
                }
            }

            // Sync groups to events_groups table
            for (AzureResourceDto azureGroup : azureGroups) {
                boolean isNew = !existingAzureIds.contains(azureGroup.getId());

                eventsGroupService.createOrUpdateAzureGroup(
                        tenantId,
                        azureGroup.getId(), // Azure group OID
                        azureGroup.getName(), // Display name
                        SYNC_USER
                );

                if (isNew) {
                    newGroups++;
                } else {
                    updatedGroups++;
                }
            }

            // Update status to completed
            AzureSyncStatus completedStatus = AzureSyncStatus.builder()
                    .tenantId(tenantId)
                    .status(AzureSyncStatus.Status.COMPLETED)
                    .totalFetched(azureGroups.size())
                    .newGroups(newGroups)
                    .updatedGroups(updatedGroups)
                    .startedAt(syncStatusCache.get(tenantId).getStartedAt())
                    .completedAt(Instant.now())
                    .message("Successfully synced " + azureGroups.size() + " Azure groups")
                    .build();

            syncStatusCache.put(tenantId, completedStatus);
            log.info("Azure group sync completed for tenant: {}. New: {}, Updated: {}",
                    tenantId, newGroups, updatedGroups);

        } catch (Exception e) {
            log.error("Failed to sync Azure groups for tenant: {}", tenantId, e);

            // Update status to failed
            AzureSyncStatus failedStatus = AzureSyncStatus.builder()
                    .tenantId(tenantId)
                    .status(AzureSyncStatus.Status.FAILED)
                    .startedAt(syncStatusCache.get(tenantId).getStartedAt())
                    .completedAt(Instant.now())
                    .message("Sync failed")
                    .errorMessage(e.getMessage())
                    .build();

            syncStatusCache.put(tenantId, failedStatus);
        }
    }

}
