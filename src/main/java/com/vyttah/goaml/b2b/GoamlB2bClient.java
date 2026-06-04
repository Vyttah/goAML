package com.vyttah.goaml.b2b;

import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.b2b.error.B2bValidationException;

/**
 * The goAML Web B2B REST operations, scoped per tenant (each call takes the tenant's {@link B2bTenantConfig},
 * so one client serves every tenant using that tenant's own endpoint + credentials).
 *
 * <p>Every method maps HTTP outcomes to the shared error taxonomy: 400 → {@link B2bValidationException}
 * (fix the report), 401 → re-auth-and-retry-once then {@link B2bAuthException}, other non-2xx / I/O →
 * {@link B2bTransportException} (retry with backoff).
 */
public interface GoamlB2bClient {

    /**
     * Submit a packaged report ZIP. Returns the FIU's {@code reportkey}.
     *
     * @param config   the tenant's B2B coordinates
     * @param zipBytes the report ZIP (report XML + attachments) from {@code ReportZipPackager}
     * @param filename the zip filename to send in the multipart part
     */
    String postReport(B2bTenantConfig config, byte[] zipBytes, String filename);

    /** Poll the asynchronous status of a previously-submitted report. */
    ReportStatus getReportStatus(B2bTenantConfig config, String reportKey);

    /** Retract a submitted report. */
    void deleteReport(B2bTenantConfig config, String reportKey);

    /** Send a MessageBoard message to the FIU; returns the raw response body. */
    String postMessage(B2bTenantConfig config, String message);

    /** Fetch the FIU's lookup code-lists (raw OData body) for refreshing local lookup sets. */
    String getLookups(B2bTenantConfig config);
}
