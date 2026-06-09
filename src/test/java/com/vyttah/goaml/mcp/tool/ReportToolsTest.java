package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.mcp.McpAccessDeniedException;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.report.ReportResponses.CreateReportResponse;
import com.vyttah.goaml.model.dto.report.ReportResponses.ReportView;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.report.ReportPreview;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.report.ReportValidationResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportTools}: the {@link ReportService} is mocked, a real bean {@link Validator}
 * runs. Verifies delegation, the bean-validation short-circuit (constraint violations returned as messages,
 * service not called), and RBAC enforcement via the thread's SecurityContext.
 */
class ReportToolsTest {

    private static ValidatorFactory validatorFactory;

    private final ReportService reportService = mock(ReportService.class);
    private final ReportTools tools = new ReportTools(reportService, validatorFactory.getValidator());

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticate(List<String> roles) {
        UserPrincipal principal = new UserPrincipal(USER_ID, TENANT_ID, "officer@demo.local", "", true, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set("tenant_demo");
    }

    @Test
    void validateDelegatesToServiceForAValidRequest() {
        authenticate(List.of("ANALYST"));
        when(reportService.validate(any(), eq(TENANT_ID)))
                .thenReturn(new ReportValidationResult("VALID", List.of()));

        ReportValidationResult result = tools.validateDpmsr(minimalRequest("V-1", new BigDecimal("90000")));

        assertThat(result.status()).isEqualTo("VALID");
        verify(reportService).validate(any(), eq(TENANT_ID));
    }

    @Test
    void validateShortCircuitsOnConstraintViolations() {
        authenticate(List.of("ANALYST"));

        // entity_reference is @NotBlank — a blank one is a constraint violation, caught before the engine.
        ReportValidationResult result = tools.validateDpmsr(minimalRequest("   ", new BigDecimal("90000")));

        assertThat(result.status()).isEqualTo("INVALID");
        assertThat(result.messages()).anyMatch(m -> m.code().equals("CONSTRAINT"));
        verify(reportService, never()).validate(any(), any());
    }

    @Test
    void validateDeniedWithoutAnalystOrMlro() {
        authenticate(List.of("TENANT_ADMIN"));

        assertThatThrownBy(() -> tools.validateDpmsr(minimalRequest("V-2", new BigDecimal("90000"))))
                .isInstanceOf(McpAccessDeniedException.class);
        verify(reportService, never()).validate(any(), any());
    }

    @Test
    void previewDelegatesToService() {
        authenticate(List.of("MLRO"));
        when(reportService.previewXml(any(), eq(TENANT_ID)))
                .thenReturn(new ReportPreview("VALID", "<report/>", List.of()));

        ReportPreview result = tools.previewDpmsrXml(minimalRequest("P-1", new BigDecimal("90000")));

        assertThat(result.xml()).isEqualTo("<report/>");
        verify(reportService).previewXml(any(), eq(TENANT_ID));
    }

    @Test
    void previewShortCircuitsOnConstraintViolations() {
        authenticate(List.of("ANALYST"));

        ReportPreview result = tools.previewDpmsrXml(minimalRequest(" ", new BigDecimal("90000")));

        assertThat(result.status()).isEqualTo("INVALID");
        assertThat(result.xml()).isNull();
        verify(reportService, never()).previewXml(any(), any());
    }

    @Test
    void createDelegatesToServiceAndReturnsId() {
        authenticate(List.of("MLRO"));
        UUID reportId = UUID.randomUUID();
        when(reportService.create(any(DpmsrCreateRequest.class),eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new ReportResult(reportId, "VALID", List.of()));

        CreateReportResponse result = tools.createDpmsr(minimalRequest("C-1", new BigDecimal("90000")));

        assertThat(result.reportId()).isEqualTo(reportId);
        assertThat(result.status()).isEqualTo("VALID");
        verify(reportService).create(any(DpmsrCreateRequest.class),eq(TENANT_ID), eq(USER_ID));
    }

    @Test
    void createShortCircuitsOnConstraintViolations() {
        authenticate(List.of("ANALYST"));

        CreateReportResponse result = tools.createDpmsr(minimalRequest("", new BigDecimal("90000")));

        assertThat(result.reportId()).isNull();
        assertThat(result.status()).isEqualTo("INVALID");
        verify(reportService, never()).create(any(DpmsrCreateRequest.class),any(), any());
    }

    @Test
    void listReportsMapsEntities() {
        authenticate(List.of("TENANT_ADMIN"));
        Report report = new Report(UUID.randomUUID(), "PAY-1", "DPMSR", 3177, "VALID", "{}", USER_ID);
        when(reportService.list()).thenReturn(List.of(report));

        List<ReportView> views = tools.listReports();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).entityReference()).isEqualTo("PAY-1");
        assertThat(views.get(0).status()).isEqualTo("VALID");
    }

    @Test
    void getReportMapsEntity() {
        authenticate(List.of("ANALYST"));
        UUID id = UUID.randomUUID();
        Report report = new Report(id, "PAY-9", "DPMSR", 3177, "VALID", "{}", USER_ID);
        when(reportService.get(id)).thenReturn(report);

        ReportView view = tools.getReport(id.toString());

        assertThat(view.entityReference()).isEqualTo("PAY-9");
    }

    @Test
    void getReportRejectsNonUuid() {
        authenticate(List.of("ANALYST"));

        assertThatThrownBy(() -> tools.getReport("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void readToolsDeniedWithoutAnyReportRole() {
        authenticate(List.of("SUPER_ADMIN"));

        assertThatThrownBy(tools::listReports).isInstanceOf(McpAccessDeniedException.class);
    }

    // ---------- fixture (mirrors the service test) ----------

    private static DpmsrCreateRequest minimalRequest(String ref, BigDecimal value) {
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                "Minimal Trading FZE", null, "123456", null, "AE", null, null);
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party(
                "Seller of gold above AED 55,000", null, entity, null);
        DpmsrCreateRequest.Goods gold = new DpmsrCreateRequest.Goods(
                "GOLD", null, "1kg gold bar", null, null, value, "AED", null, null, null, null, null, null, null);
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                null, "Sara", "Khan", null, null, null, null, null, null, null, null, null, null);
        return new DpmsrCreateRequest(null, ref, odt(), null,
                "DPMS threshold met", "Filed", List.of("DPMSJ"), mlro, null, List.of(party), List.of(gold));
    }

    private static OffsetDateTime odt() {
        return OffsetDateTime.parse("2026-06-02T12:00:00Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
