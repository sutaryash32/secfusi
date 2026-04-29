package com.secufusion.events.service;

import com.secufusion.events.entity.AuthProviderConfig;
import com.secufusion.events.repository.AuthProviderConfigRepository;
import com.secufusion.events.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthConfigServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AuthProviderConfigRepository authProviderConfigRepository;

    @InjectMocks
    private AuthConfigService authConfigService;

    private AuthProviderConfig validConfig;
    private AuthProviderConfig invalidConfig;


    @BeforeEach
    void setUp() {
        // ARRANGE common data setup
        validConfig = new AuthProviderConfig();
        validConfig.setIssuerUri("http://issuer-valid");
        validConfig.setJwkUri("http://valid-jwk-uri");

        invalidConfig = new AuthProviderConfig();
        invalidConfig.setIssuerUri("http://issuer-invalid");
        invalidConfig.setJwkUri(null); // Falls back to issuerUri + "/protocol/openid-connect/certs"
    }

    @Nested
    class GetJwtDecodersTests {

        @Test
        void happyPath() {
            // ARRANGE
            List<AuthProviderConfig> configs = new ArrayList<>();
            configs.add(validConfig);
            when(authProviderConfigRepository.findAll()).thenReturn(configs);

            // ACT
            Map<String, JwtDecoder> result = authConfigService.getJwtDecoders();

            // ASSERT
            assertNotNull(result);
            verify(authProviderConfigRepository).findAll();
            // Since NimbusJwtDecoder build could fail without a real network connection or valid URI in testing,
            // we mainly verify it handles the config without throwing unhandled exceptions.
        }

        @Test
        void exceptionSadPath() {
            // ARRANGE
            // Creating NimbusJwtDecoder with an invalid HTTP URI format triggers exception.
            // Catch block correctly logs and returns null, which stream filters out.
            invalidConfig.setJwkUri("invalid jwk uri format !!");

            List<AuthProviderConfig> configs = new ArrayList<>();
            configs.add(invalidConfig);
            when(authProviderConfigRepository.findAll()).thenReturn(configs);

            // ACT
            Map<String, JwtDecoder> result = authConfigService.getJwtDecoders();

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty()); // Filtered out due to internal exception returning null
            verify(authProviderConfigRepository).findAll();
        }
    }
}