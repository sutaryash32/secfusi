package com.secufusion.iam.service;

import com.secufusion.iam.repository.ApiFlagRepository;
import com.secufusion.iam.repository.TenantApiMappingRepository;
import com.secufusion.iam.entity.ApiFlagEntity;
import com.secufusion.iam.entity.TenantApiMappingEntity;

import dev.openfeature.sdk.*;
import org.springframework.stereotype.Component;

@Component
public class DbFeatureFlagProvider implements FeatureProvider {

    private final ApiFlagRepository apiRepo;
    private final TenantApiMappingRepository tenantApiRepo;

    public DbFeatureFlagProvider(ApiFlagRepository apiRepo,
                                 TenantApiMappingRepository tenantApiRepo) {
        this.apiRepo = apiRepo;
        this.tenantApiRepo = tenantApiRepo;
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
