package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary of tenant's subscription information from IAM API.
 * Used in TenantResponse to display package, billing, and trial details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSummary {

    // Subscription ID
    private Long subscriptionId;

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
    private String startDate;
    private String endDate;

    // Trial info
    private Boolean isTrial;
    private String trialStartDate;
    private String trialEndDate;
    private Integer trialDays;
    private Long trialDaysRemaining;
    private Boolean trialConverted;

    // Billing info
    private BigDecimal billingAmount;
    private String currency;
    private String nextBillingDate;
    private Long daysUntilNextBilling;

    // Auto-renewal
    private Boolean autoRenew;

    // Grace period
    private Boolean isInGracePeriod;
}
