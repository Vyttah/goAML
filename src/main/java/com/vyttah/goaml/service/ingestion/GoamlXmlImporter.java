package com.vyttah.goaml.service.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.engine.marshal.MarshallingException;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.validation.ReportValidator;
import com.vyttah.goaml.engine.validation.ValidationMessage;
import com.vyttah.goaml.engine.validation.ValidationResult;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Imports a single goAML XML file into a persisted {@code report} (Phase 11.2). Unmarshals the XML, re-marshals
 * it to canonical form, runs the same XSD + business-rule validators the JSON create path uses, and persists a
 * report row exactly like {@code DefaultReportService} (status {@code VALID}/{@code INVALID}, the canonical
 * XML, the validation messages). Returns a single-row {@link ImportRowResult}; nothing here throws for bad
 * input — an unreadable/duplicate/missing-reference file becomes a {@code FAILED} row.
 */
@Component
@RequiredArgsConstructor
public class GoamlXmlImporter {

    private static final int ROW = 1; // a single XML file = one report

    private final ReportMarshaller marshaller;
    private final XsdSchemaValidator xsdValidator;
    private final ReportValidator reportValidator;
    private final ReportRepository reportRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public ImportRowResult importXml(byte[] xml, String filename, UUID tenantId, UUID actorUserId) {
        Report tree;
        try {
            tree = marshaller.unmarshal(xml);
        } catch (MarshallingException e) {
            return ImportRowResult.failed(ROW, null, "Unparseable goAML XML: " + rootMessage(e));
        }

        String entityReference = tree.getEntityReference();
        if (entityReference == null || entityReference.isBlank()) {
            return ImportRowResult.failed(ROW, null, "Missing entity_reference in goAML XML");
        }
        if (reportRepository.existsByEntityReference(entityReference)) {
            return ImportRowResult.failed(ROW, entityReference,
                    "A report already exists with entity_reference " + entityReference);
        }

        // Re-marshal to canonical XML, then validate that (XSD) + the tree (business rules).
        byte[] canonical = marshaller.marshal(tree);
        String jurisdiction = configRepository.findByTenantId(tenantId)
                .map(c -> c.getJurisdictionCode().toLowerCase())
                .orElse("ae");

        ValidationResult xsd = xsdValidator.validate(canonical);
        ValidationResult rules = reportValidator.validate(tree, jurisdiction);
        List<ValidationMessage> messages = new ArrayList<>(rules.messages());
        messages.addAll(xsd.messages());
        boolean valid = xsd.isValid() && rules.isValid();
        String status = valid ? "VALID" : "INVALID";

        String reportCode = tree.getReportCode() != null ? tree.getReportCode().value() : null;
        com.vyttah.goaml.model.entity.report.Report entity =
                new com.vyttah.goaml.model.entity.report.Report(UUID.randomUUID(), entityReference,
                        reportCode, tree.getRentityId(), status, sourceMarker(filename), actorUserId);
        entity.setReportXml(new String(canonical, StandardCharsets.UTF_8));
        entity.setValidationErrors(toJson(messages));
        reportRepository.save(entity);

        return ImportRowResult.created(ROW, entityReference, status, entity.getId(), toMessageStrings(messages));
    }

    private String sourceMarker(String filename) {
        return toJson(new SourceMarker("GOAML_XML_IMPORT", filename));
    }

    private record SourceMarker(String source, String filename) {}

    private static List<String> toMessageStrings(List<ValidationMessage> messages) {
        return messages.stream()
                .map(m -> "[" + m.code() + "] " + m.message() + " (" + m.path() + ")")
                .toList();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize import JSON", e);
        }
    }
}
