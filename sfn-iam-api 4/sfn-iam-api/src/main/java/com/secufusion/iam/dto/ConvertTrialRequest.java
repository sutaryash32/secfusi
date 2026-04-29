package com.secufusion.iam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertTrialRequest {

    /**
     * Billing cycle ID for the paid subscription.
     */
    @NotNull(message = "Billing cycle ID is required")
    private Long billingCycleId;

    /**
     * Enable auto-renewal for the converted subscription.
     */
    @Builder.Default
    private Boolean autoRenew = true;

    /**
     * Notes about the conversion.
     */
    private String notes;

    /**
     * Updated by user identifier.
     */
    private String updatedBy;
}
