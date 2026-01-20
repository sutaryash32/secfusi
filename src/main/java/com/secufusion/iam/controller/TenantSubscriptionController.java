package com.secufusion.iam.controller;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.service.AddonPricingService;
import com.secufusion.iam.service.TenantSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Subscriptions", description = "APIs for managing tenant subscriptions, trials, and pricing")
public class TenantSubscriptionController {

    private final TenantSubscriptionService subscriptionService;
    private final AddonPricingService addonPricingService;

    // ==================== Subscription CRUD ====================

    @PostMapping
    @Operation(summary = "Create a new subscription", description = "Create a new subscription for a tenant. Can start as trial or paid.")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        log.info("Creating subscription for tenant: {}", request.getTenantId());

        SubscriptionResponse response = subscriptionService.createSubscription(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDto.<SubscriptionResponse>builder()
                        .responseCode("201")
                        .message("Subscription created successfully")
                        .data(response)
                        .build());
    }

    @PostMapping("/default/{tenantId}")
    @Operation(summary = "Create default subscription", description = "Create a default (Freemium) subscription for a new tenant")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> createDefaultSubscription(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Created by user") @RequestParam(required = false) String createdBy) {
        log.info("Creating default subscription for tenant: {}", tenantId);

        SubscriptionResponse response = subscriptionService.createDefaultSubscription(tenantId, createdBy);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDto.<SubscriptionResponse>builder()
                        .responseCode("201")
                        .message("Default subscription created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Get active subscription", description = "Get the current active subscription for a tenant")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> getActiveSubscription(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        log.debug("Getting active subscription for tenant: {}", tenantId);

        SubscriptionResponse response = subscriptionService.getActiveSubscription(tenantId);

        return ResponseEntity.ok(ResponseDto.<SubscriptionResponse>builder()
                .responseCode("200")
                .message("Success")
                .data(response)
                .build());
    }

    @GetMapping("/tenant/{tenantId}/history")
    @Operation(summary = "Get subscription history", description = "Get all subscription records for a tenant (including past)")
    public ResponseEntity<ResponseDto<List<SubscriptionResponse>>> getSubscriptionHistory(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        log.debug("Getting subscription history for tenant: {}", tenantId);

        List<SubscriptionResponse> response = subscriptionService.getSubscriptionHistory(tenantId);

        return ResponseEntity.ok(ResponseDto.<List<SubscriptionResponse>>builder()
                .responseCode("200")
                .message("Success")
                .data(response)
                .build());
    }

    // ==================== Subscription Actions ====================

    @PostMapping("/tenant/{tenantId}/upgrade")
    @Operation(summary = "Upgrade subscription", description = "Upgrade tenant's subscription to a higher package")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> upgradeSubscription(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "New package ID") @RequestParam Long packageId,
            @Parameter(description = "New billing cycle ID (optional)") @RequestParam(required = false) Long billingCycleId,
            @Parameter(description = "Updated by user") @RequestParam(required = false) String updatedBy) {
        log.info("Upgrading subscription for tenant: {} to package: {}", tenantId, packageId);

        SubscriptionResponse response = subscriptionService.upgradeSubscription(
                tenantId, packageId, billingCycleId, updatedBy);

        return ResponseEntity.ok(ResponseDto.<SubscriptionResponse>builder()
                .responseCode("200")
                .message("Subscription upgraded successfully")
                .data(response)
                .build());
    }

    @PostMapping("/tenant/{tenantId}/downgrade")
    @Operation(summary = "Downgrade subscription", description = "Downgrade tenant's subscription to a lower package")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> downgradeSubscription(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "New package ID") @RequestParam Long packageId,
            @Parameter(description = "Updated by user") @RequestParam(required = false) String updatedBy) {
        log.info("Downgrading subscription for tenant: {} to package: {}", tenantId, packageId);

        SubscriptionResponse response = subscriptionService.downgradeSubscription(
                tenantId, packageId, updatedBy);

        return ResponseEntity.ok(ResponseDto.<SubscriptionResponse>builder()
                .responseCode("200")
                .message("Subscription downgraded successfully")
                .data(response)
                .build());
    }

    @PostMapping("/tenant/{tenantId}/convert-trial")
    @Operation(summary = "Convert trial to paid", description = "Convert a trial subscription to a paid subscription")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> convertTrial(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Valid @RequestBody ConvertTrialRequest request) {
        log.info("Converting trial to paid for tenant: {}", tenantId);

        SubscriptionResponse response = subscriptionService.convertTrial(tenantId, request);

        return ResponseEntity.ok(ResponseDto.<SubscriptionResponse>builder()
                .responseCode("200")
                .message("Trial converted to paid subscription successfully")
                .data(response)
                .build());
    }

    @PostMapping("/tenant/{tenantId}/cancel")
    @Operation(summary = "Cancel subscription", description = "Cancel a tenant's subscription")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> cancelSubscription(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @RequestBody CancelSubscriptionRequest request) {
        log.info("Cancelling subscription for tenant: {}", tenantId);

        SubscriptionResponse response = subscriptionService.cancelSubscription(tenantId, request);

        return ResponseEntity.ok(ResponseDto.<SubscriptionResponse>builder()
                .responseCode("200")
                .message("Subscription cancelled successfully")
                .data(response)
                .build());
    }

    @PostMapping("/tenant/{tenantId}/renew")
    @Operation(summary = "Renew subscription", description = "Manually renew a subscription")
    public ResponseEntity<ResponseDto<SubscriptionResponse>> renewSubscription(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Updated by user") @RequestParam(required = false) String updatedBy) {
        log.info("Renewing subscription for tenant: {}", tenantId);

        SubscriptionResponse response = subscriptionService.renewSubscription(tenantId, updatedBy);

        return ResponseEntity.ok(ResponseDto.<SubscriptionResponse>builder()
                .responseCode("200")
                .message("Subscription renewed successfully")
                .data(response)
                .build());
    }

    // ==================== Pricing APIs ====================

    @GetMapping("/pricing/packages")
    @Operation(summary = "Get all packages with pricing", description = "Get all available packages with their pricing options")
    public ResponseEntity<ResponseDto<List<PackagePricingResponse.PackageWithPricing>>> getAllPackagesWithPricing() {
        log.debug("Getting all packages with pricing");

        List<PackagePricingResponse.PackageWithPricing> response = subscriptionService.getAllPackagesWithPricing();

        return ResponseEntity.ok(ResponseDto.<List<PackagePricingResponse.PackageWithPricing>>builder()
                .responseCode("200")
                .message("Success")
                .data(response)
                .build());
    }

    @GetMapping("/pricing/package/{packageId}")
    @Operation(summary = "Get package pricing", description = "Get all pricing options for a specific package")
    public ResponseEntity<ResponseDto<List<PackagePricingResponse>>> getPackagePricing(
            @Parameter(description = "Package ID") @PathVariable Long packageId) {
        log.debug("Getting pricing for package: {}", packageId);

        List<PackagePricingResponse> response = subscriptionService.getPackagePricing(packageId);

        return ResponseEntity.ok(ResponseDto.<List<PackagePricingResponse>>builder()
                .responseCode("200")
                .message("Success")
                .data(response)
                .build());
    }

    // ==================== Addon Pricing APIs ====================

    @GetMapping("/pricing/addons")
    @Operation(summary = "Get all addon features with pricing", description = "Get all available addon features with their pricing options")
    public ResponseEntity<ResponseDto<List<AddonPricingResponse.AddonWithAllPricing>>> getAllAddonFeaturesWithPricing() {
        log.debug("Getting all addon features with pricing");

        List<AddonPricingResponse.AddonWithAllPricing> response = addonPricingService.getAllAddonFeaturesWithPricing();

        return ResponseEntity.ok(ResponseDto.<List<AddonPricingResponse.AddonWithAllPricing>>builder()
                .responseCode("200")
                .message("Success")
                .data(response)
                .build());
    }

    @GetMapping("/pricing/addon/{featureCode}")
    @Operation(summary = "Get addon pricing by feature code", description = "Get all pricing options for a specific addon feature")
    public ResponseEntity<ResponseDto<AddonPricingResponse.AddonWithAllPricing>> getAddonPricingByFeatureCode(
            @Parameter(description = "Feature code") @PathVariable String featureCode) {
        log.debug("Getting pricing for addon feature: {}", featureCode);

        AddonPricingResponse.AddonWithAllPricing response = addonPricingService.getAddonPricingByFeatureCode(featureCode);

        return ResponseEntity.ok(ResponseDto.<AddonPricingResponse.AddonWithAllPricing>builder()
                .responseCode("200")
                .message("Success")
                .data(response)
                .build());
    }

    @GetMapping("/pricing/addon/{featureCode}/cycle/{billingCycleCode}")
    @Operation(summary = "Get addon pricing for specific billing cycle", description = "Get pricing for an addon feature with a specific billing cycle")
    public ResponseEntity<ResponseDto<AddonPricingResponse>> getAddonPricing(
            @Parameter(description = "Feature code") @PathVariable String featureCode,
            @Parameter(description = "Billing cycle code") @PathVariable String billingCycleCode) {
        log.debug("Getting pricing for addon {} with billing cycle {}", featureCode, billingCycleCode);

        AddonPricingResponse response = addonPricingService.getAddonPricing(featureCode, billingCycleCode);

        return ResponseEntity.ok(ResponseDto.<AddonPricingResponse>builder()
                .responseCode("200")
                .message("Success")
                .data(response)
                .build());
    }
}
