package com.secufusion.tenant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DbJwtAuthenticationManagerResolver resolver) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
//                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI & OpenAPI
                        .requestMatchers(
                                "/api/tenants/swagger-ui/**",
                                "/api/tenants/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/ws/**"
                        ).permitAll()

                        // Public APIs
                        .requestMatchers("/api/tenants/tenant-config/**").permitAll()

                        // API Key token exchange — no JWT needed, key IS the credential
                        .requestMatchers("/api/tenants/auth/token/api-key").permitAll()

                        // Token refresh & logout — must work with expired access tokens
                        .requestMatchers("/api/tenants/auth/token/refresh").permitAll()
                        .requestMatchers("/api/tenants/auth/logout").permitAll()

                        // Actuator
                        .requestMatchers("/actuator/**").permitAll()

                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.authenticationManagerResolver(resolver)
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

