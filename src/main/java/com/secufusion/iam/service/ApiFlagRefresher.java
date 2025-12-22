package com.secufusion.iam.service;

import com.secufusion.iam.filter.JwtTenantUserValidationFilter;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class ApiFlagRefresher {

    private final JwtTenantUserValidationFilter jwtFilter;

    public ApiFlagRefresher(JwtTenantUserValidationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Scheduled(fixedDelay = 30000)
    public void refreshCache() {
        jwtFilter.refreshCache();
    }

    @PostConstruct
    public void initialLoad() {
        jwtFilter.refreshCache();
    }
}
