package com.vyttah.goaml.config.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingestion configuration bound from {@code goaml.ingestion.*} (Phase 11).
 *
 * @param maxRows the maximum number of data rows a single CSV import may contain; larger files are
 *                rejected up front (synchronous processing, so this bounds the in-request work).
 */
@ConfigurationProperties("goaml.ingestion")
public record IngestionProperties(int maxRows) {
}
