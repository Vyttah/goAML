package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.model.entity.report.InvolvedPartyNatural;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Tenant-scoped repository for {@link InvolvedPartyNatural} — see {@link ReportRepository} for the
 * tenancy note.
 */
public interface InvolvedPartyNaturalRepository extends JpaRepository<InvolvedPartyNatural, UUID> {
}
