package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.model.entity.report.AdditionalDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Tenant-scoped repository for {@link AdditionalDetail} — see {@link ReportRepository} for the tenancy note. */
public interface AdditionalDetailRepository extends JpaRepository<AdditionalDetail, UUID> {
}
