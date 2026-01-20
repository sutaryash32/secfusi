package com.secufusion.iam.service;

import com.secufusion.iam.dto.BulkTenantAddonFeatureRequest;
import com.secufusion.iam.dto.CreateTenantAddonFeatureRequest;
import com.secufusion.iam.dto.PurchaseAddonRequest;
import com.secufusion.iam.dto.TenantAddonFeatureResponse;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAddonFeatureService {

    private final TenantAddonFeatureRepository tenantAddonFeatureRepository;
    private final TenantRepository tenantRepository;
    private final FeatureRepository featureRepository;
    private final AccessLevelRepository accessLevelRepository;
    private final RetentionPeriodRepository retentionPeriodRepository;
    private final AddonPricingRepository addonPricingRepository;
    private final BillingCycleRepository billingCycleRepository;

    @Transactional
    public TenantAddonFeatureResponse createAddonFeature(CreateTenantAddonFeatureRequest request, String createdBy) {
        log.info("Creating addon feature for tenant: {}", request.getTenantId());

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + request.getTenantId()));

        Feature feature = resolveFeature(request.getFeatureId(), request.getFeatureCode());

        if (tenantAddonFeatureRepository.existsByTenantTenantIDAndFeaturePkFeatureID(
                request.getTenantId(), feature.getPkFeatureID())) {
            throw new IllegalArgumentException("Addon feature already exists for this tenant and feature");
        }

        AccessLevel accessLevel = accessLevelRepository.findById(request.getAccessLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + request.getAccessLevelId()));

        RetentionPeriod retentionPeriod = null;
        if (request.getRetentionPeriodId() != null) {
            retentionPeriod = retentionPeriodRepository.findById(request.getRetentionPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + request.getRetentionPeriodId()));
        }

        TenantAddonFeature addon = new TenantAddonFeature();
        addon.setTenant(tenant);
        addon.setFeature(feature);
        addon.setAccessLevel(accessLevel);
        addon.setRetentionPeriod(retentionPeriod);
        addon.setIsEnabled(true);
        addon.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        addon.setEndDate(request.getEndDate());
        addon.setIsTrial(request.getIsTrial() != null ? request.getIsTrial() : false);
        addon.setCustomConfig(request.getCustomConfig());
        addon.setCreatedBy(createdBy);
        addon.setUpdatedBy(createdBy);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        log.info("Created addon feature with ID: {}", saved.getPkAddonId());

        return mapToResponse(saved);
    }

    @Transactional
    public List<TenantAddonFeatureResponse> createBulkAddonFeatures(BulkTenantAddonFeatureRequest request, String createdBy) {
        log.info("Creating bulk addon features for tenant: {}", request.getTenantId());

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + request.getTenantId()));

        List<TenantAddonFeatureResponse> responses = new ArrayList<>();

        for (BulkTenantAddonFeatureRequest.AddonFeatureItem item : request.getAddons()) {
            Feature feature = resolveFeature(item.getFeatureId(), item.getFeatureCode());

            if (tenantAddonFeatureRepository.existsByTenantTenantIDAndFeaturePkFeatureID(
                    request.getTenantId(), feature.getPkFeatureID())) {
                log.warn("Skipping duplicate addon for feature: {}", feature.getFeatureCode());
                continue;
            }

            AccessLevel accessLevel = accessLevelRepository.findById(item.getAccessLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + item.getAccessLevelId()));

            RetentionPeriod retentionPeriod = null;
            if (item.getRetentionPeriodId() != null) {
                retentionPeriod = retentionPeriodRepository.findById(item.getRetentionPeriodId())
                        .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + item.getRetentionPeriodId()));
            }

            TenantAddonFeature addon = new TenantAddonFeature();
            addon.setTenant(tenant);
            addon.setFeature(feature);
            addon.setAccessLevel(accessLevel);
            addon.setRetentionPeriod(retentionPeriod);
            addon.setIsEnabled(true);
            addon.setStartDate(LocalDate.now());
            addon.setIsTrial(false);
            addon.setCreatedBy(createdBy);
            addon.setUpdatedBy(createdBy);

            TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
            responses.add(mapToResponse(saved));
        }

        log.info("Created {} addon features for tenant: {}", responses.size(), request.getTenantId());
        return responses;
    }

    public List<TenantAddonFeatureResponse> getAddonsByTenant(String tenantId) {
        log.debug("Fetching addons for tenant: {}", tenantId);
        List<TenantAddonFeature> addons = tenantAddonFeatureRepository.findByTenantTenantID(tenantId);
        return addons.stream().map(this::mapToResponse).toList();
    }

    public List<TenantAddonFeatureResponse> getActiveAddonsByTenant(String tenantId) {
        log.debug("Fetching active addons for tenant: {}", tenantId);
        List<TenantAddonFeature> addons = tenantAddonFeatureRepository.findAllActiveAddonsByTenant(
                tenantId, LocalDate.now());
        return addons.stream().map(this::mapToResponse).toList();
    }

    public TenantAddonFeatureResponse getAddonById(Long addonId) {
        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon feature not found: " + addonId));
        return mapToResponse(addon);
    }

    public Optional<TenantAddonFeatureResponse> getAddonByTenantAndFeatureCode(String tenantId, String featureCode) {
        return tenantAddonFeatureRepository.findActiveAddonByTenantAndFeatureCode(
                tenantId, featureCode, LocalDate.now())
                .map(this::mapToResponse);
    }

    @Transactional
    public TenantAddonFeatureResponse updateAddonFeature(Long addonId, CreateTenantAddonFeatureRequest request, String updatedBy) {
        log.info("Updating addon feature: {}", addonId);

        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon feature not found: " + addonId));

        if (request.getAccessLevelId() != null) {
            AccessLevel accessLevel = accessLevelRepository.findById(request.getAccessLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + request.getAccessLevelId()));
            addon.setAccessLevel(accessLevel);
        }

        if (request.getRetentionPeriodId() != null) {
            RetentionPeriod retentionPeriod = retentionPeriodRepository.findById(request.getRetentionPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + request.getRetentionPeriodId()));
            addon.setRetentionPeriod(retentionPeriod);
        }

        if (request.getStartDate() != null) {
            addon.setStartDate(request.getStartDate());
        }

        if (request.getEndDate() != null) {
            addon.setEndDate(request.getEndDate());
        }

        if (request.getIsTrial() != null) {
            addon.setIsTrial(request.getIsTrial());
        }

        if (request.getCustomConfig() != null) {
            addon.setCustomConfig(request.getCustomConfig());
        }

        addon.setUpdatedBy(updatedBy);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        log.info("Updated addon feature: {}", addonId);

        return mapToResponse(saved);
    }

    @Transactional
    public TenantAddonFeatureResponse toggleAddonFeature(Long addonId, boolean enabled, String updatedBy) {
        log.info("Toggling addon feature {} to enabled={}", addonId, enabled);

        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon feature not found: " + addonId));

        addon.setIsEnabled(enabled);
        addon.setUpdatedBy(updatedBy);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteAddonFeature(Long addonId) {
        log.info("Deleting addon feature: {}", addonId);
        if (!tenantAddonFeatureRepository.existsById(addonId)) {
            throw new ResourceNotFoundException("Addon feature not found: " + addonId);
        }
        tenantAddonFeatureRepository.deleteById(addonId);
    }

    @Transactional
    public void deleteAddonByTenantAndFeature(String tenantId, Long featureId) {
        log.info("Deleting addon for tenant: {} and feature: {}", tenantId, featureId);
        tenantAddonFeatureRepository.deleteByTenantTenantIDAndFeaturePkFeatureID(tenantId, featureId);
    }

    public boolean hasActiveAddon(String tenantId, String featureCode) {
        return tenantAddonFeatureRepository.findActiveAddonByTenantAndFeatureCode(
                tenantId, featureCode, LocalDate.now()).isPresent();
    }

    /**
     * Purchase an addon feature for a tenant with billing.
     * This is the main method for paid addon purchases.
     */
    @Transactional
    public TenantAddonFeatureResponse purchaseAddon(String tenantId, PurchaseAddonRequest request, String purchasedBy) {
        log.info("Processing addon purchase for tenant: {}, feature: {}", tenantId, request.getFeatureId());

        // 1. Validate tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // 2. Resolve feature
        Feature feature = resolveFeature(request.getFeatureId(), request.getFeatureCode());

        // 3. Validate feature is an addon
        if (!Boolean.TRUE.equals(feature.getIsAddon())) {
            throw new IllegalArgumentException("Feature is not available as an addon: " + feature.getFeatureCode());
        }

        // 4. Check if already purchased
        if (tenantAddonFeatureRepository.existsByTenantTenantIDAndFeaturePkFeatureID(tenantId, feature.getPkFeatureID())) {
            throw new IllegalArgumentException("Addon already exists for this tenant. Use upgrade or renew instead.");
        }

        // 5. Get billing cycle
        BillingCycle billingCycle = billingCycleRepository.findById(request.getBillingCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing cycle not found: " + request.getBillingCycleId()));

        // 6. Get pricing
        AddonPricing pricing = addonPricingRepository
                .findByFeatureAndBillingCycle(feature.getPkFeatureID(), billingCycle.getPkBillingCycleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pricing not available for this feature and billing cycle combination"));

        // 7. Determine access level (default to FULL if not specified)
        AccessLevel accessLevel;
        if (request.getAccessLevelId() != null) {
            accessLevel = accessLevelRepository.findById(request.getAccessLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + request.getAccessLevelId()));
        } else {
            // Default to highest access level (FULL)
            accessLevel = accessLevelRepository.findByLevelCode("YES")
                    .orElseThrow(() -> new ResourceNotFoundException("Default access level not found"));
        }

        // 8. Determine retention period if applicable
        RetentionPeriod retentionPeriod = null;
        if (request.getRetentionPeriodId() != null) {
            retentionPeriod = retentionPeriodRepository.findById(request.getRetentionPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + request.getRetentionPeriodId()));
        }

        // 9. Calculate dates
        LocalDate startDate = LocalDate.now();
        LocalDate endDate;
        boolean isTrial = Boolean.TRUE.equals(request.getStartTrial()) &&
                feature.getAddonTrialDays() != null && feature.getAddonTrialDays() > 0;

        if (isTrial) {
            endDate = startDate.plusDays(feature.getAddonTrialDays());
            log.info("Starting addon trial for {} days", feature.getAddonTrialDays());
        } else {
            endDate = startDate.plusMonths(billingCycle.getDurationMonths());
        }

        // 10. Create addon record
        TenantAddonFeature addon = new TenantAddonFeature();
        addon.setTenant(tenant);
        addon.setFeature(feature);
        addon.setAccessLevel(accessLevel);
        addon.setRetentionPeriod(retentionPeriod);
        addon.setIsEnabled(true);
        addon.setStartDate(startDate);
        addon.setEndDate(endDate);
        addon.setIsTrial(isTrial);
        addon.setCreatedBy(purchasedBy);
        addon.setUpdatedBy(purchasedBy);

        // 11. Store billing info in custom config
        Map<String, Object> billingConfig = new HashMap<>();
        billingConfig.put("billingCycleId", billingCycle.getPkBillingCycleId());
        billingConfig.put("billingCycleCode", billingCycle.getCycleCode());
        billingConfig.put("price", pricing.getPrice());
        billingConfig.put("currency", pricing.getCurrency());
        billingConfig.put("paymentMethodId", request.getPaymentMethodId());
        billingConfig.put("nextBillingDate", endDate.toString());
        addon.setCustomConfig(billingConfig);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        log.info("Addon purchased successfully. ID: {}, Price: {} {}, Trial: {}",
                saved.getPkAddonId(), pricing.getPrice(), pricing.getCurrency(), isTrial);

        return mapToResponse(saved);
    }

    /**
     * Renew an existing addon subscription.
     */
    @Transactional
    public TenantAddonFeatureResponse renewAddon(Long addonId, Long billingCycleId, String renewedBy) {
        log.info("Renewing addon: {}", addonId);

        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon not found: " + addonId));

        Feature feature = addon.getFeature();
        if (!Boolean.TRUE.equals(feature.getIsAddon())) {
            throw new IllegalArgumentException("Feature is not a purchasable addon");
        }

        BillingCycle billingCycle = billingCycleRepository.findById(billingCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing cycle not found: " + billingCycleId));

        AddonPricing pricing = addonPricingRepository
                .findByFeatureAndBillingCycle(feature.getPkFeatureID(), billingCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing not available"));

        // Extend from current end date or today, whichever is later
        LocalDate renewalStart = addon.getEndDate() != null && addon.getEndDate().isAfter(LocalDate.now())
                ? addon.getEndDate()
                : LocalDate.now();
        LocalDate newEndDate = renewalStart.plusMonths(billingCycle.getDurationMonths());

        addon.setEndDate(newEndDate);
        addon.setIsTrial(false);
        addon.setIsEnabled(true);
        addon.setUpdatedBy(renewedBy);

        // Update billing info
        Map<String, Object> config = addon.getCustomConfig() != null ? addon.getCustomConfig() : new HashMap<>();
        config.put("billingCycleId", billingCycle.getPkBillingCycleId());
        config.put("billingCycleCode", billingCycle.getCycleCode());
        config.put("price", pricing.getPrice());
        config.put("lastRenewalDate", LocalDate.now().toString());
        config.put("nextBillingDate", newEndDate.toString());
        addon.setCustomConfig(config);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        log.info("Addon renewed successfully. New end date: {}", newEndDate);

        return mapToResponse(saved);
    }

    /**
     * Convert an addon trial to paid subscription.
     */
    @Transactional
    public TenantAddonFeatureResponse convertAddonTrial(Long addonId, Long billingCycleId, String convertedBy) {
        log.info("Converting addon trial to paid: {}", addonId);

        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon not found: " + addonId));

        if (!Boolean.TRUE.equals(addon.getIsTrial())) {
            throw new IllegalArgumentException("Addon is not in trial state");
        }

        Feature feature = addon.getFeature();

        BillingCycle billingCycle = billingCycleRepository.findById(billingCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing cycle not found: " + billingCycleId));

        AddonPricing pricing = addonPricingRepository
                .findByFeatureAndBillingCycle(feature.getPkFeatureID(), billingCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing not available"));

        // Start paid period from today
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(billingCycle.getDurationMonths());

        addon.setStartDate(startDate);
        addon.setEndDate(endDate);
        addon.setIsTrial(false);
        addon.setUpdatedBy(convertedBy);

        // Update billing info
        Map<String, Object> config = addon.getCustomConfig() != null ? addon.getCustomConfig() : new HashMap<>();
        config.put("billingCycleId", billingCycle.getPkBillingCycleId());
        config.put("billingCycleCode", billingCycle.getCycleCode());
        config.put("price", pricing.getPrice());
        config.put("trialConvertedAt", LocalDate.now().toString());
        config.put("nextBillingDate", endDate.toString());
        addon.setCustomConfig(config);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        log.info("Addon trial converted to paid. End date: {}", endDate);

        return mapToResponse(saved);
    }

    /**
     * Get addon billing summary for a tenant.
     */
    public Map<String, Object> getAddonBillingSummary(String tenantId) {
        List<TenantAddonFeature> activeAddons = tenantAddonFeatureRepository
                .findAllActiveAddonsByTenant(tenantId, LocalDate.now());

        BigDecimal totalMonthlyValue = BigDecimal.ZERO;
        List<Map<String, Object>> addonDetails = new ArrayList<>();

        for (TenantAddonFeature addon : activeAddons) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("addonId", addon.getPkAddonId());
            detail.put("featureCode", addon.getFeature().getFeatureCode());
            detail.put("featureName", addon.getFeature().getFeatureName());
            detail.put("isTrial", addon.getIsTrial());
            detail.put("endDate", addon.getEndDate());

            if (addon.getCustomConfig() != null) {
                Object price = addon.getCustomConfig().get("price");
                Object billingCycleCode = addon.getCustomConfig().get("billingCycleCode");
                detail.put("price", price);
                detail.put("billingCycleCode", billingCycleCode);

                if (price != null && !Boolean.TRUE.equals(addon.getIsTrial())) {
                    BigDecimal addonPrice = price instanceof BigDecimal
                            ? (BigDecimal) price
                            : new BigDecimal(price.toString());
                    totalMonthlyValue = totalMonthlyValue.add(addonPrice);
                }
            }

            addonDetails.add(detail);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("tenantId", tenantId);
        summary.put("activeAddonCount", activeAddons.size());
        summary.put("totalValue", totalMonthlyValue);
        summary.put("addons", addonDetails);

        return summary;
    }

    private Feature resolveFeature(Long featureId, String featureCode) {
        if (featureId != null) {
            return featureRepository.findById(featureId)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + featureId));
        } else if (featureCode != null && !featureCode.isEmpty()) {
            return featureRepository.findByFeatureCode(featureCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found with code: " + featureCode));
        } else {
            throw new IllegalArgumentException("Either featureId or featureCode must be provided");
        }
    }

    private TenantAddonFeatureResponse mapToResponse(TenantAddonFeature addon) {
        LocalDate today = LocalDate.now();
        boolean isExpired = addon.getEndDate() != null && addon.getEndDate().isBefore(today);

        return TenantAddonFeatureResponse.builder()
                .pkAddonId(addon.getPkAddonId())
                .tenantId(addon.getTenant().getTenantID())
                .tenantName(addon.getTenant().getTenantName())
                .featureId(addon.getFeature().getPkFeatureID())
                .featureName(addon.getFeature().getFeatureName())
                .featureCode(addon.getFeature().getFeatureCode())
                .featureGroupName(addon.getFeature().getFeatureGroup() != null
                        ? addon.getFeature().getFeatureGroup().getGroupName() : null)
                .accessLevelId(addon.getAccessLevel().getPkAccessLevelId())
                .accessLevelName(addon.getAccessLevel().getLevelName())
                .accessLevelValue(addon.getAccessLevel().getLevelValue())
                .retentionPeriodId(addon.getRetentionPeriod() != null
                        ? addon.getRetentionPeriod().getPkRetentionPeriodId() : null)
                .retentionPeriodName(addon.getRetentionPeriod() != null
                        ? addon.getRetentionPeriod().getPeriodName() : null)
                .retentionDays(addon.getRetentionPeriod() != null
                        ? addon.getRetentionPeriod().getPeriodDays() : null)
                .isEnabled(addon.getIsEnabled())
                .startDate(addon.getStartDate())
                .endDate(addon.getEndDate())
                .isTrial(addon.getIsTrial())
                .isExpired(isExpired)
                .customConfig(addon.getCustomConfig())
                .createdAt(addon.getCreatedAt())
                .updatedAt(addon.getUpdatedAt())
                .createdBy(addon.getCreatedBy())
                .updatedBy(addon.getUpdatedBy())
                .build();
    }
}
