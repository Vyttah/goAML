package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.mcp.McpIdentity;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionExceptions;
import com.vyttah.goaml.service.submission.SubmissionResult;
import com.vyttah.goaml.service.submission.SubmissionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The guarded, irreversible MCP tools — submitting a report to the UAE FIU, tracking its status, and posting
 * FIU MessageBoard messages. <strong>The single most important rule of the plugin: an agent must never
 * silently file a regulatory report.</strong> So the submit tool is, in order:
 *
 * <ol>
 *   <li><b>MLRO-gated</b> — only the money-laundering reporting officer role may submit;</li>
 *   <li><b>dry-run-first</b> — the default is a preview that sends nothing and shows the exact XML;</li>
 *   <li><b>human-confirmed</b> — a real send requires {@code dryRun=false} <em>and</em> {@code confirm=true};</li>
 *   <li><b>validate-first</b> — only a {@code VALID} report is submittable (also enforced server-side);</li>
 *   <li><b>idempotent</b> — the service refuses to re-submit a report that is not {@code VALID}.</li>
 * </ol>
 *
 * <p>FIU outcomes (rejection / transport failure) are caught and returned as structured results so the agent
 * reacts correctly rather than seeing an opaque exception. Submission/audit happen in {@link SubmissionService};
 * these tools add only the guard rails.
 */
@Component
public class SubmissionTools {

    private final ReportService reportService;
    private final SubmissionService submissionService;

    public SubmissionTools(ReportService reportService, SubmissionService submissionService) {
        this.reportService = reportService;
        this.submissionService = submissionService;
    }

    /** Result of a submit attempt — either a dry-run preview, a refusal, or a real outcome. */
    public record SubmitResult(
            boolean dryRun,
            boolean submitted,
            UUID reportId,
            String entityReference,
            String reportStatus,
            String reportKey,
            String xmlPreview,
            String message) {}

    /** Latest FIU status for a report's most recent submission. */
    public record FiuStatusResult(String reportKey, String status, String errors) {}

    /** Outcome of posting an FIU MessageBoard message. */
    public record MessageResult(boolean sent, String response, String message) {}

    @Tool(name = "goaml_submit_report",
            description = "Submit a stored report to the UAE FIU. SAFE BY DEFAULT: with no flags (or "
                    + "dryRun=true) it performs a DRY RUN — it sends nothing and returns the exact goAML XML "
                    + "that WOULD be filed. A real, IRREVERSIBLE submission requires BOTH dryRun=false AND "
                    + "confirm=true, the MLRO role, and a VALID report. Always dry-run and show the human the "
                    + "payload before a real submit.")
    public SubmitResult submitReport(
            @ToolParam(description = "The report's UUID.") String reportId,
            @ToolParam(required = false,
                    description = "Dry run (default TRUE). TRUE previews the payload and sends nothing.")
            Boolean dryRun,
            @ToolParam(required = false,
                    description = "Explicit confirmation for a REAL submission. Must be true (with "
                            + "dryRun=false) to actually file to the FIU. This is irreversible.")
            Boolean confirm) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("MLRO");
        boolean isDryRun = dryRun == null || dryRun;          // default: dry run
        boolean isConfirmed = confirm != null && confirm;     // default: not confirmed
        UUID rid = parseUuid(reportId);

        // Gate 1 — a real submission requires explicit confirmation (checked before any lookup).
        if (!isDryRun && !isConfirmed) {
            return new SubmitResult(false, false, rid, null, null, null, null,
                    "Refusing to submit to the FIU without explicit confirmation. Filing is IRREVERSIBLE. "
                            + "Re-call goaml_submit_report with dryRun=false AND confirm=true to file report "
                            + rid + ".");
        }

        Report report = reportService.get(rid); // throws ReportNotFoundException if absent in this tenant
        String entityRef = report.getEntityReference();
        String status = report.getStatus();

        // Gate 2 — validate-before-submit: only VALID reports may be filed.
        if (!"VALID".equals(status)) {
            return new SubmitResult(isDryRun, false, rid, entityRef, status, null, null,
                    "Report " + rid + " is " + status + " — only VALID reports can be submitted. Fix the "
                            + "validation errors (goaml_validate_dpmsr) and re-create it first.");
        }

        // Dry run — show exactly what would be sent, send nothing.
        if (isDryRun) {
            return new SubmitResult(true, false, rid, entityRef, status, null, report.getReportXml(),
                    "DRY RUN — nothing was sent to the FIU. xmlPreview is the exact goAML XML that WOULD be "
                            + "submitted (registered attachments are bundled at submit time). To file it, an "
                            + "MLRO must re-call with dryRun=false AND confirm=true. This is irreversible.");
        }

        // Real submission — MLRO + confirmed + VALID. The service validates, packages, posts, and audits.
        try {
            SubmissionResult result = submissionService.submit(rid, identity.tenantId(), identity.userId());
            return new SubmitResult(false, true, rid, entityRef, result.status(), result.reportKey(), null,
                    "Submitted to the FIU. reportKey=" + result.reportKey()
                            + ". Track the outcome with goaml_get_fiu_status.");
        } catch (SubmissionExceptions.SubmissionRejectedException e) {
            return new SubmitResult(false, false, rid, entityRef, "REJECTED", null, null,
                    "The FIU REJECTED the report. Fix it and re-submit. FIU response: " + e.responseBody());
        } catch (SubmissionExceptions.SubmissionTransportException e) {
            return new SubmitResult(false, false, rid, entityRef, "FAILED", null, null,
                    "Submission failed before the FIU accepted it (transient auth/transport): "
                            + e.getMessage() + ". You may retry.");
        } catch (SubmissionExceptions.TenantConfigMissingException e) {
            return new SubmitResult(false, false, rid, entityRef, status, null, null,
                    "This tenant has no FIU configuration, so the report cannot be submitted. " + e.getMessage());
        }
    }

    @Tool(name = "goaml_get_fiu_status",
            description = "Fetch the latest FIU status for a submitted report (polls the FIU and returns its "
                    + "reportKey, status — SUBMITTED/ACCEPTED/REJECTED — and any errors). Read-only. Requires "
                    + "ANALYST, MLRO, or TENANT_ADMIN.")
    public FiuStatusResult getFiuStatus(
            @ToolParam(description = "The report's UUID.") String reportId) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        ReportStatus status = submissionService.refreshStatus(parseUuid(reportId), identity.tenantId());
        return new FiuStatusResult(status.reportKey(), status.status(), status.errors());
    }

    @Tool(name = "goaml_post_message",
            description = "Post a free-text message to the tenant's FIU MessageBoard (correspondence, not a "
                    + "report). This SENDS to the regulator, so it requires the MLRO role AND confirm=true; "
                    + "without confirmation it refuses and sends nothing.")
    public MessageResult postMessage(
            @ToolParam(description = "The message text to send to the FIU.") String message,
            @ToolParam(required = false,
                    description = "Explicit confirmation; must be true to actually send.") Boolean confirm) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("MLRO");
        if (confirm == null || !confirm) {
            return new MessageResult(false, null,
                    "Refusing to send a message to the FIU without confirmation. Re-call goaml_post_message "
                            + "with confirm=true to send it.");
        }
        String response = submissionService.postMessage(message, identity.tenantId(), identity.userId());
        return new MessageResult(true, response, "Message posted to the FIU MessageBoard.");
    }

    private static UUID parseUuid(String reportId) {
        try {
            return UUID.fromString(reportId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("reportId must be a UUID, got '" + reportId + "'");
        }
    }
}
