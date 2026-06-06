package com.vyttah.goaml.config.ingestion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link IngestionProperties} (Phase 11) so {@code goaml.ingestion.*} is available to the ingestion
 * service. Multipart upload limits are configured separately under {@code spring.servlet.multipart.*}.
 */
@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionConfig {
}
