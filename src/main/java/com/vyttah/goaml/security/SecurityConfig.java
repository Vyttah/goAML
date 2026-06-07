package com.vyttah.goaml.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import lombok.RequiredArgsConstructor;

/**
 * Stateless JWT security for the platform.
 *
 * <ul>
 *   <li>CSRF disabled — JWT API, no cookies.</li>
 *   <li>Session policy STATELESS.</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}, {@code /actuator/prometheus},
 *       {@code /api/v1/auth/**} are open; everything else requires authentication.</li>
 *   <li>Method-level RBAC is enabled — controllers use {@code @PreAuthorize}.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/api/v1/auth/**"
                        ).permitAll()
                        // Phase 12: the MCP transport (SSE stream + JSON-RPC message endpoint) is
                        // authenticated like the REST API. With base-url=/api/v1 the endpoints land at
                        // /api/v1/sse + /api/v1/mcp/message (already covered by /api/** below); these
                        // explicit matchers are a safety net so the transport is never accidentally public
                        // if the base-url prefix is removed/changed. JwtAuthFilter sets TenantContext + the
                        // principal on the same servlet thread that runs the tool → MCP inherits tenant+RBAC.
                        .requestMatchers("/sse", "/mcp/**").authenticated()
                        // The REST API requires authentication; method-level @PreAuthorize enforces RBAC.
                        .requestMatchers("/api/**").authenticated()
                        // Everything else — the React SPA shell + its static assets (served from
                        // classpath:/static/ via SpaWebConfig) — is public: the app bootstraps, then
                        // authenticates against /api/v1/auth/login.
                        .anyRequest().permitAll())
                // For a JSON API, missing/invalid credentials → 401, not 403 (Spring's default).
                // AccessDeniedException (authenticated but insufficient role) still yields 403.
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * Prevents Spring Boot from auto-registering {@link JwtAuthFilter} as a servlet-level
     * filter. Without this, the filter runs twice per request (once outside Spring Security
     * and once via {@code addFilterBefore} inside the chain), and Spring Security's
     * {@code SecurityContextHolderFilter} blanks the auth set during the servlet-level pass.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
