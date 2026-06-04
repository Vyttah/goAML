package com.vyttah.goaml.b2b;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link B2bProperties}. The {@code RestClient.Builder} the B2B client uses is the one Spring Boot
 * auto-configures, so no extra bean is needed here.
 */
@Configuration
@EnableConfigurationProperties(B2bProperties.class)
public class B2bConfig {
}
