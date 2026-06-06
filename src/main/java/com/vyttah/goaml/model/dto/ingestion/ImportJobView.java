package com.vyttah.goaml.model.dto.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.model.entity.ingestion.ImportJob;
import com.vyttah.goaml.service.ingestion.ImportRowResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API view of an {@link ImportJob} (Phase 11), including the deserialized per-row {@link ImportRowResult}s.
 */
public record ImportJobView(UUID id, String sourceType, String filename, String status,
                            int totalRows, int succeeded, int failed,
                            List<ImportRowResult> results, OffsetDateTime createdAt) {

    public static ImportJobView from(ImportJob job, ObjectMapper objectMapper) {
        return new ImportJobView(job.getId(), job.getSourceType(), job.getFilename(), job.getStatus(),
                job.getTotalRows(), job.getSucceeded(), job.getFailed(),
                parseResults(job.getResults(), objectMapper), job.getCreatedAt());
    }

    private static List<ImportRowResult> parseResults(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ImportRowResult>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read import results JSON", e);
        }
    }
}
