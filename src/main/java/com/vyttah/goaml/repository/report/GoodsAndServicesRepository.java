package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.model.entity.report.GoodsAndServices;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Tenant-scoped repository for {@link GoodsAndServices} — see {@link ReportRepository} for the tenancy note. */
public interface GoodsAndServicesRepository extends JpaRepository<GoodsAndServices, UUID> {
}
