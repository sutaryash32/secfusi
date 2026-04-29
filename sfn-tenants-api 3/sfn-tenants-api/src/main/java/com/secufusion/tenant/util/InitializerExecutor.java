package com.secufusion.tenant.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class InitializerExecutor {

    private final DefaultTenantInitializer initializer;
    private final AtomicBoolean initializationStarted = new AtomicBoolean(false);

    @Async("tenantProvisioningExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!initializationStarted.compareAndSet(false, true)) {
            log.debug("Default tenant initialization already started, skipping duplicate trigger.");
            return;
        }
        runInitialization();
    }

    public void runInitialization() {
        initializer.initialize();
    }
}


