package com.vyttah.goaml.model.entity.federated;

/**
 * A sibling Vyttah system that integrates with goAML via federated token-exchange and/or the integration
 * push endpoints (Phase 1.5).
 */
public enum SourceSystem {
    /** Accounting / ERP — source of transactions/invoices that may be reportable (DPMSR). */
    ACCOUNTING,
    /** AML Screening — source of party / KYC / sanctions data. */
    SCREENING
}
