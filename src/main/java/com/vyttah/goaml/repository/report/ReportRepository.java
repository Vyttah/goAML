package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.model.entity.report.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link Report}. Every call requires a tenant bound to
 * {@link com.vyttah.goaml.config.tenant.TenantContext} (the {@code JwtAuthFilter} sets it per request, the
 * Phase 9 poller binds it per tenant) or it routes to {@code public} and fails — by design.
 */
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByEntityReference(String entityReference);

    boolean existsByEntityReference(String entityReference);

    /** All reports in the given status for the current tenant (the poller queries {@code SUBMITTED}). */
    List<Report> findByStatus(String status);

    /** Delete-guard: is this user the author or reviewer of any report in the current tenant? If so, a
     *  hard-delete would orphan an audit reference — block it (disable the user instead). */
    boolean existsByCreatedBy(UUID createdBy);

    boolean existsByReviewedBy(UUID reviewedBy);

    /**
     * Atomic submit-claim (CAS): flip the report to the transient {@code SUBMITTING} state only if it is
     * still in the expected submittable status. Returns the update count — {@code 0} means another submit
     * already claimed it (or the status moved), and the caller must refuse with a conflict instead of
     * sending a second copy to the FIU. {@code @Transactional} because the caller
     * ({@code DefaultSubmissionService}) runs outside a transaction.
     */
    @Transactional
    @Modifying
    @Query("update Report r set r.status = 'SUBMITTING' where r.id = :id and r.status = :expectedStatus")
    int claimForSubmission(@Param("id") UUID id, @Param("expectedStatus") String expectedStatus);
}
