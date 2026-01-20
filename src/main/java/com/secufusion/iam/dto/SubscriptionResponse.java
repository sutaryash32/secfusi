package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    private Long subscriptionId;
    private String tenantId;
    private String tenantName;

    // Package info
    private Long packageId;
    private String packageName;
    private String packageType;

    // Billing cycle info
    private Long billingCycleId;
    private String billingCycleCode;
    private String billingCycleName;
    private Integer durationMonths;

    // Status
    private String status;
    private Boolean isActive;

    // Dates
    private LocalDate startDate;
    private LocalDate endDate;

    // Trial info
    private Boolean isTrial;
    private LocalDate trialStartDate;
    private LocalDate trialEndDate;
    private Integer trialDays;
    private Long trialDaysRemaining;
    private Boolean trialConverted;

    // Pricing info
    private Long pricingId;
    private BigDecimal basePrice;
    private BigDecimal discountPercentage;
    private BigDecimal finalPrice;
    private BigDecimal billingAmount;
    private String currency;
    private BigDecimal pricePerUser;
    private BigDecimal setupFee;

    // Billing dates
    private LocalDate nextBillingDate;
    private LocalDate lastBillingDate;
    private Long daysUntilNextBilling;

    // Auto-renewal
    private Boolean autoRenew;

    // Grace period
    private Integer gracePeriodDays;
    private LocalDate graceEndDate;
    private Boolean isInGracePeriod;

    // Cancellation info
    private Instant cancelledAt;
    private String cancellationReason;

    // Upgrade/downgrade info
    private Long previousPackageId;
    private String previousPackageName;
    private Instant upgradedAt;
    private Instant downgradedAt;

    // Metadata
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
}
