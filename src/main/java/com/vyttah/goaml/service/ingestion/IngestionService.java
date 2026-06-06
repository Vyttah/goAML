package com.vyttah.goaml.service.ingestion;

import com.vyttah.goaml.model.entity.ingestion.ImportJob;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates file imports (Phase 11): runs the right importer, tallies the per-row results into a
 * persisted {@link ImportJob}, audits, and serves the job history. Implemented by
 * {@link DefaultIngestionService}. Requires a bound tenant (the request filter / `UserPrincipal`).
 */
public interface IngestionService {

    /** Import a single goAML XML file → a one-row {@link ImportJob}. */
    ImportJob importXml(byte[] xml, String filename, UUID tenantId, UUID actorUserId);

    /**
     * Import a flat DPMSR CSV → an {@link ImportJob} with one result per row.
     *
     * @throws IngestionExceptions.ImportRejectedException if the file is unreadable, missing required
     *         headers, or over the row cap (no job is persisted)
     */
    ImportJob importCsv(byte[] csv, String filename, UUID tenantId, UUID actorUserId);

    /** A tenant's import jobs, newest first. */
    List<ImportJob> list();

    /**
     * One import job by id.
     *
     * @throws IngestionExceptions.ImportJobNotFoundException if it doesn't exist in this tenant
     */
    ImportJob get(UUID jobId);
}
