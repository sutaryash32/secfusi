package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.SubscriptionHistoryDto;
import com.secufusion.tenant.dto.SubscriptionSummary;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing tenant subscriptions.
 * Provides direct database access instead of REST API calls.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final TenantSubscriptionRepository subscriptionRepository;
    private final SubscriptionPackageRepository packageRepository;
    private final BillingCycleRepository billingCycleRepository;
    private final PackagePricingRepository pricingRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionHistoryRepository historyRepository;

    private static final String DEFAULT_PACKAGE_NAME = "Freemium";
    private static final String DEFAULT_BILLING_CYCLE = "MONTHLY";

    // ==================== Deletion Methods ====================

    @Transactional
    public void deleteSubscriptionsByTenantId(String tenantId) {
        log.info("Deleting subscription data for tenantId={}", tenantId);
        historyRepository.deleteByTenantId(tenantId);
        subscriptionRepository.deleteByTenantId(tenantId);
    }

    // ==================== Query Methods ====================

    /**
     * Get active subscription for a tenant.
     */
    public Optional<SubscriptionSummary> getActiveSubscription(String tenantId) {
        log.debug("Getting active subscription for tenantId={}", tenantId);
        return subscriptionRepository.findActiveByTenantId(tenantId)
                .map(this::mapToSummary);
    }

    /**
     * Get subscription history for a tenant.
     */
    public List<SubscriptionSummary> getSubscriptionHistory(String tenantId) {
        log.debug("Getting subscription history for tenantId={}", tenantId);
        return subscriptionRepository.findAllByTenantId(tenantId)
                .stream()
                .map(this::mapToSummary)
                .toList();
    }

    /**
     * Check if tenant has active subscription.
     */
    public boolean hasActiveSubscription(String tenantId) {
        return subscriptionRepository.findActiveByTenantId(tenantId).isPresent();
    }

    /**
     * Get subscription by ID.
     */
    public Optional<SubscriptionSummary> getSubscriptionById(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .map(this::mapToSummary);
    }

    // ==================== Subscription Management ====================

    /**
     * Create a new subscription for a tenant.
     */
    @Transactional
    public SubscriptionSummary createSubscription(String tenantId, Long packageId, Long billingCycleId,
                                                   Boolean startTrial, String createdBy) {
        log.info("Creating subscription for tenantId={}, packageId={}, billingCycleId={}, startTrial={}",
                tenantId, packageId, billingCycleId, startTrial);

        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        SubscriptionPackage pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        BillingCycle billingCycle = billingCycleRepository.findById(billingCycleId)
                .orElseThrow(() -> new IllegalArgumentException("Billing cycle not found: " + billingCycleId));

        // Get pricing for package and billing cycle
        PackagePricing pricing = pricingRepository.findByPackageIdAndBillingCycleId(packageId, billingCycleId)
                .orElse(null);

        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenant(tenant);
        subscription.setPkg(pkg);
        subscription.setBillingCycle(billingCycle);
        subscription.setPricing(pricing);
        subscription.setStartDate(LocalDate.now());
        subscription.setCreatedBy(createdBy);
        subscription.setAutoRenew(true);

        if (Boolean.TRUE.equals(startTrial) && Boolean.TRUE.equals(pkg.getIsTrialAvailable())) {
            setupTrialSubscription(subscription, pkg);
        } else {
            setupPaidSubscription(subscription, billingCycle, pricing);
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Created subscription {} for tenant {}", saved.getPkSubscriptionId(), tenantId);

        // Record history
        SubscriptionHistory.Action action = Boolean.TRUE.equals(startTrial) ?
                SubscriptionHistory.Action.TRIAL_STARTED : SubscriptionHistory.Action.CREATED;
        recordHistory(saved, action, null, pkg, null, billingCycle,
                null, pricing != null ? pricing.getFinalPrice() : null, createdBy, null);

        return mapToSummary(saved);
    }

    /**
     * Create default (Freemium) subscription for a new tenant.
     */
    @Transactional
    public SubscriptionSummary createDefaultSubscription(String tenantId, String createdBy) {
        log.info("Creating default subscription for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        SubscriptionPackage pkg = packageRepository.findByPackageName(DEFAULT_PACKAGE_NAME)
                .orElseThrow(() -> new IllegalArgumentException("Default package not found: " + DEFAULT_PACKAGE_NAME));

        BillingCycle billingCycle = billingCycleRepository.findByCycleCode(DEFAULT_BILLING_CYCLE)
                .orElseThrow(() -> new IllegalArgumentException("Default billing cycle not found: " + DEFAULT_BILLING_CYCLE));

        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenant(tenant);
        subscription.setPkg(pkg);
        subscription.setBillingCycle(billingCycle);
        subscription.setStartDate(LocalDate.now());
        subscription.setStatus(TenantSubscription.Status.ACTIVE);
        subscription.setIsTrial(false);
        subscription.setBillingAmount(BigDecimal.ZERO);
        subscription.setCurrency("USD");
        subscription.setAutoRenew(true);
        subscription.setCreatedBy(createdBy);

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Created default subscription {} for tenant {}", saved.getPkSubscriptionId(), tenantId);

        // Record history
        recordHistory(saved, SubscriptionHistory.Action.CREATED, null, pkg, null, billingCycle,
                null, BigDecimal.ZERO, createdBy, "Default Freemium subscription");

        return mapToSummary(saved);
    }

    /**
     * Upgrade subscription to a new package.
     */
    @Transactional
    public SubscriptionSummary upgradeSubscription(String tenantId, Long newPackageId, Long billingCycleId, String updatedBy) {
        log.info("Upgrading subscription for tenantId={} to packageId={}", tenantId, newPackageId);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("No active subscription found for tenant: " + tenantId));

        SubscriptionPackage newPackage = packageRepository.findById(newPackageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + newPackageId));

        BillingCycle oldBillingCycle = subscription.getBillingCycle();
        BillingCycle billingCycle = billingCycleId != null
                ? billingCycleRepository.findById(billingCycleId).orElse(subscription.getBillingCycle())
                : subscription.getBillingCycle();

        PackagePricing pricing = pricingRepository.findByPackageIdAndBillingCycleId(
                newPackageId, billingCycle.getPkBillingCycleId()).orElse(null);

        // Store previous values for history
        SubscriptionPackage oldPackage = subscription.getPkg();
        BigDecimal oldPrice = subscription.getBillingAmount();
        String oldStatus = subscription.getStatus().name();

        // Store previous package for tracking
        subscription.setPreviousPackage(oldPackage);
        subscription.setUpgradedAt(java.time.Instant.now());

        // Update to new package
        subscription.setPkg(newPackage);
        subscription.setBillingCycle(billingCycle);
        subscription.setPricing(pricing);
        subscription.setUpdatedBy(updatedBy);

        BigDecimal newPrice = null;
        if (pricing != null) {
            newPrice = pricing.getFinalPrice() != null ? pricing.getFinalPrice() : pricing.getBasePrice();
            subscription.setBillingAmount(newPrice);
            subscription.setCurrency(pricing.getCurrency());
        }

        // If was on trial, convert to paid
        if (Boolean.TRUE.equals(subscription.getIsTrial())) {
            subscription.setIsTrial(false);
            subscription.setTrialConverted(true);
            subscription.setStatus(TenantSubscription.Status.ACTIVE);
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Upgraded subscription {} to package {}", saved.getPkSubscriptionId(), newPackageId);

        // Record history
        recordHistory(saved, SubscriptionHistory.Action.UPGRADED, oldPackage, newPackage,
                oldBillingCycle, billingCycle, oldPrice, newPrice, updatedBy,
                "Upgraded from " + oldPackage.getPackageName() + " to " + newPackage.getPackageName());

        return mapToSummary(saved);
    }

    /**
     * Convert trial to paid subscription.
     */
    @Transactional
    public SubscriptionSummary convertTrial(String tenantId, Long billingCycleId, String updatedBy) {
        log.info("Converting trial for tenantId={}", tenantId);

        TenantSubscription subscription = subscriptionRepository.findByTenantIdAndStatus(tenantId, TenantSubscription.Status.TRIAL)
                .orElseThrow(() -> new IllegalArgumentException("No trial subscription found for tenant: " + tenantId));

        BillingCycle oldBillingCycle = subscription.getBillingCycle();
        BillingCycle billingCycle = billingCycleRepository.findById(billingCycleId)
                .orElseThrow(() -> new IllegalArgumentException("Billing cycle not found: " + billingCycleId));

        PackagePricing pricing = pricingRepository.findByPackageIdAndBillingCycleId(
                subscription.getPkg().getPkPackageId(), billingCycleId).orElse(null);

        subscription.setBillingCycle(billingCycle);
        subscription.setPricing(pricing);
        subscription.setIsTrial(false);
        subscription.setTrialConverted(true);
        subscription.setStatus(TenantSubscription.Status.ACTIVE);
        subscription.setUpdatedBy(updatedBy);

        BigDecimal newPrice = null;
        if (pricing != null) {
            newPrice = pricing.getFinalPrice() != null ? pricing.getFinalPrice() : pricing.getBasePrice();
            subscription.setBillingAmount(newPrice);
            subscription.setCurrency(pricing.getCurrency());
        }

        // Set billing dates
        subscription.setLastBillingDate(LocalDate.now());
        if (billingCycle.getDurationMonths() != null && billingCycle.getDurationMonths() > 0) {
            subscription.setNextBillingDate(LocalDate.now().plusMonths(billingCycle.getDurationMonths()));
            subscription.setEndDate(LocalDate.now().plusMonths(billingCycle.getDurationMonths()));
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Converted trial subscription {} to paid", saved.getPkSubscriptionId());

        // Record history
        recordHistory(saved, SubscriptionHistory.Action.TRIAL_CONVERTED, subscription.getPkg(), subscription.getPkg(),
                oldBillingCycle, billingCycle, BigDecimal.ZERO, newPrice, updatedBy, "Trial converted to paid subscription");

        return mapToSummary(saved);
    }

    /**
     * Cancel subscription.
     */
    @Transactional
    public SubscriptionSummary cancelSubscription(String tenantId, String reason, Boolean immediate, String cancelledBy) {
        log.info("Cancelling subscription for tenantId={}, immediate={}", tenantId, immediate);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("No active subscription found for tenant: " + tenantId));

        String oldStatus = subscription.getStatus().name();
        subscription.setCancelledAt(java.time.Instant.now());
        subscription.setCancellationReason(reason);
        subscription.setCancelledBy(cancelledBy);
        subscription.setAutoRenew(false);

        if (Boolean.TRUE.equals(immediate)) {
            subscription.setStatus(TenantSubscription.Status.CANCELLED);
            subscription.setEndDate(LocalDate.now());
        } else {
            // Cancel at end of billing period
            subscription.setStatus(TenantSubscription.Status.CANCELLED);
            // Keep end date as is
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Cancelled subscription {}", saved.getPkSubscriptionId());

        // Record history
        SubscriptionHistory history = SubscriptionHistory.builder()
                .subscription(saved)
                .tenant(saved.getTenant())
                .action(SubscriptionHistory.Action.CANCELLED)
                .fromPackage(saved.getPkg())
                .toPackage(null)
                .fromStatus(oldStatus)
                .toStatus(TenantSubscription.Status.CANCELLED.name())
                .fromBillingCycle(saved.getBillingCycle())
                .toBillingCycle(null)
                .fromPrice(saved.getBillingAmount())
                .toPrice(BigDecimal.ZERO)
                .currency(saved.getCurrency())
                .reason(reason)
                .notes(Boolean.TRUE.equals(immediate) ? "Immediate cancellation" : "Cancel at end of billing period")
                .changedBy(cancelledBy)
                .build();
        historyRepository.save(history);

        return mapToSummary(saved);
    }

    // ==================== Package & Pricing Queries ====================

    /**
     * Get all available packages.
     */
    public List<SubscriptionPackage> getAllPackages() {
        return packageRepository.findAll();
    }

    /**
     * Get package by ID.
     */
    public Optional<SubscriptionPackage> getPackageById(Long packageId) {
        return packageRepository.findById(packageId);
    }

    /**
     * Get pricing options for a package.
     */
    public List<PackagePricing> getPackagePricing(Long packageId) {
        return pricingRepository.findByPackageIdAndActive(packageId);
    }

    /**
     * Get all billing cycles.
     */
    public List<BillingCycle> getActiveBillingCycles() {
        return billingCycleRepository.findByIsActiveTrue();
    }

    /**
     * Find billing cycle ID by code directly (avoids loading all cycles into memory).
     */
    public Optional<Long> findBillingCycleIdByCode(String cycleCode) {
        return billingCycleRepository.findByCycleCode(cycleCode)
                .map(BillingCycle::getPkBillingCycleId);
    }

    // ==================== Helper Methods ====================

    private void setupTrialSubscription(TenantSubscription subscription, SubscriptionPackage pkg) {
        int trialDays = pkg.getTrialDays() != null ? pkg.getTrialDays() : 14;

        subscription.setIsTrial(true);
        subscription.setTrialDays(trialDays);
        subscription.setTrialStartDate(LocalDate.now());
        subscription.setTrialEndDate(LocalDate.now().plusDays(trialDays));
        subscription.setStatus(TenantSubscription.Status.TRIAL);
        subscription.setTrialConverted(false);
        subscription.setEndDate(subscription.getTrialEndDate());
        subscription.setBillingAmount(BigDecimal.ZERO);
        subscription.setNextBillingDate(subscription.getTrialEndDate());
    }

    private void setupPaidSubscription(TenantSubscription subscription, BillingCycle billingCycle, PackagePricing pricing) {
        subscription.setIsTrial(false);
        subscription.setStatus(TenantSubscription.Status.ACTIVE);

        if (billingCycle.getDurationMonths() != null && billingCycle.getDurationMonths() > 0) {
            subscription.setEndDate(LocalDate.now().plusMonths(billingCycle.getDurationMonths()));
            subscription.setNextBillingDate(LocalDate.now().plusMonths(billingCycle.getDurationMonths()));
        }

        subscription.setLastBillingDate(LocalDate.now());

        if (pricing != null) {
            subscription.setBillingAmount(pricing.getFinalPrice() != null ? pricing.getFinalPrice() : pricing.getBasePrice());
            subscription.setCurrency(pricing.getCurrency());
        }
    }

    private SubscriptionSummary mapToSummary(TenantSubscription subscription) {
        return SubscriptionSummary.builder()
                .subscriptionId(subscription.getPkSubscriptionId())
                // Package info
                .packageId(subscription.getPkg().getPkPackageId())
                .packageName(subscription.getPkg().getPackageName())
                .packageType(subscription.getPkg().getPackageType() != null
                        ? subscription.getPkg().getPackageType().getPackageTypeName() : null)
                // Billing cycle info
                .billingCycleId(subscription.getBillingCycle().getPkBillingCycleId())
                .billingCycleCode(subscription.getBillingCycle().getCycleCode())
                .billingCycleName(subscription.getBillingCycle().getCycleName())
                .durationMonths(subscription.getBillingCycle().getDurationMonths())
                // Status
                .status(subscription.getStatus().name())
                .isActive(subscription.isCurrentlyActive())
                // Dates
                .startDate(subscription.getStartDate() != null ? subscription.getStartDate().toString() : null)
                .endDate(subscription.getEndDate() != null ? subscription.getEndDate().toString() : null)
                // Trial info
                .isTrial(subscription.getIsTrial())
                .trialStartDate(subscription.getTrialStartDate() != null ? subscription.getTrialStartDate().toString() : null)
                .trialEndDate(subscription.getTrialEndDate() != null ? subscription.getTrialEndDate().toString() : null)
                .trialDays(subscription.getTrialDays())
                .trialDaysRemaining(subscription.getTrialDaysRemaining())
                .trialConverted(subscription.getTrialConverted())
                // Billing info
                .billingAmount(subscription.getBillingAmount())
                .currency(subscription.getCurrency())
                .nextBillingDate(subscription.getNextBillingDate() != null ? subscription.getNextBillingDate().toString() : null)
                .daysUntilNextBilling(subscription.getDaysUntilNextBilling())
                // Auto-renewal
                .autoRenew(subscription.getAutoRenew())
                // Grace period
                .isInGracePeriod(subscription.isInGracePeriod())
                .build();
    }

    // ==================== History Methods ====================

    /**
     * Record subscription change history.
     */
    private void recordHistory(TenantSubscription subscription, SubscriptionHistory.Action action,
                               SubscriptionPackage fromPackage, SubscriptionPackage toPackage,
                               BillingCycle fromBillingCycle, BillingCycle toBillingCycle,
                               BigDecimal fromPrice, BigDecimal toPrice,
                               String changedBy, String notes) {
        try {
            SubscriptionHistory history = SubscriptionHistory.builder()
                    .subscription(subscription)
                    .tenant(subscription.getTenant())
                    .action(action)
                    .fromPackage(fromPackage)
                    .toPackage(toPackage)
                    .fromStatus(fromPackage != null ? subscription.getStatus().name() : null)
                    .toStatus(subscription.getStatus().name())
                    .fromBillingCycle(fromBillingCycle)
                    .toBillingCycle(toBillingCycle)
                    .fromPrice(fromPrice)
                    .toPrice(toPrice)
                    .currency(subscription.getCurrency())
                    .notes(notes)
                    .changedBy(changedBy)
                    .build();
            historyRepository.save(history);
            log.debug("Recorded subscription history: action={}, tenant={}", action, subscription.getTenant().getTenantID());
        } catch (Exception e) {
            log.warn("Failed to record subscription history: {}", e.getMessage());
            // Don't fail the main operation if history recording fails
        }
    }

    /**
     * Get subscription change history for a tenant.
     */
    public List<SubscriptionHistoryDto> getSubscriptionChangeHistory(String tenantId) {
        log.debug("Getting subscription change history for tenantId={}", tenantId);
        return historyRepository.findByTenantIdOrderByChangedAtDesc(tenantId)
                .stream()
                .map(this::mapToHistoryDto)
                .toList();
    }

    /**
     * Get subscription change history by action type.
     */
    public List<SubscriptionHistoryDto> getSubscriptionHistoryByAction(String tenantId, SubscriptionHistory.Action action) {
        log.debug("Getting subscription history for tenantId={}, action={}", tenantId, action);
        return historyRepository.findByTenantIdAndAction(tenantId, action)
                .stream()
                .map(this::mapToHistoryDto)
                .toList();
    }

    private SubscriptionHistoryDto mapToHistoryDto(SubscriptionHistory history) {
        return SubscriptionHistoryDto.builder()
                .historyId(history.getPkHistoryId())
                .subscriptionId(history.getSubscription().getPkSubscriptionId())
                .tenantId(history.getTenant().getTenantID())
                .action(history.getAction().name())
                .fromPackageName(history.getFromPackage() != null ? history.getFromPackage().getPackageName() : null)
                .toPackageName(history.getToPackage() != null ? history.getToPackage().getPackageName() : null)
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .fromBillingCycle(history.getFromBillingCycle() != null ? history.getFromBillingCycle().getCycleName() : null)
                .toBillingCycle(history.getToBillingCycle() != null ? history.getToBillingCycle().getCycleName() : null)
                .fromPrice(history.getFromPrice())
                .toPrice(history.getToPrice())
                .currency(history.getCurrency())
                .reason(history.getReason())
                .notes(history.getNotes())
                .changedAt(history.getChangedAt())
                .changedBy(history.getChangedBy())
                .build();
    }
}
