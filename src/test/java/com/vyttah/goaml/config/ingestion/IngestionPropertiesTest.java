package com.vyttah.goaml.config.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11.1: {@link IngestionProperties} binds the {@code goaml.ingestion.*} keys.
 */
class IngestionPropertiesTest {

    @Test
    void bindsMaxRows() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "goaml.ingestion.max-rows", "500"));

        IngestionProperties props = new Binder(source)
                .bind("goaml.ingestion", IngestionProperties.class).get();

        assertThat(props.maxRows()).isEqualTo(500);
    }
}
