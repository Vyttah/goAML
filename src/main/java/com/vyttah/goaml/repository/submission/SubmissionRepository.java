package com.vyttah.goaml.repository.submission;

import com.vyttah.goaml.model.entity.submission.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link Submission}. Requires a bound tenant (see {@code ReportRepository}).
 */
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByReportIdOrderBySubmittedAtDesc(UUID reportId);
}
