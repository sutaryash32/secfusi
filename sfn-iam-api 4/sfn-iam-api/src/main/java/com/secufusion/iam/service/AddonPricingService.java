package com.secufusion.iam.service;

import com.secufusion.iam.dto.AddonPricingResponse;
import com.secufusion.iam.entity.AddonPricing;
import com.secufusion.iam.entity.Feature;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AddonPricingRepository;
import com.secufusion.iam.repository.FeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddonPricingService {

    private final AddonPricingRepository addonPricingRepository;
    private final FeatureRepository featureRepository;

    /**
     * Get all available addon features with their pricing options.
     */
    @Transactional(readOnly = true)
    public List<AddonPricingResponse.AddonWithAllPricing> getAllAddonFeaturesWithPricing() {
        log.debug("Fetching all addon features with pricing");

        List<AddonPricing> allPricing = addonPricingRepository.findAllAddonPricing();

        // Group by feature
        Map<Long, List<AddonPricing>> pricingByFeature = allPricing.stream()
                .collect(Collectors.groupingBy(ap -> ap.getFeature().getPkFeatureID()));

        return pricingByFeature.entrySet().stream()
                .map(entry -> {
                    List<AddonPricing> featurePricing = entry.getValue();
                    Feature feature = featurePricing.get(0).getFeature();

                    List<AddonPricingResponse.PricingOption> pricingOptions = featurePricing.stream()
                            .map(this::mapToPricingOption)
                            .collect(Collectors.toList());

                    return AddonPricingResponse.AddonWithAllPricing.builder()
                            .featureId(feature.getPkFeatureID())
                            .featureCode(feature.getFeatureCode())
                            .featureName(feature.getFeatureName())
                            .featureDescription(feature.getDescription())
                            .featureGroupName(feature.getFeatureGroup() != null
                                    ? feature.getFeatureGroup().getGroupName() : null)
                            .monthlyPrice(feature.getAddonMonthlyPrice())
                            .trialDays(feature.getAddonTrialDays())
                            .pricingOptions(pricingOptions)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get pricing options for a specific addon feature.
     */
    @Transactional(readOnly = true)
    public AddonPricingResponse.AddonWithAllPricing getAddonPricingByFeatureCode(String featureCode) {
        log.debug("Fetching pricing for addon feature: {}", featureCode);

        Feature feature = featureRepository.findByFeatureCode(featureCode)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + featureCode));

        if (!Boolean.TRUE.equals(feature.getIsAddon())) {
            throw new IllegalArgumentException("Feature is not an addon: " + featureCode);
        }

        List<AddonPricing> pricingOptions = addonPricingRepository.findAllByFeatureCode(featureCode);

        List<AddonPricingResponse.PricingOption> options = pricingOptions.stream()
                .map(this::mapToPricingOption)
                .collect(Collectors.toList());

        return AddonPricingResponse.AddonWithAllPricing.builder()
                .featureId(feature.getPkFeatureID())
                .featureCode(feature.getFeatureCode())
                .featureName(feature.getFeatureName())
                .featureDescription(feature.getDescription())
                .featureGroupName(feature.getFeatureGroup() != null
                        ? feature.getFeatureGroup().getGroupName() : null)
                .monthlyPrice(feature.getAddonMonthlyPrice())
                .trialDays(feature.getAddonTrialDays())
                .pricingOptions(options)
                .build();
    }

    /**
     * Get pricing for a specific addon with a specific billing cycle.
     */
    @Transactional(readOnly = true)
    public AddonPricingResponse getAddonPricing(String featureCode, String billingCycleCode) {
        log.debug("Fetching pricing for addon {} with billing cycle {}", featureCode, billingCycleCode);

        AddonPricing pricing = addonPricingRepository
                .findByFeatureCodeAndBillingCycleCode(featureCode, billingCycleCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pricing not found for feature: " + featureCode + " with billing cycle: " + billingCycleCode));

        Feature feature = pricing.getFeature();

        return AddonPricingResponse.builder()
                .featureId(feature.getPkFeatureID())
                .featureCode(feature.getFeatureCode())
                .featureName(feature.getFeatureName())
                .featureDescription(feature.getDescription())
                .featureGroupName(feature.getFeatureGroup() != null
                        ? feature.getFeatureGroup().getGroupName() : null)
                .monthlyPrice(feature.getAddonMonthlyPrice())
                .trialDays(feature.getAddonTrialDays())
                .billingCycleId(pricing.getBillingCycle().getPkBillingCycleId())
                .billingCycleCode(pricing.getBillingCycle().getCycleCode())
                .billingCycleName(pricing.getBillingCycle().getCycleName())
                .price(pricing.getPrice())
                .discountPercentage(pricing.getDiscountPercentage())
                .currency(pricing.getCurrency())
                .build();
    }

    /**
     * Get all addon features (without pricing details).
     */
    @Transactional(readOnly = true)
    public List<Feature> getAllAddonFeatures() {
        return featureRepository.findByIsAddonTrueAndIsActiveTrue();
    }

    private AddonPricingResponse.PricingOption mapToPricingOption(AddonPricing pricing) {
        Feature feature = pricing.getFeature();
        BigDecimal monthlyTotal = feature.getAddonMonthlyPrice()
                .multiply(BigDecimal.valueOf(pricing.getBillingCycle().getDurationMonths()));
        BigDecimal savings = monthlyTotal.subtract(pricing.getPrice()).setScale(2, RoundingMode.HALF_UP);

        return AddonPricingResponse.PricingOption.builder()
                .billingCycleId(pricing.getBillingCycle().getPkBillingCycleId())
                .billingCycleCode(pricing.getBillingCycle().getCycleCode())
                .billingCycleName(pricing.getBillingCycle().getCycleName())
                .durationMonths(pricing.getBillingCycle().getDurationMonths())
                .price(pricing.getPrice())
                .discountPercentage(pricing.getDiscountPercentage())
                .savingsAmount(savings.compareTo(BigDecimal.ZERO) > 0 ? savings : BigDecimal.ZERO)
                .currency(pricing.getCurrency())
                .build();
    }
}
