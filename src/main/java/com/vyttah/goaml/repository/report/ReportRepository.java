package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.model.entity.report.Report;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
