package com.vyttah.goaml.service.report;

import com.vyttah.goaml.engine.validation.ValidationMessage;

import java.util.List;

/**
 * Outcome of building a DPMSR request to its marshalled goAML XML <em>without</em> persisting it, plus the
 * validation verdict. Used by the MCP {@code goaml_preview_dpmsr_xml} tool (and a future REST preview
 * endpoint) — the XML that <em>would</em> be submitted, so a human/agent can inspect it first.
 */
public record ReportPreview(String status, String xml, List<ValidationMessage> messages) {}
