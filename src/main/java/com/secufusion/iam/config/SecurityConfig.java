package com.secufusion.iam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DbJwtAuthenticationManagerResolver resolver) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))  // <-- ENABLE CORS HERE
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/iam/public/**").permitAll()
                        .requestMatchers("/tenant-config/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**","/login/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolver)  // Inject the resolver bean
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));         // Allow all origins
        config.setAllowedMethods(List.of("*"));         // Allow GET, POST, PUT, DELETE, OPTIONS
        config.setAllowedHeaders(List.of("*"));         // Allow all headers
        config.setAllowCredentials(false);              // Avoid credential leak when "*"

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // Apply to all paths

        return source;
    }
//    @Bean
//    public AuthenticationEntryPoint customJwtEntryPoint() {
//        return new AuthenticationEntryPoint() {
//            @Override
//            public void commence(HttpServletRequest request, HttpServletResponse response,
//                                 AuthenticationException authException) throws IOException {
//
//                Throwable cause = authException.getCause();
//                String message = "Invalid token";
//
//                if (cause instanceof JwtValidationException jwtEx) {
//                    if (jwtEx.getErrors().stream().anyMatch(e -> e.getDescription().contains("expired"))) {
//                        message = "TOKEN_EXPIRED";
//                    }
//                }
//
//                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                response.setContentType("application/json");
//                new ObjectMapper().writeValue(response.getWriter(),
//                        Map.of("error", message, "status", 401));
//            }
//        };
//    }
//
//    @Bean
//    public JwtAuthenticationConverter jwtAuthenticationConverter() {
//        JwtGrantedAuthoritiesConverter realmConverter = new JwtGrantedAuthoritiesConverter();
//        realmConverter.setAuthoritiesClaimName("realm_access.roles");
//        realmConverter.setAuthorityPrefix("ROLE_");
//
//        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
//        authenticationConverter.setJwtGrantedAuthoritiesConverter(realmConverter);
//
//        return authenticationConverter;
//    }
}
