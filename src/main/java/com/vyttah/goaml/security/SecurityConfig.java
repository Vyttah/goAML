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
    private final IntegrationAuthFilter integrationAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // D2: liveness/readiness/info stay public for k8s probes + ops; the metrics scrape
                        // (/actuator/prometheus) must NOT be public — it leaks internal cardinality + traffic
                        // shape to the internet via the single `/` ingress. Require auth; Prometheus scrapes
                        // it in-cluster (NetworkPolicy / sidecar credential), never over the public ingress.
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/api/v1/auth/**",
                                // Phase 1.5b/1.5c: server-to-server integration push (accounting/screening).
                                // Authenticated by the signed service assertion (X-Service-Assertion),
                                // verified by IntegrationAuthFilter BEFORE controller dispatch (C1) — not a
                                // user JWT — so it is permitted in the user-JWT chain (like /api/v1/auth/**).
                                "/api/v1/integration/**"
                        ).permitAll()
                        // D2: metrics behind authentication (in-cluster scrape only).
                        .requestMatchers("/actuator/prometheus").authenticated()
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
                // C1: the integration assertion filter runs before the user-JWT filter. It only acts on
                // /api/v1/integration/** (shouldNotFilter elsewhere) and rejects (401) any such request whose
                // signed assertion fails to verify — before the controller is ever reached.
                .addFilterBefore(integrationAuthFilter, UsernamePasswordAuthenticationFilter.class)
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

    /**
     * Same double-registration guard as {@link #jwtAuthFilterRegistration} but for {@link IntegrationAuthFilter}
     * (also a {@code @Component}) — it must run only inside the Spring Security chain, not as an auto-registered
     * servlet filter.
     */
    @Bean
    public FilterRegistrationBean<IntegrationAuthFilter> integrationAuthFilterRegistration(
            IntegrationAuthFilter filter) {
        FilterRegistrationBean<IntegrationAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
