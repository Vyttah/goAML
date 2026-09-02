package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.model.entity.report.InvolvedPartyLegal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Tenant-scoped repository for {@link InvolvedPartyLegal} — see {@link ReportRepository} for the
 * tenancy note.
 */
public interface InvolvedPartyLegalRepository extends JpaRepository<InvolvedPartyLegal, UUID> {
}
