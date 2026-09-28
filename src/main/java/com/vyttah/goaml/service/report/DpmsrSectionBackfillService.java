package com.vyttah.goaml.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.mapper.report.DpmsrRequestMapper;
import com.vyttah.goaml.model.mapper.report.DpmsrSectionMapper;
import com.vyttah.goaml.model.mapper.report.DpmsrSectionResult;
import com.vyttah.goaml.repository.report.AdditionalDetailRepository;
import com.vyttah.goaml.repository.report.GoodsAndServicesRepository;
import com.vyttah.goaml.repository.report.InvolvedPartyLegalRepository;
import com.vyttah.goaml.repository.report.InvolvedPartyNaturalRepository;
import com.vyttah.goaml.repository.report.ReportDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Persists the DPMSR section tables (migrate-json-storage-to-sql) from an already-built
 * {@link DpmsrReportInput} — the single shared implementation {@link DefaultReportService} calls on
 * every create (dual-write) and {@link DpmsrSectionBackfillRunner} calls per report during the one-time
 * historical backfill. One save implementation, not two to keep in sync.
 *
 * <p>{@link #backfillOne} additionally reconstructs the {@link DpmsrReportInput} from a report's already-
 * persisted {@code input} JSON, for the backfill case where no {@link DpmsrReportInput} was just built.
 * {@code report.input} was stored from one of two contracts depending on how the report was created (see
 * {@code ReportController}): {@link DpmsrReportPayload} (the primary {@code POST /api/v1/reports}
 * endpoint — implements {@code DpmsrInputSource} directly) or the curated {@link DpmsrCreateRequest}
 * ({@code POST /api/v1/reports/dpmsr}, mapped via {@link DpmsrRequestMapper}). Both are tried, payload
 * first since it's what the primary endpoint persists.
 */
@Service
@RequiredArgsConstructor
public class DpmsrSectionBackfillService {

    private final ObjectMapper objectMapper;
    private final DpmsrRequestMapper requestMapper;
    private final DpmsrSectionMapper sectionMapper;
    private final ReportDetailsRepository reportDetailsRepository;
    private final GoodsAndServicesRepository goodsAndServicesRepository;
    private final InvolvedPartyNaturalRepository involvedPartyNaturalRepository;
    private final InvolvedPartyLegalRepository involvedPartyLegalRepository;
    private final AdditionalDetailRepository additionalDetailRepository;

    /** Idempotency check — a report with a {@code dpmsr_report_details} row is already backfilled. */
    public boolean alreadyBackfilled(UUID reportId) {
        return reportDetailsRepository.existsById(reportId);
    }

    /**
     * Dual-write entry point (create path): map + save the 5 section tables for an already-built input.
     * Caller owns the transaction boundary (see {@link DefaultReportService#doCreate}).
     */
    public void persistSections(DpmsrReportInput input, UUID reportId) {
        DpmsrSectionResult sections = sectionMapper.toSections(input, reportId);
        reportDetailsRepository.save(sections.reportDetails());
        if (!sections.goods().isEmpty()) {
            goodsAndServicesRepository.saveAll(sections.goods());
        }
        if (!sections.naturalParties().isEmpty()) {
            involvedPartyNaturalRepository.saveAll(sections.naturalParties());
        }
        if (!sections.legalParties().isEmpty()) {
            involvedPartyLegalRepository.saveAll(sections.legalParties());
        }
        if (!sections.additionalDetails().isEmpty()) {
            additionalDetailRepository.saveAll(sections.additionalDetails());
        }
    }

    /** Backfill entry point (historical report): reconstruct the input from stored JSON, then persist. */
    @Transactional
    public void backfillOne(Report report) {
        DpmsrReportInput input = toInput(report);
        persistSections(input, report.getId());
    }

    private DpmsrReportInput toInput(Report report) {
        String json = report.getInput();
        try {
            DpmsrReportPayload payload = objectMapper.readValue(json, DpmsrReportPayload.class);
            return payload.toInput(report.getRentityId());
        } catch (JsonProcessingException payloadFailure) {
            try {
                DpmsrCreateRequest request = objectMapper.readValue(json, DpmsrCreateRequest.class);
                return requestMapper.toInput(request, report.getRentityId(), new ArrayList<>());
            } catch (JsonProcessingException createRequestFailure) {
                throw new IllegalStateException(
                        "report.input for " + report.getId()
                                + " matches neither DpmsrReportPayload nor DpmsrCreateRequest",
                        createRequestFailure);
            }
        }
    }
}
