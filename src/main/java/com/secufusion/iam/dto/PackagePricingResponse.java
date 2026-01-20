package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackagePricingResponse {

    private Long pricingId;

    // Package info
    private Long packageId;
    private String packageName;
    private String packageType;
    private String packageDescription;
    private Integer trialDays;
    private Boolean isTrialAvailable;

    // Billing cycle info
    private Long billingCycleId;
    private String billingCycleCode;
    private String billingCycleName;
    private Integer durationMonths;

    // Pricing details
    private BigDecimal basePrice;
    private String currency;
    private BigDecimal discountPercentage;
    private BigDecimal finalPrice;
    private BigDecimal savingsAmount;
    private BigDecimal savingsPercentage;

    // Per-user pricing
    private BigDecimal pricePerUser;
    private Integer minUsers;
    private Integer maxUsers;

    // Setup fee
    private BigDecimal setupFee;

    // Validity
    private LocalDate validFrom;
    private LocalDate validUntil;
    private Boolean isActive;

    /**
     * Response containing all pricing options for a package.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageWithPricing {
        private Long packageId;
        private String packageName;
        private String packageType;
        private String description;
        private Integer trialDays;
        private Boolean isTrialAvailable;
        private List<PricingOption> pricingOptions;
        private List<String> features;
    }

    /**
     * Simplified pricing option for display.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingOption {
        private Long pricingId;
        private Long billingCycleId;
        private String billingCycleCode;
        private String billingCycleName;
        private Integer durationMonths;
        private BigDecimal basePrice;
        private BigDecimal finalPrice;
        private BigDecimal discountPercentage;
        private BigDecimal monthlyEquivalent;
        private String currency;
        private Boolean isPopular;
        private String badge;
    }

    /**
     * Price comparison across packages.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceComparison {
        private String billingCycleCode;
        private String billingCycleName;
        private List<PackagePrice> packages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackagePrice {
        private Long packageId;
        private String packageName;
        private BigDecimal price;
        private BigDecimal discountPercentage;
        private String currency;
    }
}
