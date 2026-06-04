package com.vyttah.goaml.service.submission;

import java.util.UUID;

/**
 * The outcome of submitting a report to the FIU: the submission row id, the FIU {@code reportkey}, and the
 * submission status.
 */
public record SubmissionResult(UUID submissionId, String reportKey, String status) {
}
