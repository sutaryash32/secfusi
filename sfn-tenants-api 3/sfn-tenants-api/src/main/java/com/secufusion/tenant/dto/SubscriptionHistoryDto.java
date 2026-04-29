package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for subscription history records.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionHistoryDto {

    private Long historyId;
    private Long subscriptionId;
    private String tenantId;

    private String action;  // CREATED, UPGRADED, DOWNGRADED, CANCELLED, etc.

    // Package change
    private String fromPackageName;
    private String toPackageName;

    // Status change
    private String fromStatus;
    private String toStatus;

    // Billing cycle change
    private String fromBillingCycle;
    private String toBillingCycle;

    // Price change
    private BigDecimal fromPrice;
    private BigDecimal toPrice;
    private String currency;

    // Additional info
    private String reason;
    private String notes;

    // Audit
    private Instant changedAt;
    private String changedBy;
}
