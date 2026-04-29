package com.secufusion.iam.service;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.entity.Package;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class TenantSubscriptionService {

    @Autowired
    private TenantSubscriptionRepository subscriptionRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private BillingCycleRepository billingCycleRepository;

    @Autowired
    private PackagePricingRepository pricingRepository;

    private static final String DEFAULT_PACKAGE_NAME = "Freemium";
    private static final String DEFAULT_BILLING_CYCLE = "MONTHLY";

    /**
     * Create a new subscription for a tenant.
     */
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        log.info("Creating subscription for tenant: {}, package: {}", request.getTenantId(), request.getPackageId());

        // Validate tenant exists
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + request.getTenantId()));

        // Check if tenant already has an active subscription
        if (subscriptionRepository.hasActiveSubscription(request.getTenantId())) {
            throw new IllegalStateException("Tenant already has an active subscription. Please cancel or upgrade instead.");
        }

        // Validate package
        Package pkg = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + request.getPackageId()));

        // Validate billing cycle
        BillingCycle billingCycle = billingCycleRepository.findById(request.getBillingCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing cycle not found: " + request.getBillingCycleId()));

        // Get pricing
        Optional<PackagePricing> pricingOpt = pricingRepository.findByPackageAndBillingCycle(
                request.getPackageId(), request.getBillingCycleId());

        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenant(tenant);
        subscription.setPkg(pkg);
        subscription.setBillingCycle(billingCycle);
        subscription.setStartDate(LocalDate.now());
        subscription.setAutoRenew(request.getAutoRenew() != null ? request.getAutoRenew() : true);
        subscription.setNotes(request.getNotes());
        subscription.setCreatedBy(request.getCreatedBy());

        // Set pricing if available
        if (pricingOpt.isPresent()) {
            PackagePricing pricing = pricingOpt.get();
            subscription.setPricing(pricing);
            subscription.setBillingAmount(pricing.getFinalPrice());
            subscription.setCurrency(pricing.getCurrency());
        }

        // Handle trial vs paid subscription
        if (Boolean.TRUE.equals(request.getStartTrial())) {
            setupTrialSubscription(subscription, pkg, request.getCustomTrialDays());
        } else {
            setupPaidSubscription(subscription, billingCycle);
        }

        // Also update tenant's subscription package reference
        tenant.setSubscriptionPackage(pkg);
        tenantRepository.save(tenant);

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created: id={}, tenant={}, package={}, status={}",
                saved.getPkSubscriptionId(), tenant.getTenantID(), pkg.getPackageName(), saved.getStatus());

        return mapToResponse(saved);
    }

    /**
     * Create default subscription for a new tenant.
     */
    public SubscriptionResponse createDefaultSubscription(String tenantId, String createdBy) {
        log.info("Creating default subscription for tenant: {}", tenantId);

        Package defaultPackage = packageRepository.findAll().stream()
                .filter(p -> DEFAULT_PACKAGE_NAME.equalsIgnoreCase(p.getPackageName()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Default package not found: " + DEFAULT_PACKAGE_NAME));

        BillingCycle defaultCycle = billingCycleRepository.findByCycleCode(DEFAULT_BILLING_CYCLE)
                .orElseThrow(() -> new ResourceNotFoundException("Default billing cycle not found: " + DEFAULT_BILLING_CYCLE));

        CreateSubscriptionRequest request = CreateSubscriptionRequest.builder()
                .tenantId(tenantId)
                .packageId(defaultPackage.getPkPackageId())
                .billingCycleId(defaultCycle.getPkBillingCycleId())
                .startTrial(false)
                .autoRenew(true)
                .createdBy(createdBy)
                .build();

        return createSubscription(request);
    }

    /**
     * Get active subscription for a tenant.
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getActiveSubscription(String tenantId) {
        log.debug("Getting active subscription for tenant: {}", tenantId);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant: " + tenantId));

        return mapToResponse(subscription);
    }

    /**
     * Get subscription history for a tenant.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getSubscriptionHistory(String tenantId) {
        log.debug("Getting subscription history for tenant: {}", tenantId);

        List<TenantSubscription> subscriptions = subscriptionRepository.findAllByTenantId(tenantId);
        return subscriptions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Upgrade subscription to a new package.
     */
    public SubscriptionResponse upgradeSubscription(String tenantId, Long newPackageId, Long billingCycleId, String updatedBy) {
        log.info("Upgrading subscription for tenant: {} to package: {}", tenantId, newPackageId);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant: " + tenantId));

        Package newPackage = packageRepository.findById(newPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + newPackageId));

        // Store previous package for tracking
        subscription.setPreviousPackage(subscription.getPkg());
        subscription.setUpgradedAt(Instant.now());

        // Update package
        subscription.setPkg(newPackage);

        // Update billing cycle if provided
        if (billingCycleId != null) {
            BillingCycle newCycle = billingCycleRepository.findById(billingCycleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Billing cycle not found: " + billingCycleId));
            subscription.setBillingCycle(newCycle);
        }

        // Update pricing
        Optional<PackagePricing> pricingOpt = pricingRepository.findByPackageAndBillingCycle(
                newPackageId, subscription.getBillingCycle().getPkBillingCycleId());
        if (pricingOpt.isPresent()) {
            subscription.setPricing(pricingOpt.get());
            subscription.setBillingAmount(pricingOpt.get().getFinalPrice());
        }

        // If it was a trial, convert it
        if (subscription.getStatus() == TenantSubscription.Status.TRIAL) {
            subscription.setTrialConverted(true);
            subscription.setStatus(TenantSubscription.Status.ACTIVE);
            subscription.setStartDate(LocalDate.now());
            calculateNextBillingDate(subscription);
        }

        subscription.setUpdatedBy(updatedBy);

        // Update tenant's package reference
        Tenant tenant = subscription.getTenant();
        tenant.setSubscriptionPackage(newPackage);
        tenantRepository.save(tenant);

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription upgraded: tenant={}, newPackage={}", tenantId, newPackage.getPackageName());

        return mapToResponse(saved);
    }

    /**
     * Downgrade subscription to a new package.
     */
    public SubscriptionResponse downgradeSubscription(String tenantId, Long newPackageId, String updatedBy) {
        log.info("Downgrading subscription for tenant: {} to package: {}", tenantId, newPackageId);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant: " + tenantId));

        Package newPackage = packageRepository.findById(newPackageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + newPackageId));

        // Store previous package for tracking
        subscription.setPreviousPackage(subscription.getPkg());
        subscription.setDowngradedAt(Instant.now());

        // Update package
        subscription.setPkg(newPackage);

        // Update pricing
        Optional<PackagePricing> pricingOpt = pricingRepository.findByPackageAndBillingCycle(
                newPackageId, subscription.getBillingCycle().getPkBillingCycleId());
        if (pricingOpt.isPresent()) {
            subscription.setPricing(pricingOpt.get());
            subscription.setBillingAmount(pricingOpt.get().getFinalPrice());
        }

        subscription.setUpdatedBy(updatedBy);

        // Update tenant's package reference
        Tenant tenant = subscription.getTenant();
        tenant.setSubscriptionPackage(newPackage);
        tenantRepository.save(tenant);

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription downgraded: tenant={}, newPackage={}", tenantId, newPackage.getPackageName());

        return mapToResponse(saved);
    }

    /**
     * Convert trial to paid subscription.
     */
    public SubscriptionResponse convertTrial(String tenantId, ConvertTrialRequest request) {
        log.info("Converting trial to paid subscription for tenant: {}", tenantId);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant: " + tenantId));

        if (subscription.getStatus() != TenantSubscription.Status.TRIAL) {
            throw new IllegalStateException("Subscription is not in trial status");
        }

        BillingCycle billingCycle = billingCycleRepository.findById(request.getBillingCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing cycle not found: " + request.getBillingCycleId()));

        subscription.setBillingCycle(billingCycle);
        subscription.setTrialConverted(true);
        subscription.setStatus(TenantSubscription.Status.ACTIVE);
        subscription.setStartDate(LocalDate.now());
        subscription.setAutoRenew(request.getAutoRenew() != null ? request.getAutoRenew() : true);

        // Update pricing
        Optional<PackagePricing> pricingOpt = pricingRepository.findByPackageAndBillingCycle(
                subscription.getPkg().getPkPackageId(), request.getBillingCycleId());
        if (pricingOpt.isPresent()) {
            subscription.setPricing(pricingOpt.get());
            subscription.setBillingAmount(pricingOpt.get().getFinalPrice());
            subscription.setCurrency(pricingOpt.get().getCurrency());
        }

        calculateNextBillingDate(subscription);

        if (request.getNotes() != null) {
            subscription.setNotes(request.getNotes());
        }
        subscription.setUpdatedBy(request.getUpdatedBy());

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Trial converted to paid subscription: tenant={}, billingCycle={}",
                tenantId, billingCycle.getCycleName());

        return mapToResponse(saved);
    }

    /**
     * Cancel subscription.
     */
    public SubscriptionResponse cancelSubscription(String tenantId, CancelSubscriptionRequest request) {
        log.info("Cancelling subscription for tenant: {}", tenantId);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant: " + tenantId));

        subscription.setCancelledAt(Instant.now());
        subscription.setCancellationReason(request.getReason());
        subscription.setCancelledBy(request.getCancelledBy());
        subscription.setAutoRenew(false);

        if (Boolean.TRUE.equals(request.getImmediate())) {
            subscription.setStatus(TenantSubscription.Status.CANCELLED);
            subscription.setEndDate(LocalDate.now());

            // Downgrade tenant to free package
            Package freePackage = packageRepository.findAll().stream()
                    .filter(p -> "Freemium".equalsIgnoreCase(p.getPackageName()))
                    .findFirst()
                    .orElse(null);
            if (freePackage != null) {
                Tenant tenant = subscription.getTenant();
                tenant.setSubscriptionPackage(freePackage);
                tenantRepository.save(tenant);
            }
        } else {
            // Cancel at end of billing period
            subscription.setEndDate(subscription.getNextBillingDate());
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription cancelled: tenant={}, immediate={}", tenantId, request.getImmediate());

        return mapToResponse(saved);
    }

    /**
     * Renew subscription.
     */
    public SubscriptionResponse renewSubscription(String tenantId, String updatedBy) {
        log.info("Renewing subscription for tenant: {}", tenantId);

        TenantSubscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant: " + tenantId));

        if (subscription.getStatus() == TenantSubscription.Status.CANCELLED) {
            throw new IllegalStateException("Cannot renew a cancelled subscription");
        }

        subscription.setLastBillingDate(LocalDate.now());
        calculateNextBillingDate(subscription);
        subscription.setRenewalReminderSent(false);
        subscription.setUpdatedBy(updatedBy);

        // If was in grace period, restore to active
        if (subscription.getStatus() == TenantSubscription.Status.GRACE_PERIOD) {
            subscription.setStatus(TenantSubscription.Status.ACTIVE);
            subscription.setGraceEndDate(null);
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription renewed: tenant={}, nextBilling={}", tenantId, saved.getNextBillingDate());

        return mapToResponse(saved);
    }

    /**
     * Get all pricing options for a package.
     */
    @Transactional(readOnly = true)
    public List<PackagePricingResponse> getPackagePricing(Long packageId) {
        log.debug("Getting pricing for package: {}", packageId);

        List<PackagePricing> pricingList = pricingRepository.findAllByPackageId(packageId);
        return pricingList.stream()
                .map(this::mapToPricingResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all packages with their pricing options.
     */
    @Transactional(readOnly = true)
    public List<PackagePricingResponse.PackageWithPricing> getAllPackagesWithPricing() {
        log.debug("Getting all packages with pricing");

        List<Package> packages = packageRepository.findAll();
        return packages.stream()
                .map(pkg -> {
                    List<PackagePricing> pricingList = pricingRepository.findAllByPackageId(pkg.getPkPackageId());
                    List<PackagePricingResponse.PricingOption> options = pricingList.stream()
                            .map(this::mapToPricingOption)
                            .collect(Collectors.toList());

                    List<String> featureNames = pkg.getFeatures().stream()
                            .map(Feature::getFeatureName)
                            .collect(Collectors.toList());

                    return PackagePricingResponse.PackageWithPricing.builder()
                            .packageId(pkg.getPkPackageId())
                            .packageName(pkg.getPackageName())
                            .packageType(pkg.getPackageType() != null ? pkg.getPackageType().getPackageTypeName() : null)
                            .description(pkg.getDescription())
                            .trialDays(pkg.getTrialDays())
                            .isTrialAvailable(pkg.getIsTrialAvailable())
                            .pricingOptions(options)
                            .features(featureNames)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ==================== Helper Methods ====================

    private void setupTrialSubscription(TenantSubscription subscription, Package pkg, Integer customTrialDays) {
        int trialDays = customTrialDays != null ? customTrialDays :
                (pkg.getTrialDays() != null ? pkg.getTrialDays() : 14);

        subscription.setIsTrial(true);
        subscription.setTrialDays(trialDays);
        subscription.setTrialStartDate(LocalDate.now());
        subscription.setTrialEndDate(LocalDate.now().plusDays(trialDays));
        subscription.setStatus(TenantSubscription.Status.TRIAL);
        subscription.setTrialConverted(false);

        // For trial, set end date same as trial end date
        subscription.setEndDate(subscription.getTrialEndDate());

        // No billing during trial
        subscription.setBillingAmount(BigDecimal.ZERO);
        subscription.setNextBillingDate(subscription.getTrialEndDate());
    }

    private void setupPaidSubscription(TenantSubscription subscription, BillingCycle billingCycle) {
        subscription.setIsTrial(false);
        subscription.setStatus(TenantSubscription.Status.ACTIVE);

        // Calculate end date based on billing cycle
        if (billingCycle.getDurationMonths() != null && billingCycle.getDurationMonths() > 0) {
            subscription.setEndDate(LocalDate.now().plusMonths(billingCycle.getDurationMonths()));
        }

        calculateNextBillingDate(subscription);
        subscription.setLastBillingDate(LocalDate.now());
    }

    private void calculateNextBillingDate(TenantSubscription subscription) {
        BillingCycle cycle = subscription.getBillingCycle();
        if (cycle != null && cycle.getDurationMonths() != null && cycle.getDurationMonths() > 0) {
            LocalDate baseDate = subscription.getLastBillingDate() != null ?
                    subscription.getLastBillingDate() : LocalDate.now();
            subscription.setNextBillingDate(baseDate.plusMonths(cycle.getDurationMonths()));
            subscription.setEndDate(subscription.getNextBillingDate());
        }
    }

    private SubscriptionResponse mapToResponse(TenantSubscription sub) {
        Package pkg = sub.getPkg();
        BillingCycle cycle = sub.getBillingCycle();
        PackagePricing pricing = sub.getPricing();
        Package prevPkg = sub.getPreviousPackage();

        SubscriptionResponse.SubscriptionResponseBuilder builder = SubscriptionResponse.builder()
                .subscriptionId(sub.getPkSubscriptionId())
                .tenantId(sub.getTenant().getTenantID())
                .tenantName(sub.getTenant().getTenantName())
                .packageId(pkg.getPkPackageId())
                .packageName(pkg.getPackageName())
                .packageType(pkg.getPackageType() != null ? pkg.getPackageType().getPackageTypeName() : null)
                .billingCycleId(cycle.getPkBillingCycleId())
                .billingCycleCode(cycle.getCycleCode())
                .billingCycleName(cycle.getCycleName())
                .durationMonths(cycle.getDurationMonths())
                .status(sub.getStatus().name())
                .isActive(sub.isCurrentlyActive())
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .isTrial(sub.getIsTrial())
                .trialStartDate(sub.getTrialStartDate())
                .trialEndDate(sub.getTrialEndDate())
                .trialDays(sub.getTrialDays())
                .trialDaysRemaining(sub.getTrialDaysRemaining())
                .trialConverted(sub.getTrialConverted())
                .billingAmount(sub.getBillingAmount())
                .currency(sub.getCurrency())
                .nextBillingDate(sub.getNextBillingDate())
                .lastBillingDate(sub.getLastBillingDate())
                .daysUntilNextBilling(sub.getDaysUntilNextBilling())
                .autoRenew(sub.getAutoRenew())
                .gracePeriodDays(sub.getGracePeriodDays())
                .graceEndDate(sub.getGraceEndDate())
                .isInGracePeriod(sub.isInGracePeriod())
                .cancelledAt(sub.getCancelledAt())
                .cancellationReason(sub.getCancellationReason())
                .upgradedAt(sub.getUpgradedAt())
                .downgradedAt(sub.getDowngradedAt())
                .notes(sub.getNotes())
                .createdAt(sub.getCreatedAt())
                .updatedAt(sub.getUpdatedAt())
                .createdBy(sub.getCreatedBy());

        if (pricing != null) {
            builder.pricingId(pricing.getPkPricingId())
                    .basePrice(pricing.getBasePrice())
                    .discountPercentage(pricing.getDiscountPercentage())
                    .finalPrice(pricing.getFinalPrice())
                    .pricePerUser(pricing.getPricePerUser())
                    .setupFee(pricing.getSetupFee());
        }

        if (prevPkg != null) {
            builder.previousPackageId(prevPkg.getPkPackageId())
                    .previousPackageName(prevPkg.getPackageName());
        }

        return builder.build();
    }

    private PackagePricingResponse mapToPricingResponse(PackagePricing pricing) {
        Package pkg = pricing.getPkg();
        BillingCycle cycle = pricing.getBillingCycle();

        BigDecimal savings = BigDecimal.ZERO;
        if (pricing.getDiscountPercentage() != null && pricing.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            savings = pricing.getBasePrice().subtract(pricing.getFinalPrice());
        }

        return PackagePricingResponse.builder()
                .pricingId(pricing.getPkPricingId())
                .packageId(pkg.getPkPackageId())
                .packageName(pkg.getPackageName())
                .packageType(pkg.getPackageType() != null ? pkg.getPackageType().getPackageTypeName() : null)
                .packageDescription(pkg.getDescription())
                .trialDays(pkg.getTrialDays())
                .isTrialAvailable(pkg.getIsTrialAvailable())
                .billingCycleId(cycle.getPkBillingCycleId())
                .billingCycleCode(cycle.getCycleCode())
                .billingCycleName(cycle.getCycleName())
                .durationMonths(cycle.getDurationMonths())
                .basePrice(pricing.getBasePrice())
                .currency(pricing.getCurrency())
                .discountPercentage(pricing.getDiscountPercentage())
                .finalPrice(pricing.getFinalPrice())
                .savingsAmount(savings)
                .savingsPercentage(pricing.getDiscountPercentage())
                .pricePerUser(pricing.getPricePerUser())
                .minUsers(pricing.getMinUsers())
                .maxUsers(pricing.getMaxUsers())
                .setupFee(pricing.getSetupFee())
                .validFrom(pricing.getValidFrom())
                .validUntil(pricing.getValidUntil())
                .isActive(pricing.getIsActive())
                .build();
    }

    private PackagePricingResponse.PricingOption mapToPricingOption(PackagePricing pricing) {
        BillingCycle cycle = pricing.getBillingCycle();

        // Calculate monthly equivalent price
        BigDecimal monthlyEquivalent = pricing.getFinalPrice();
        if (cycle.getDurationMonths() != null && cycle.getDurationMonths() > 0) {
            monthlyEquivalent = pricing.getFinalPrice()
                    .divide(BigDecimal.valueOf(cycle.getDurationMonths()), 2, RoundingMode.HALF_UP);
        }

        // Determine if this is the popular option (annual is typically most popular)
        boolean isPopular = "ANNUAL".equals(cycle.getCycleCode());

        // Badge for best value
        String badge = null;
        if ("ANNUAL".equals(cycle.getCycleCode())) {
            badge = "Most Popular";
        } else if ("BIENNIAL".equals(cycle.getCycleCode())) {
            badge = "Best Value";
        }

        return PackagePricingResponse.PricingOption.builder()
                .pricingId(pricing.getPkPricingId())
                .billingCycleId(cycle.getPkBillingCycleId())
                .billingCycleCode(cycle.getCycleCode())
                .billingCycleName(cycle.getCycleName())
                .durationMonths(cycle.getDurationMonths())
                .basePrice(pricing.getBasePrice())
                .finalPrice(pricing.getFinalPrice())
                .discountPercentage(pricing.getDiscountPercentage())
                .monthlyEquivalent(monthlyEquivalent)
                .currency(pricing.getCurrency())
                .isPopular(isPopular)
                .badge(badge)
                .build();
    }
}
