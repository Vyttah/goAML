package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionConfig;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.mcp.McpAccessDeniedException;
import com.vyttah.goaml.model.dto.lookup.LookupViews.JurisdictionView;
import com.vyttah.goaml.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LookupTools}: the registry + lookup service are mocked; verifies the read tools map
 * engine data correctly, that describe combines shape metadata with jurisdiction acceptance, and that the
 * tools require authentication.
 */
class LookupToolsTest {

    private final JurisdictionRegistry registry = mock(JurisdictionRegistry.class);
    private final LookupService lookupService = mock(LookupService.class);
    private final LookupTools tools = new LookupTools(registry, lookupService);

    private static final JurisdictionConfig UAE = new JurisdictionConfig(
            "ae", "United Arab Emirates", "AED",
            Set.of(ReportType.DPMSR, ReportType.STR), new BigDecimal("55000"), "ae");

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                "officer@demo.local", "", true, List.of("ANALYST"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set("tenant_demo");
    }

    @Test
    void listJurisdictionsMapsConfig() {
        authenticate();
        when(registry.codes()).thenReturn(Set.of("ae"));
        when(registry.require("ae")).thenReturn(UAE);

        List<JurisdictionView> views = tools.listJurisdictions();

        assertThat(views).hasSize(1);
        JurisdictionView view = views.get(0);
        assertThat(view.code()).isEqualTo("ae");
        assertThat(view.defaultCurrency()).isEqualTo("AED");
        assertThat(view.allowedReportTypes()).contains("DPMSR", "STR");
        assertThat(view.dpmsThreshold()).isEqualByComparingTo("55000");
    }

    @Test
    void listLookupSetsSorted() {
        authenticate();
        when(lookupService.setNames("ae")).thenReturn(Set.of("currencies", "countries"));

        assertThat(tools.listLookupSets("ae")).containsExactly("countries", "currencies");
    }

    @Test
    void listLookupsSortedAndNullSafe() {
        authenticate();
        when(lookupService.codes("ae", "currencies")).thenReturn(Set.of("USD", "AED"));
        when(lookupService.codes("ae", "missing")).thenReturn(null);

        assertThat(tools.listLookups("ae", "currencies")).containsExactly("AED", "USD");
        assertThat(tools.listLookups("ae", "missing")).isEmpty();
    }

    @Test
    void describeReportTypeCombinesMetadataAndJurisdiction() {
        authenticate();
        when(registry.find("ae")).thenReturn(Optional.of(UAE));

        LookupTools.ReportTypeDescription d = tools.describeReportType("DPMSR", "ae");

        assertThat(d.reportCode()).isEqualTo("DPMSR");
        assertThat(d.shape()).isEqualTo("ACTIVITY");
        assertThat(d.allowedInJurisdiction()).isTrue();
        assertThat(d.dpmsThreshold()).isEqualByComparingTo("55000");
    }

    @Test
    void describeReportTypeDefaultsJurisdictionToAe() {
        authenticate();
        when(registry.find("ae")).thenReturn(Optional.of(UAE));

        LookupTools.ReportTypeDescription d = tools.describeReportType("STR", null);

        assertThat(d.jurisdiction()).isEqualTo("ae");
        assertThat(d.shape()).isEqualTo("TRANSACTION");
        assertThat(d.locationReasonActionRequired()).isTrue();
        assertThat(d.dpmsThreshold()).isNull(); // only DPMSR carries the threshold
    }

    @Test
    void describeReportTypeRejectsUnknownCode() {
        authenticate();

        assertThatThrownBy(() -> tools.describeReportType("NOPE", "ae"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown report_code");
    }

    @Test
    void toolsRequireAuthentication() {
        assertThatThrownBy(tools::listJurisdictions).isInstanceOf(McpAccessDeniedException.class);
    }
}
