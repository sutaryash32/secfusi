package com.secufusion.iam.openFeatureService.config;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class OpenFeatureBeans {

    @Autowired
    private DbFeatureProvider dbProvider;

    @Bean
    public OpenFeatureAPI openFeatureAPI() {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProvider(dbProvider);   // default provider
        return api;
    }

    @Bean
    public Client openFeatureClient(OpenFeatureAPI api) {
        return api.getClient();
    }
}

