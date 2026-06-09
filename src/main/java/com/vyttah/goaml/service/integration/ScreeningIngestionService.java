package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningSubjectResponse;

import java.util.List;

/**
 * AML screening → goAML ingestion (Phase 1.5c): receive a resolved screened-customer payload, map it to a
 * goAML party set, and persist it as a reusable {@code screened_subject} (idempotent on the derived
 * reference). The stored subject can later seed a DPMSR draft's parties. Implemented by
 * {@link DefaultScreeningIngestionService}.
 */
public interface ScreeningIngestionService {

    /** Store (or update) one screened customer; idempotent on {@code SCR-<companyId>-<customerUid>}. */
    ScreeningSubjectResponse ingest(ScreeningPartyPayload payload);

    /** Fetch a previously-stored subject (by screening company + customer uid). */
    ScreeningSubjectResponse get(String companyId, String customerUid);

    /** All screened subjects originating from this screening company. */
    List<ScreeningSubjectResponse> list(String companyId);
}
