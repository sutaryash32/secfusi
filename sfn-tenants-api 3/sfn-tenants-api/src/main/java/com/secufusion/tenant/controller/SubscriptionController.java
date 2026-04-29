package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.ResponseDto;
import com.secufusion.tenant.dto.SubscriptionHistoryDto;
import com.secufusion.tenant.dto.SubscriptionSummary;
import com.secufusion.tenant.entity.SubscriptionHistory;
import com.secufusion.tenant.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for subscription management.
 * Provides endpoints for subscription queries and history.
 */
@RestController
@RequestMapping("/api/tenants/subscriptions")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "APIs for subscription management and history")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Get active subscription for a tenant.
     */
    @GetMapping("/{tenantId}")
    @Operation(
            summary = "Get active subscription",
            description = "Returns the active subscription for a tenant including package, billing cycle, and status info.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Subscription found",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SubscriptionSummary.class))),
                    @ApiResponse(responseCode = "404", description = "No active subscription found")
            }
    )
    public ResponseEntity<ResponseDto<SubscriptionSummary>> getActiveSubscription(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable String tenantId
    ) {
        log.info("Getting active subscription for tenantId={}", tenantId);
        return subscriptionService.getActiveSubscription(tenantId)
                .map(sub -> ResponseEntity.ok(new ResponseDto<>(sub, "200")))
                .orElse(ResponseEntity.ok(new ResponseDto<>(null, "404", "No active subscription found")));
    }

    /**
     * Get subscription change history for a tenant.
     */
    @GetMapping("/{tenantId}/history")
    @Operation(
            summary = "Get subscription history",
            description = "Returns the complete subscription change history for a tenant including upgrades, downgrades, cancellations, etc.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "History retrieved",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = SubscriptionHistoryDto.class))))
            }
    )
    public ResponseEntity<ResponseDto<List<SubscriptionHistoryDto>>> getSubscriptionHistory(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable String tenantId
    ) {
        log.info("Getting subscription history for tenantId={}", tenantId);
        List<SubscriptionHistoryDto> history = subscriptionService.getSubscriptionChangeHistory(tenantId);
        return ResponseEntity.ok(new ResponseDto<>(history, "200"));
    }

    /**
     * Get subscription history filtered by action type.
     */
    @GetMapping("/{tenantId}/history/filter")
    @Operation(
            summary = "Get subscription history by action",
            description = "Returns subscription history filtered by action type (CREATED, UPGRADED, DOWNGRADED, CANCELLED, etc.)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Filtered history retrieved",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = SubscriptionHistoryDto.class))))
            }
    )
    public ResponseEntity<ResponseDto<List<SubscriptionHistoryDto>>> getSubscriptionHistoryByAction(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable String tenantId,
            @Parameter(description = "Action type to filter by", required = true,
                    schema = @Schema(allowableValues = {"CREATED", "UPGRADED", "DOWNGRADED", "CANCELLED",
                            "RENEWED", "TRIAL_STARTED", "TRIAL_CONVERTED", "TRIAL_EXPIRED", "SUSPENDED", "REACTIVATED"}))
            @RequestParam String action
    ) {
        log.info("Getting subscription history for tenantId={}, action={}", tenantId, action);
        try {
            SubscriptionHistory.Action actionEnum = SubscriptionHistory.Action.valueOf(action.toUpperCase());
            List<SubscriptionHistoryDto> history = subscriptionService.getSubscriptionHistoryByAction(tenantId, actionEnum);
            return ResponseEntity.ok(new ResponseDto<>(history, "200"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new ResponseDto<>(null, "400", "Invalid action type: " + action));
        }
    }

    /**
     * Upgrade subscription to a new package.
     */
    @PutMapping("/{tenantId}/upgrade")
    @Operation(
            summary = "Upgrade subscription",
            description = "Upgrades the tenant's subscription to a new package.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Subscription upgraded",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SubscriptionSummary.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "404", description = "Tenant or package not found")
            }
    )
    public ResponseEntity<ResponseDto<SubscriptionSummary>> upgradeSubscription(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable String tenantId,
            @Parameter(description = "New package ID", required = true)
            @RequestParam Long packageId,
            @Parameter(description = "Billing cycle ID (optional)")
            @RequestParam(required = false) Long billingCycleId,
            @Parameter(description = "User performing the upgrade")
            @RequestParam(required = false) String updatedBy
    ) {
        log.info("Upgrading subscription for tenantId={} to packageId={}", tenantId, packageId);
        try {
            SubscriptionSummary upgraded = subscriptionService.upgradeSubscription(tenantId, packageId, billingCycleId, updatedBy);
            return ResponseEntity.ok(new ResponseDto<>(upgraded, "200"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new ResponseDto<>(null, "400", e.getMessage()));
        }
    }

    /**
     * Convert trial subscription to paid.
     */
    @PutMapping("/{tenantId}/convert-trial")
    @Operation(
            summary = "Convert trial to paid",
            description = "Converts a trial subscription to a paid subscription.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Trial converted",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SubscriptionSummary.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request or no trial found")
            }
    )
    public ResponseEntity<ResponseDto<SubscriptionSummary>> convertTrial(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable String tenantId,
            @Parameter(description = "Billing cycle ID for the paid subscription", required = true)
            @RequestParam Long billingCycleId,
            @Parameter(description = "User performing the conversion")
            @RequestParam(required = false) String updatedBy
    ) {
        log.info("Converting trial for tenantId={}", tenantId);
        try {
            SubscriptionSummary converted = subscriptionService.convertTrial(tenantId, billingCycleId, updatedBy);
            return ResponseEntity.ok(new ResponseDto<>(converted, "200"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new ResponseDto<>(null, "400", e.getMessage()));
        }
    }

    /**
     * Cancel subscription.
     */
    @PutMapping("/{tenantId}/cancel")
    @Operation(
            summary = "Cancel subscription",
            description = "Cancels the tenant's subscription. Can be immediate or at end of billing period.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Subscription cancelled",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SubscriptionSummary.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "404", description = "No active subscription found")
            }
    )
    public ResponseEntity<ResponseDto<SubscriptionSummary>> cancelSubscription(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable String tenantId,
            @Parameter(description = "Cancellation reason")
            @RequestParam(required = false) String reason,
            @Parameter(description = "If true, cancel immediately; if false, cancel at end of billing period")
            @RequestParam(required = false, defaultValue = "false") Boolean immediate,
            @Parameter(description = "User performing the cancellation")
            @RequestParam(required = false) String cancelledBy
    ) {
        log.info("Cancelling subscription for tenantId={}, immediate={}", tenantId, immediate);
        try {
            SubscriptionSummary cancelled = subscriptionService.cancelSubscription(tenantId, reason, immediate, cancelledBy);
            return ResponseEntity.ok(new ResponseDto<>(cancelled, "200"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new ResponseDto<>(null, "400", e.getMessage()));
        }
    }
}
