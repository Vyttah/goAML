package com.vyttah.goaml.model.mapper.report;

import com.vyttah.goaml.model.entity.report.AdditionalDetail;
import com.vyttah.goaml.model.entity.report.GoodsAndServices;
import com.vyttah.goaml.model.entity.report.InvolvedPartyLegal;
import com.vyttah.goaml.model.entity.report.InvolvedPartyNatural;
import com.vyttah.goaml.model.entity.report.ReportDetails;

import java.util.List;

/**
 * The five section-table rows built from one {@link com.vyttah.goaml.engine.build.DpmsrReportInput} by
 * {@link DpmsrSectionMapper} — see {@code .planning/migrate-json-storage-to-sql.md}.
 */
public record DpmsrSectionResult(
        ReportDetails reportDetails,
        List<GoodsAndServices> goods,
        List<InvolvedPartyNatural> naturalParties,
        List<InvolvedPartyLegal> legalParties,
        List<AdditionalDetail> additionalDetails) {
}
