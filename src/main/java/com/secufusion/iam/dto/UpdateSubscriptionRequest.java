package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionRequest {

    /**
     * New package ID (for upgrade/downgrade).
     */
    private Long packageId;

    /**
     * New billing cycle ID.
     */
    private Long billingCycleId;

    /**
     * Enable/disable auto-renewal.
     */
    private Boolean autoRenew;

    /**
     * Updated notes.
     */
    private String notes;

    /**
     * Updated by user identifier.
     */
    private String updatedBy;
}
