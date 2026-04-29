package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelSubscriptionRequest {

    /**
     * Reason for cancellation.
     */
    private String reason;

    /**
     * Whether to cancel immediately or at end of billing period.
     */
    @Builder.Default
    private Boolean immediate = false;

    /**
     * Cancelled by user identifier.
     */
    private String cancelledBy;
}
