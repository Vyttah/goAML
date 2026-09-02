package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.model.entity.report.ReportDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Tenant-scoped repository for {@link ReportDetails} — see {@link ReportRepository} for the tenancy note. */
public interface ReportDetailsRepository extends JpaRepository<ReportDetails, UUID> {
}
