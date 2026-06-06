package com.vyttah.goaml.config.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS for {@code /api/**} (Phase 13), gated by {@code goaml.web.allowed-origins} (comma-separated).
 *
 * <p>In production the SPA is served same-origin from the jar, so no CORS is needed and the property is
 * left blank → the source returns no CORS config (headers absent, behaviour unchanged). It's used only
 * when a separately-hosted SPA (e.g. the Vite dev server) must call a remote backend; set the origins
 * explicitly there. {@code SecurityConfig} wires this source via {@code http.cors(...)}.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${goaml.web.allowed-origins:}") String origins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (!allowed.isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(allowed);
            cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            cfg.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cfg);
        }
        return source;
    }
}
