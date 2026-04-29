package com.secufusion.iam.service;

import com.secufusion.iam.repository.ApiFlagRepository;
import com.secufusion.iam.repository.TenantApiMappingRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.PackageFeatureMappingRepository;
import com.secufusion.iam.entity.ApiFlagEntity;
import com.secufusion.iam.entity.TenantApiMappingEntity;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.PackageFeatureMapping;

import dev.openfeature.sdk.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class DbFeatureFlagProvider implements FeatureProvider {

    private final ApiFlagRepository apiRepo;
    private final TenantApiMappingRepository tenantApiRepo;
    private final TenantRepository tenantRepository;
    private final PackageFeatureMappingRepository packageFeatureMappingRepository;

    public DbFeatureFlagProvider(ApiFlagRepository apiRepo,
                                 TenantApiMappingRepository tenantApiRepo,
                                 TenantRepository tenantRepository,
                                 PackageFeatureMappingRepository packageFeatureMappingRepository) {
        this.apiRepo = apiRepo;
        this.tenantApiRepo = tenantApiRepo;
        this.tenantRepository = tenantRepository;
        this.packageFeatureMappingRepository = packageFeatureMappingRepository;
    }

    @Override
    public Metadata getMetadata() {
        return () -> "db-feature-provider";
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
            String flagKey, Boolean defaultValue, EvaluationContext ctx) {

        String tenantId = ctx.getValue("tenantId") != null
                ? ctx.getValue("tenantId").asString()
                : null;

        if (tenantId == null) {
            // tenant not available → allow
            return ProviderEvaluation.<Boolean>builder()
                    .value(true)
                    .reason("NO_TENANT_ID")
                    .build();
        }

        // 1️⃣ API must exist to be controlled
        ApiFlagEntity api = apiRepo.findByPath(flagKey).orElse(null);
        if (api == null) {
            // API not defined → allow
            return ProviderEvaluation.<Boolean>builder()
                    .value(true)
                    .reason("API_NOT_DEFINED")
                    .build();
        }

        // 2️⃣ Tenant mapping must exist to block
        TenantApiMappingEntity mapping =
                tenantApiRepo.findByApiIdAndTenantId(api.getId(), tenantId).orElse(null);

        if (mapping == null) {
            // mapping not defined → allow
            return ProviderEvaluation.<Boolean>builder()
                    .value(true)
                    .reason("NO_TENANT_MAPPING")
                    .build();
        }

        // 3️⃣ Only block if enabled=true
        boolean block = mapping.isEnabled();
        boolean allow = !block;

        return ProviderEvaluation.<Boolean>builder()
                .value(allow)
                .variant(allow ? "ALLOW" : "BLOCK")
                .reason("TENANT_MAPPING_FOUND")
                .build();
    }

    /**
     * Check if tenant has access to a feature based on their subscription package.
     * This method can be called directly or via the featureCode context parameter.
     */
    public boolean hasPackageFeatureAccess(String tenantId, String featureCode) {
        if (tenantId == null || featureCode == null) {
            return true; // Allow if no tenant or feature code specified
        }

        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty()) {
            log.debug("Tenant not found: {}", tenantId);
            return true; // Allow if tenant not found
        }

        Tenant tenant = tenantOpt.get();
        if (tenant.getSubscriptionPackage() == null) {
            log.debug("Tenant {} has no subscription package", tenantId);
            return false; // Deny if no package assigned
        }

        Long packageId = tenant.getSubscriptionPackage().getPkPackageId();
        Optional<PackageFeatureMapping> mappingOpt = packageFeatureMappingRepository
                .findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mappingOpt.isEmpty()) {
            log.debug("No package-feature mapping for package {} and feature {}", packageId, featureCode);
            return false; // Deny if no mapping exists
        }

        PackageFeatureMapping pfm = mappingOpt.get();
        if (!pfm.getIsEnabled()) {
            log.debug("Feature {} is disabled for package {}", featureCode, packageId);
            return false;
        }

        // Access granted if level value > 0 (anything except "No")
        boolean hasAccess = pfm.getAccessLevel().getLevelValue() > 0;
        log.debug("Package feature access: tenant={}, feature={}, access={}", tenantId, featureCode, hasAccess);

        return hasAccess;
    }

    /**
     * Get the access level code for a tenant's feature.
     */
    public String getPackageFeatureAccessLevel(String tenantId, String featureCode) {
        if (tenantId == null || featureCode == null) {
            return "NO";
        }

        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty() || tenantOpt.get().getSubscriptionPackage() == null) {
            return "NO";
        }

        Long packageId = tenantOpt.get().getSubscriptionPackage().getPkPackageId();
        Optional<PackageFeatureMapping> mappingOpt = packageFeatureMappingRepository
                .findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mappingOpt.isEmpty() || !mappingOpt.get().getIsEnabled()) {
            return "NO";
        }

        return mappingOpt.get().getAccessLevel().getLevelCode();
    }




    @Override
    public ProviderEvaluation<String> getStringEvaluation(
            String flagKey, String defaultValue, EvaluationContext ctx) {

        return ProviderEvaluation.<String>builder()
                .value(defaultValue)
                .reason("UNSUPPORTED")
                .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
            String flagKey, Integer defaultValue, EvaluationContext ctx) {

        return ProviderEvaluation.<Integer>builder()
                .value(defaultValue)
                .reason("UNSUPPORTED")
                .build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
            String flagKey, Double defaultValue, EvaluationContext ctx) {

        return ProviderEvaluation.<Double>builder()
                .value(defaultValue)
                .reason("UNSUPPORTED")
                .build();
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
            String flagKey, Value defaultValue, EvaluationContext ctx) {

        return ProviderEvaluation.<Value>builder()
                .value(defaultValue)
                .reason("UNSUPPORTED")
                .build();
    }
}
