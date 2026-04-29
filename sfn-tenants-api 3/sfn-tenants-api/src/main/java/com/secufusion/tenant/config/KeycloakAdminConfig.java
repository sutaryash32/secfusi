package com.secufusion.tenant.config;

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

    /** Connection timeout in seconds for Keycloak HTTP calls. Default: 10s. */
    @Value("${keycloak.admin.connection-timeout:10}")
    private int connectionTimeout;

    /** Socket/read timeout in seconds for Keycloak HTTP calls. Default: 30s. */
    @Value("${keycloak.admin.socket-timeout:30}")
    private int socketTimeout;

    private Keycloak keycloak;

    @Bean
    public Keycloak keycloakAdminClient() {
        // Set HTTP client timeouts via system properties.
        // These are picked up by the underlying JAX-RS client used by the Keycloak admin client.
        System.setProperty("org.jboss.resteasy.connect.timeout", String.valueOf(connectionTimeout * 1000));
        System.setProperty("org.jboss.resteasy.read.timeout", String.valueOf(socketTimeout * 1000));

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
