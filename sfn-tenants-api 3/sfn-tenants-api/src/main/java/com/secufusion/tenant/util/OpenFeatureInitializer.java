package com.secufusion.tenant.util;

import com.secufusion.tenant.service.DbFeatureFlagProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenFeatureInitializer {

    private final DbFeatureFlagProvider provider;

    public OpenFeatureInitializer(DbFeatureFlagProvider provider) {
        this.provider = provider;
    }

    @PostConstruct
    public void init() {
        log.info("⚡ Registering OpenFeature Provider: {}", provider.getMetadata().getName());
        OpenFeatureAPI.getInstance().setProvider(provider);
    }
}
