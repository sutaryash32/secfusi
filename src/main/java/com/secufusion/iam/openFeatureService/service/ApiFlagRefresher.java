package com.secufusion.iam.openFeatureService.service;

import com.secufusion.iam.openFeatureService.config.FeatureFlagFilter;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class ApiFlagRefresher {

    private final FeatureFlagFilter featureFlagFilter;

    public ApiFlagRefresher(FeatureFlagFilter featureFlagFilter) {
        this.featureFlagFilter = featureFlagFilter;
    }

    @Scheduled(fixedDelay = 30000)
    public void refreshCache() {
        featureFlagFilter.refreshCache();
    }

    @PostConstruct
    public void initialLoad() {
        featureFlagFilter.refreshCache();
    }
}

