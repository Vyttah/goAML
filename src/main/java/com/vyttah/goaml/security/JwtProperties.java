package com.vyttah.goaml.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from {@code goaml.jwt.*}.
 *
 * @param secret                 HS256 signing secret (≥ 256 bits). Sourced from Secrets Manager in prod.
 * @param issuer                 value of the {@code iss} claim
 * @param accessTokenTtlMinutes  access token lifetime in minutes
 */
@ConfigurationProperties("goaml.jwt")
public record JwtProperties(String secret, String issuer, int accessTokenTtlMinutes) {
}
