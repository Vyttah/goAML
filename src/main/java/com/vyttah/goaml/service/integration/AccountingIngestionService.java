package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.AccountingReportResponse;
import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;
import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;

import java.util.List;

/**
 * Accounting → goAML ingestion (Phase 1.5b, Model 2): receive a self-contained invoice payload, judge
 * reportability, and (if reportable) create a validated DPMSR draft. Implemented by
 * {@link DefaultAccountingIngestionService}.
 *
 * <p>Phase 2 (F0) adds the <em>filing</em> path: the ERP's goAML filing wizard assembles the whole report
 * itself and posts it ({@link #fileReport}); goAML does not judge reportability there — the operator already
 * did.
 */
public interface AccountingIngestionService {

    /** Ingest one source document; idempotent on the derived goAML reference. */
    AccountingTxnResponse ingest(AccountingTxnPayload payload);

    /**
     * File an ERP-assembled full-fidelity DPMSR report (Phase 2, F0). Resolves the goAML tenant from the
     * accounting company's {@code ACCOUNTING} external ref, defaults the reporting person when the caller
     * omitted it, creates + validates the report, and applies the same auto-submit / MLRO-notify gate as
     * {@link #ingest}. Idempotent on {@code payload.entityReference()}: a retry returns the existing report
     * untouched.
     *
     * @param companyId     the accounting company id (already org-checked by the controller)
     * @param payload       the assembled report
     * @param actorSubject  the ERP user id from the assertion's {@code sub} claim, recorded in the audit log
     */
    AccountingReportResponse fileReport(int companyId, DpmsrReportPayload payload, String actorSubject);

    /** Status + persisted validation messages of a filed report, by its {@code entityReference}. */
    AccountingReportResponse reportStatus(int companyId, String entityReference);

    /** Status of a previously-ingested document (by accounting company + document number). */
    AccountingTxnResponse status(int companyId, String documentNumber);

    /** All goAML reports originating from this accounting company (optionally filtered by status). */
    List<AccountingTxnResponse> list(int companyId, String statusFilter);
}
