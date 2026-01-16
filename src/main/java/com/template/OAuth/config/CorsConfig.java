package com.template.OAuth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;

import java.util.List;
import java.util.Objects;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        CorsConfiguration cfg = new CorsConfiguration();

        // Allow credentials requires specific origins (not "*")
        cfg.setAllowCredentials(true);
        cfg.setAllowedOrigins(appProperties.getCors().getAllowedOrigins()); // from application*.yaml

        // Methods/headers
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        cfg.addAllowedHeader(CorsConfiguration.ALL);

        // Cache preflight responses
        cfg.setMaxAge(Objects.requireNonNull(Duration.ofHours(1)));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
