package com.vyttah.goaml.repository.ingestion;

import com.vyttah.goaml.model.entity.ingestion.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link ImportJob}. Requires a bound tenant (see {@code ReportRepository}):
 * the table only exists in {@code tenant_<id>} schemas, resolved via the active {@code search_path}.
 */
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    List<ImportJob> findAllByOrderByCreatedAtDesc();

    /** B6 delete-guard: did this user run any import in the current tenant? */
    boolean existsByCreatedBy(UUID createdBy);
}
