package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddonPricingResponse {

    private Long featureId;
    private String featureCode;
    private String featureName;
    private String featureDescription;
    private String featureGroupName;
    private BigDecimal monthlyPrice;
    private Integer trialDays;

    // Pricing for selected billing cycle (when specific cycle requested)
    private Long billingCycleId;
    private String billingCycleCode;
    private String billingCycleName;
    private BigDecimal price;
    private BigDecimal discountPercentage;
    private String currency;

    /**
     * Response with all pricing options for an addon feature.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddonWithAllPricing {
        private Long featureId;
        private String featureCode;
        private String featureName;
        private String featureDescription;
        private String featureGroupName;
        private BigDecimal monthlyPrice;
        private Integer trialDays;
        private List<PricingOption> pricingOptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingOption {
        private Long billingCycleId;
        private String billingCycleCode;
        private String billingCycleName;
        private Integer durationMonths;
        private BigDecimal price;
        private BigDecimal discountPercentage;
        private BigDecimal savingsAmount; // Savings compared to monthly
        private String currency;
    }
}
