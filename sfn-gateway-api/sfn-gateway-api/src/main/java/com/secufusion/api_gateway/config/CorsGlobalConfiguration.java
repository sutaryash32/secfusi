//package com.secufusion.api_gateway.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.reactive.CorsWebFilter;
//import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
//
//import java.util.List;
//
//@Configuration
//public class CorsGlobalConfiguration {
//
//    @Bean
//    public CorsWebFilter corsWebFilter() {
//
//        CorsConfiguration config = new CorsConfiguration();
//
//        // ✅ Allow ALL
//        config.setAllowedOriginPatterns(List.of("*"));
//        config.setAllowedMethods(List.of("*"));
//        config.setAllowedHeaders(List.of("*"));
//        config.setExposedHeaders(List.of("*"));
//
//        // ⚠️ Must be false when using "*"
//        config.setAllowCredentials(false);
//
//        UrlBasedCorsConfigurationSource source =
//                new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//
//        return new CorsWebFilter(source);
//    }
//}
