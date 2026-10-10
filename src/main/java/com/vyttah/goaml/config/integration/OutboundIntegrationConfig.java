package com.vyttah.goaml.config.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link OutboundIntegrationProperties} so {@code goaml.integration.outbound.*} is available to the
 * outbound signer and the report-status publisher (goAML ↔ ERP wiring, Phase 2 / S2).
 *
 * <p>The beans themselves are unconditional and check {@link OutboundIntegrationProperties#usable()} at call
 * time, rather than being created only when the feature is switched on. Making their existence conditional on
 * a property is what broke the goAML lookups in U0 — a blank placeholder satisfies
 * {@code @ConditionalOnProperty}, so the bean appears in environments that cannot use it and vanishes in ones
 * that can, and a missing bean fails at startup rather than where it is used. An always-present bean that
 * answers "not configured" is the behaviour a standalone goAML deployment needs anyway.
 */
@Configuration
@EnableConfigurationProperties(OutboundIntegrationProperties.class)
public class OutboundIntegrationConfig {
}
