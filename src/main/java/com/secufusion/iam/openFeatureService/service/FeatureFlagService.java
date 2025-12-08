package com.secufusion.iam.openFeatureService.service;

import dev.openfeature.sdk.Client;
import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService {

    private final Client client;

    public FeatureFlagService(Client client) {
        this.client = client;
    }

    public boolean isApiEnabled(String flagKey) {
        return client.getBooleanValue(flagKey, true, null);  // No context
    }
}
