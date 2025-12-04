package com.secufusion.iam.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InitializerExecutor {

    private final DefaultTenantInitializer initializer;

    @Transactional
    public void runInitialization() {
        initializer.initialize();
    }
}


