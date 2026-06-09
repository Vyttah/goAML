package com.vyttah.goaml.service.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vyttah.goaml.engine.build.ActivityReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportBuilder;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.validation.ReportValidator;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.mapper.report.DpmsrRequestMapper;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
 * Unit tests for {@link DefaultReportService}: repos + audit mocked, the real engine (mapper + builder +
 * validator + XSD) runs so create→validate→persist is exercised end-to-end without a DB.
 */
class DefaultReportServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final TenantGoamlConfigRepository configRepository = mock(TenantGoamlConfigRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final DpmsrReportBuilder builder = new DpmsrReportBuilder(
            new ActivityReportBuilder(),
            new ReportValidator(new JurisdictionRegistry(), new LookupService()),
            new XsdSchemaValidator(),
            new ReportMarshaller());

    private final DefaultReportService service = new DefaultReportService(
            new DpmsrRequestMapper(), builder, reportRepository, configRepository, auditService, objectMapper);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private void stubConfig(int rentityId) {
        TenantGoamlConfig config = mock(TenantGoamlConfig.class);
        when(config.getRentityId()).thenReturn(rentityId);
        when(config.getJurisdictionCode()).thenReturn("AE");
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
    }

    @Test
    void createValidDpmsrPersistsValid() {
        stubConfig(3177);
        when(reportRepository.existsByEntityReference(any())).thenReturn(false);

        ReportResult result = service.create(minimalRequest("PAY-1", new BigDecimal("90000.00")), tenantId, actor);

        assertThat(result.status()).isEqualTo("VALID");
        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("VALID");
        assertThat(saved.getValue().getReportCode()).isEqualTo("DPMSR");
        assertThat(saved.getValue().getRentityId()).isEqualTo(3177);
        assertThat(saved.getValue().getReportXml()).contains("<report_code>DPMSR</report_code>");
        assertThat(saved.getValue().getInput()).contains("PAY-1");
        verify(auditService).record(eq("REPORT.CREATE"), any(), any(), any(), any());
    }

    @Test
    void belowThresholdIsInvalid() {
        stubConfig(3177);
        when(reportRepository.existsByEntityReference(any())).thenReturn(false);

        ReportResult result = service.create(minimalRequest("PAY-2", new BigDecimal("10000.00")), tenantId, actor);

        assertThat(result.status()).isEqualTo("INVALID");
        assertThat(result.validationMessages()).anyMatch(m -> m.code().equals("DPMS_THRESHOLD"));
    }

    @Test
    void duplicateEntityReferenceThrows() {
        when(reportRepository.existsByEntityReference("DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(minimalRequest("DUP", new BigDecimal("90000.00")), tenantId, actor))
                .isInstanceOf(ReportExceptions.DuplicateEntityReferenceException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void noTenantConfigYieldsInvalidWithMandatoryRentityId() {
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(reportRepository.existsByEntityReference(any())).thenReturn(false);

        ReportResult result = service.create(minimalRequest("PAY-3", new BigDecimal("90000.00")), tenantId, actor);

        assertThat(result.status()).isEqualTo("INVALID");
        assertThat(result.validationMessages())
                .anyMatch(m -> m.code().equals("MANDATORY") && m.path().contains("rentity_id"));
    }

    @Test
    void validateDoesNotPersistAndReturnsVerdict() {
        stubConfig(3177);

        ReportValidationResult valid = service.validate(minimalRequest("V-1", new BigDecimal("90000.00")), tenantId);
        ReportValidationResult invalid = service.validate(minimalRequest("V-2", new BigDecimal("10000.00")), tenantId);

        assertThat(valid.status()).isEqualTo("VALID");
        assertThat(invalid.status()).isEqualTo("INVALID");
        assertThat(invalid.messages()).anyMatch(m -> m.code().equals("DPMS_THRESHOLD"));
        // Pure check: nothing persisted, no duplicate lookup, no audit.
        verify(reportRepository, never()).save(any());
        verify(reportRepository, never()).existsByEntityReference(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void previewXmlReturnsMarshalledXmlWithoutPersisting() {
        stubConfig(3177);

        ReportPreview preview = service.previewXml(minimalRequest("P-1", new BigDecimal("90000.00")), tenantId);

        assertThat(preview.status()).isEqualTo("VALID");
        assertThat(preview.xml())
                .contains("<report_code>DPMSR</report_code>")
                .contains("<rentity_id>3177</rentity_id>");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void getMissingThrows() {
        when(reportRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(ReportExceptions.ReportNotFoundException.class);
    }

    // ---------- fixtures ----------

    private static DpmsrCreateRequest minimalRequest(String ref, BigDecimal value) {
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                "Minimal Trading FZE", null, "123456", null, "AE", null, null);
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party(
                "Seller of gold above AED 55,000", null, entity, null);
        DpmsrCreateRequest.Goods gold = new DpmsrCreateRequest.Goods(
                "GOLD", null, "1kg gold bar", null, null, value, "AED", null, null, null, null, null, null, null);
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                null, "Sara", "Khan", null, null, null, null, null, null, null, null, null, null);
        return new DpmsrCreateRequest(null, ref, odt("2026-06-02T12:00:00"), null,
                "DPMS threshold met", "Filed", List.of("DPMSJ"), mlro, null, List.of(party), List.of(gold));
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
