package com.secufusion.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotNull(message = "Package ID is required")
    private Long packageId;

    @NotNull(message = "Billing cycle ID is required")
    private Long billingCycleId;

    /**
     * Start with trial period. If true, subscription starts as TRIAL status.
     */
    @Builder.Default
    private Boolean startTrial = false;

    /**
     * Custom trial days (overrides package default).
     */
    private Integer customTrialDays;

    /**
     * Enable auto-renewal for the subscription.
     */
    @Builder.Default
    private Boolean autoRenew = true;

    /**
     * Notes about the subscription.
     */
    private String notes;

    /**
     * Created by user identifier.
     */
    private String createdBy;
}
