package com.vyttah.goaml.service.submission;

import com.vyttah.goaml.b2b.ReportStatus;

import java.util.UUID;

/**
 * Submits a validated report to the FIU and refreshes its status. Resolves the tenant's B2B coordinates from
 * {@code tenant_goaml_config}, packages the stored XML, calls the goAML B2B client, and records the
 * submission + the report's new status.
 */
public interface SubmissionService {

    /**
     * Submit a {@code VALID} report. Persists a {@code submission} row + sets the report {@code SUBMITTED}.
     *
     * @throws SubmissionExceptions.ReportNotSubmittableException report missing or not {@code VALID}
     * @throws SubmissionExceptions.TenantConfigMissingException  no {@code tenant_goaml_config}
     * @throws SubmissionExceptions.SubmissionRejectedException    FIU rejected (400)
     * @throws SubmissionExceptions.SubmissionTransportException   auth/transport failure
     */
    SubmissionResult submit(UUID reportId, UUID tenantId, UUID actorUserId);

    /** Poll the FIU for the latest status of a report's most recent submission and persist the update. */
    ReportStatus refreshStatus(UUID reportId, UUID tenantId);
}
