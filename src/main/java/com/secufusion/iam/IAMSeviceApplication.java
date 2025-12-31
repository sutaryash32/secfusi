package com.secufusion.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry for the IAM service.
 *
 * Boots the Spring context and triggers any required initialization
 * logic after the application bean is constructed.
 */
@SpringBootApplication
public class IAMSeviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IAMSeviceApplication.class, args);
    }

}