package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * C1 — {@link IntegrationAuthFilter} verifies the service assertion before dispatch for every
 * {@code /api/v1/integration/**} request, rejects (401) on failure, stashes the verified principal on
 * success, and leaves non-integration paths (incl. {@code /api/v1/auth/**}) entirely alone.
 */
class IntegrationAuthFilterTest {

    private final ServiceCredentialValidator validator = mock(ServiceCredentialValidator.class);
    private final IntegrationAuthFilter filter = new IntegrationAuthFilter(validator);

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        // The filter matches on the request URI (minus context path), not the servlet path: under Spring Boot 3
        // the DispatcherServlet is mapped to "/" so getServletPath() is empty for these requests. Set both so the
        // mock mirrors a real container.
        req.setServletPath(path);
        req.setRequestURI(path);
        return req;
    }

    @Test
    void doesNotFilterNonIntegrationPaths() {
        // shouldNotFilter is the public contract OncePerRequestFilter uses to skip work.
        assertThat(invokeShouldNotFilter("/api/v1/auth/login")).isTrue();
        assertThat(invokeShouldNotFilter("/api/v1/auth/federated/token")).isTrue();
        assertThat(invokeShouldNotFilter("/api/v1/reports")).isTrue();
        assertThat(invokeShouldNotFilter("/actuator/prometheus")).isTrue();
        assertThat(invokeShouldNotFilter("/api/v1/integration/screening/subjects")).isFalse();
    }

    @Test
    void rejectsIntegrationRequestWithNoAssertion() throws Exception {
        when(validator.verify(eq(SourceSystem.SCREENING), any()))
                .thenThrow(new ServiceCredentialException("Missing service assertion"));
        MockHttpServletRequest req = request("/api/v1/integration/screening/subjects");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unauthorized");
        verifyNoInteractions(chain);   // never reaches the controller
    }

    @Test
    void verifiesAndStashesAssertionThenContinues() throws Exception {
        VerifiedServiceAssertion verified = mock(VerifiedServiceAssertion.class);
        when(validator.verify(eq(SourceSystem.SCREENING), any())).thenReturn(verified);
        MockHttpServletRequest req = request("/api/v1/integration/screening/subjects");
        req.addHeader("X-Service-Assertion", "signed-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);                 // reaches the controller
        assertThat(req.getAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR)).isSameAs(verified);
    }

    @Test
    void derivesAccountingSourceForAccountingPath() throws Exception {
        when(validator.verify(eq(SourceSystem.ACCOUNTING), any())).thenReturn(mock(VerifiedServiceAssertion.class));
        MockHttpServletRequest req = request("/api/v1/integration/accounting/transactions");
        req.addHeader("X-Service-Assertion", "signed-token");

        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(validator).verify(eq(SourceSystem.ACCOUNTING), any());
        verify(validator, never()).verify(eq(SourceSystem.SCREENING), any());
    }

    /** Drive shouldNotFilter via the OncePerRequestFilter package-visible contract. */
    private boolean invokeShouldNotFilter(String path) {
        try {
            var m = OncePerRequestFilter.class.getDeclaredMethod("shouldNotFilter", jakarta.servlet.http.HttpServletRequest.class);
            m.setAccessible(true);
            return (boolean) m.invoke(filter, request(path));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
