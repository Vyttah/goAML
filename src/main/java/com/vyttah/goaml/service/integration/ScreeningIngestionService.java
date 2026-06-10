package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.ScreeningFilingPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningFilingResponse;
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

    /**
     * One-shot filing (Phase C.4a): build + validate + persist a DPMSR from a complete bundle (parties + goods
     * + header) and return the report id + status. Idempotent on {@code FIL-<companyId>-<filingRef>} — a retried
     * "File to goAML" returns the existing report rather than creating a duplicate.
     */
    ScreeningFilingResponse file(ScreeningFilingPayload payload);

    /** Status of a previously-filed report (by AML company + filing ref). */
    ScreeningFilingResponse filingStatus(String companyId, String filingRef);

    /**
     * The marshalled goAML XML of a previously-filed report (by AML company + filing ref), so the AML cockpit
     * can download the report it filed without leaving its own UI (Phase C.4b). 404 if no report / no XML.
     */
    String filingXml(String companyId, String filingRef);
}
