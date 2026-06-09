package com.vyttah.goaml.config;

import com.fasterxml.jackson.databind.Module;
import com.vyttah.goaml.domain.jackson.GeneratedEnumJacksonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link GeneratedEnumJacksonModule} so the xjc-generated goAML enums bind by their schema
 * value across the whole application's JSON (REST + persisted report JSONB). Spring Boot adds any
 * {@link Module} bean to the auto-configured {@code ObjectMapper}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module goamlGeneratedEnumModule() {
        return new GeneratedEnumJacksonModule();
    }
}
