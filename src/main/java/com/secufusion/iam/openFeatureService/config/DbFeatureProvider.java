package com.secufusion.iam.openFeatureService.config;

import com.secufusion.iam.openFeatureService.repository.FeatureFlagsRepository;
import com.secufusion.iam.openFeatureService.entity.FeatureFlags;
import dev.openfeature.sdk.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DbFeatureProvider implements FeatureProvider {

    @Autowired
    private FeatureFlagsRepository repository;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    @Override
    public Metadata getMetadata() {
        return () -> "DbFeatureFlagProvider";
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) {
        // optional: eager warmup
        refreshCache();
    }

    @Override
    public void shutdown() {
        cache.clear();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
            String key,
            Boolean defaultValue,
            EvaluationContext ctx
    ) {
        Boolean cached = cache.get(key);
        if (cached != null) {
            return ProviderEvaluation.<Boolean>builder().value(cached).build();
        }

        Optional<FeatureFlags> entityOpt = repository.findByFlagKey(key);
        boolean value = entityOpt.map(FeatureFlags::isEnabled).orElse(defaultValue);
        cache.put(key, value);

        return ProviderEvaluation.<Boolean>builder().value(value).build();
    }


    @Override
    public ProviderEvaluation<String> getStringEvaluation(
            String key,
            String defaultValue,
            EvaluationContext ctx
    ) {
        // Fetch string flag value from DB or return default
        // For now, use default wrapped in ProviderEvaluation
        return ProviderEvaluation.<String>builder()
                .value(defaultValue)
                .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
            String key,
            Integer defaultValue,
            EvaluationContext ctx
    ) {
        // Fetch integer flag value from DB or return default
        return ProviderEvaluation.<Integer>builder()
                .value(defaultValue)
                .build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
            String key,
            Double defaultValue,
            EvaluationContext ctx
    ) {
        // Fetch double flag value from DB or return default
        return ProviderEvaluation.<Double>builder()
                .value(defaultValue)
                .build();
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
            String key,
            Value defaultValue,
            EvaluationContext ctx
    ) {
        // Fetch JSON/object flag value from DB or return default
        return ProviderEvaluation.<Value>builder()
                .value(defaultValue)
                .build();
    }

    @Scheduled(fixedDelay = 30000)
    public void refreshCache() {
        Map<String, Boolean> newCache = new ConcurrentHashMap<>();
        for (FeatureFlags f : repository.findAll()) {
            newCache.put(f.getFlagKey(), f.isEnabled());
        }
        cache.clear();
        cache.putAll(newCache);
    }

}
