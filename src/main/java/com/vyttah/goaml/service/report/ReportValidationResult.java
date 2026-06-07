package com.vyttah.goaml.service.report;

import com.vyttah.goaml.engine.validation.ValidationMessage;

import java.util.List;

/**
 * Outcome of validating a DPMSR request <em>without</em> persisting it: the merged business-rule + XSD
 * verdict ({@code VALID}/{@code INVALID}) and all messages. Used by the MCP {@code goaml_validate_dpmsr}
 * tool (and available for a future REST validate endpoint) so callers can check a draft before saving.
 */
public record ReportValidationResult(String status, List<ValidationMessage> messages) {}
