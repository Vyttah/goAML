package com.vyttah.goaml.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.engine.marshal.MarshallingException;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.validation.ReportValidator;
import com.vyttah.goaml.engine.validation.ValidationResult;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoamlXmlImporter}: marshaller/validators/repos mocked, a real {@link ObjectMapper},
 * a real domain {@link Report} tree. Covers the create (VALID), create-INVALID, unparseable, missing-ref,
 * and duplicate branches — and that nothing throws for bad input.
 */
class GoamlXmlImporterTest {

    private final ReportMarshaller marshaller = mock(ReportMarshaller.class);
    private final XsdSchemaValidator xsdValidator = mock(XsdSchemaValidator.class);
    private final ReportValidator reportValidator = mock(ReportValidator.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final TenantGoamlConfigRepository configRepository = mock(TenantGoamlConfigRepository.class);

    private final GoamlXmlImporter importer = new GoamlXmlImporter(marshaller, xsdValidator, reportValidator,
            reportRepository, configRepository, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private Report tree(String ref) {
        Report r = new Report();
        r.setEntityReference(ref);
        r.setReportCode(ReportType.DPMSR);
        r.setRentityId(3177);
        return r;
    }

    @Test
    void validXmlCreatesValidReport() {
        Report tree = tree("REF-1");
        when(marshaller.unmarshal(any())).thenReturn(tree);
        when(marshaller.marshal(tree)).thenReturn("<report/>".getBytes(StandardCharsets.UTF_8));
        when(reportRepository.existsByEntityReference("REF-1")).thenReturn(false);
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(xsdValidator.validate(any())).thenReturn(new ValidationResult());
        when(reportValidator.validate(any(), anyString())).thenReturn(new ValidationResult());

        ImportRowResult result = importer.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(result.status()).isEqualTo("VALID");
        assertThat(result.entityReference()).isEqualTo("REF-1");
        assertThat(result.reportCreated()).isTrue();

        ArgumentCaptor<com.vyttah.goaml.model.entity.report.Report> saved =
                ArgumentCaptor.forClass(com.vyttah.goaml.model.entity.report.Report.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getEntityReference()).isEqualTo("REF-1");
        assertThat(saved.getValue().getReportCode()).isEqualTo("DPMSR");
        assertThat(saved.getValue().getRentityId()).isEqualTo(3177);
        assertThat(saved.getValue().getStatus()).isEqualTo("VALID");
        assertThat(saved.getValue().getReportXml()).isEqualTo("<report/>");
        assertThat(saved.getValue().getInput()).contains("GOAML_XML_IMPORT").contains("r.xml");
    }

    @Test
    void xsdErrorsCreateInvalidReport() {
        Report tree = tree("REF-2");
        when(marshaller.unmarshal(any())).thenReturn(tree);
        when(marshaller.marshal(tree)).thenReturn("<report/>".getBytes(StandardCharsets.UTF_8));
        when(reportRepository.existsByEntityReference("REF-2")).thenReturn(false);
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        ValidationResult xsd = new ValidationResult();
        xsd.error("report", "XSD", "element 'foo' not allowed");
        when(xsdValidator.validate(any())).thenReturn(xsd);
        when(reportValidator.validate(any(), anyString())).thenReturn(new ValidationResult());

        ImportRowResult result = importer.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(result.status()).isEqualTo("INVALID");
        assertThat(result.reportCreated()).isTrue();
        assertThat(result.errors()).anyMatch(s -> s.contains("not allowed"));
        verify(reportRepository).save(any());
    }

    @Test
    void unparseableXmlFailsRowWithoutSaving() {
        when(marshaller.unmarshal(any())).thenThrow(new MarshallingException("boom", new RuntimeException("eof")));

        ImportRowResult result = importer.importXml("garbage".getBytes(), "r.xml", tenantId, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.reportCreated()).isFalse();
        assertThat(result.errors().get(0)).contains("Unparseable");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void missingEntityReferenceFails() {
        when(marshaller.unmarshal(any())).thenReturn(tree(null));

        ImportRowResult result = importer.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errors().get(0)).contains("entity_reference");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void mismatchedRentityIdIsRejectedWhenTenantHasAConfiguredOne() {
        // A file claiming a different reporting entity than this tenant's configured rentity_id is someone
        // else's report — reject the row, never persist it under this tenant.
        Report tree = tree("REF-RENT");
        tree.setRentityId(9999);
        when(marshaller.unmarshal(any())).thenReturn(tree);
        when(reportRepository.existsByEntityReference("REF-RENT")).thenReturn(false);
        com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig config =
                mock(com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig.class);
        when(config.getRentityId()).thenReturn(3177);
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));

        ImportRowResult result = importer.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errors().get(0)).contains("rentity_id 9999").contains("3177");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void matchingRentityIdPassesTheTenantBindingCheck() {
        Report tree = tree("REF-OK");
        when(marshaller.unmarshal(any())).thenReturn(tree);
        when(marshaller.marshal(tree)).thenReturn("<report/>".getBytes(StandardCharsets.UTF_8));
        when(reportRepository.existsByEntityReference("REF-OK")).thenReturn(false);
        com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig config =
                mock(com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig.class);
        when(config.getRentityId()).thenReturn(3177);  // == tree's rentity_id
        when(config.getJurisdictionCode()).thenReturn("AE");
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(xsdValidator.validate(any())).thenReturn(new ValidationResult());
        when(reportValidator.validate(any(), anyString())).thenReturn(new ValidationResult());

        ImportRowResult result = importer.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(result.status()).isEqualTo("VALID");
        verify(reportRepository).save(any());
    }

    @Test
    void duplicateEntityReferenceFails() {
        when(marshaller.unmarshal(any())).thenReturn(tree("DUP"));
        when(reportRepository.existsByEntityReference("DUP")).thenReturn(true);

        ImportRowResult result = importer.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errors().get(0)).contains("already exists");
        verify(reportRepository, never()).save(any());
    }
}
