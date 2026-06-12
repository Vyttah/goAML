package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * C1 — verifies the signed service assertion for every {@code /api/v1/integration/**} request <em>before</em>
 * controller dispatch, so a future integration endpoint cannot ship unauthenticated by forgetting to call
 * {@link ServiceCredentialValidator#verify}. The integration paths are {@code permitAll()} for the user-JWT
 * security chain (they carry no user token); this filter is their authentication.
 *
 * <p>On success the {@link VerifiedServiceAssertion} is stashed as a request attribute
 * ({@link #VERIFIED_ASSERTION_ATTR}) and the controllers read it from there — they do <strong>not</strong>
 * re-verify, because B10 makes an assertion single-use (a second verify of the same {@code jti} would be
 * rejected as a replay). On any failure the request is rejected with a {@code 401} and never reaches a
 * controller.
 *
 * <p>The {@link SourceSystem} is derived from the path prefix: {@code /accounting/**} → ACCOUNTING; everything
 * else under the integration root ({@code /screening/**}, {@code /lookups/**}) → SCREENING. {@code /api/v1/auth/**}
 * is outside this filter's scope (handled by {@code shouldNotFilter}).
 */
@RequiredArgsConstructor
@Component
public class IntegrationAuthFilter extends OncePerRequestFilter {

    /**
     * Request attribute holding the {@link VerifiedServiceAssertion} for the integration controllers.
     * Must stay a compile-time constant (string literal) so the controllers can reference it from a
     * {@code @RequestAttribute(...)} annotation, whose element value must be a constant expression.
     */
    public static final String VERIFIED_ASSERTION_ATTR =
            "com.vyttah.goaml.security.IntegrationAuthFilter.verifiedAssertion";

    private static final String ROOT = "/api/v1/integration/";
    private static final String HEADER = "X-Service-Assertion";

    private final ServiceCredentialValidator credentialValidator;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathWithinApplication(request).startsWith(ROOT);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        SourceSystem source = sourceFor(pathWithinApplication(request));
        String assertion = request.getHeader(HEADER);
        try {
            VerifiedServiceAssertion verified = credentialValidator.verify(source, assertion);
            request.setAttribute(VERIFIED_ASSERTION_ATTR, verified);
        } catch (ServiceCredentialException ex) {
            reject(response, ex.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The request path relative to the application root. We do NOT use {@link HttpServletRequest#getServletPath()}:
     * under Spring Boot 3 / Servlet 6 with the DispatcherServlet mapped to {@code /}, the servlet path is empty
     * (the whole path lives in the path-info / request URI) — so matching on it silently skips the filter for
     * every integration request. The request URI minus the context path is stable across MockMvc and a real
     * container.
     */
    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    /** Accounting push is ACCOUNTING-signed; the screening push + lookup passthrough are SCREENING-signed. */
    private static SourceSystem sourceFor(String path) {
        if (path.startsWith(ROOT + "accounting")) {
            return SourceSystem.ACCOUNTING;
        }
        return SourceSystem.SCREENING;
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = message == null ? "Unauthorized" : message.replace("\"", "'");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + safe + "\"}");
    }
}
