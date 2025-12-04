package com.secufusion.iam;

import com.secufusion.iam.service.InitializerExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry for the IAM service.
 *
 * Boots the Spring context and triggers any required initialization
 * logic after the application bean is constructed.
 */
@SpringBootApplication
@RequiredArgsConstructor
public class IAMSeviceApplication {

    /**
     * Executor responsible for running initialization routines (e.g., default tenant setup).
     * Injected via Lombok-generated constructor.
     */
    private final InitializerExecutor initializerExecutor;

    /**
     * Application entry point.
     *
     * Delegates to SpringApplication to start the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(IAMSeviceApplication.class, args);
    }

    /**
     * Post-construction initialization hook.
     *
     * Called once the Spring container has injected dependencies into this bean.
     * Triggers the initializer executor to run application initialization tasks.
     */
    @PostConstruct
    public void initDefaultTenant() {
        initializerExecutor.runInitialization();
    }

}