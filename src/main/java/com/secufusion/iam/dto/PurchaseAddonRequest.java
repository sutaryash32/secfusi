package com.secufusion.iam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to purchase an addon feature for a tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAddonRequest {

    @NotNull(message = "Feature ID or feature code is required")
    private Long featureId;

    private String featureCode;

    @NotNull(message = "Billing cycle ID is required")
    private Long billingCycleId;

    /**
     * Start with a trial period if available.
     */
    private Boolean startTrial = false;

    /**
     * Access level for the addon (optional, defaults to FULL).
     */
    private Long accessLevelId;

    /**
     * Retention period for data features (optional).
     */
    private Long retentionPeriodId;

    /**
     * Payment method ID for billing (external reference).
     */
    private String paymentMethodId;
}
