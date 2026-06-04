package com.vyttah.goaml.service.report;

import com.vyttah.goaml.engine.validation.ValidationMessage;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of creating/validating a report: its id, persisted status, and the merged validation messages
 * (business-rule + XSD). The controller maps this to the HTTP response.
 */
public record ReportResult(UUID reportId, String status, List<ValidationMessage> validationMessages) {
}
