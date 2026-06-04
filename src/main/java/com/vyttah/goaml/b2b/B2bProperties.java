package com.vyttah.goaml.b2b;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * goAML B2B client configuration bound from {@code goaml.b2b.*}.
 *
 * @param tokenTtl how long a cached session token stays valid in Redis (default 20 minutes)
 */
@ConfigurationProperties("goaml.b2b")
public record B2bProperties(Duration tokenTtl) {

    public B2bProperties {
        if (tokenTtl == null) {
            tokenTtl = Duration.ofMinutes(20);
        }
    }
}
