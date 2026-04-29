package com.secufusion.iam.config;

import jakarta.annotation.PreDestroy;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String masterRealm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.username}")
    private String username;

    @Value("${keycloak.admin.password}")
    private String password;

    private Keycloak keycloak;

    @Bean
    public Keycloak keycloakAdminClient() {
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(masterRealm)
                .clientId(clientId)
                .username(username)
                .password(password)
                .grantType("password")
                .build();
        return this.keycloak;
    }

    @PreDestroy
    public void cleanup() {
        if (this.keycloak != null) {
            this.keycloak.close();
        }
    }
}
