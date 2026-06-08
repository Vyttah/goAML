package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;

import java.util.List;

/**
 * Accounting → goAML ingestion (Phase 1.5b, Model 2): receive a self-contained invoice payload, judge
 * reportability, and (if reportable) create a validated DPMSR draft. Implemented by
 * {@link DefaultAccountingIngestionService}.
 */
public interface AccountingIngestionService {

    /** Ingest one source document; idempotent on the derived goAML reference. */
    AccountingTxnResponse ingest(AccountingTxnPayload payload);

    /** Status of a previously-ingested document (by accounting company + document number). */
    AccountingTxnResponse status(int companyId, String documentNumber);

    /** All goAML reports originating from this accounting company (optionally filtered by status). */
    List<AccountingTxnResponse> list(int companyId, String statusFilter);
}
