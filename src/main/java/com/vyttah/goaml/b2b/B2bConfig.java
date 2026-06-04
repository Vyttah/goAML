package com.vyttah.goaml.b2b;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;

/**
 * Enables {@link B2bProperties} and provides the shared HTTP request factory the B2B layer uses.
 *
 * <p>The factory is pinned to <strong>HTTP/1.1</strong>: goAML Web is a legacy ASP.NET service that speaks
 * HTTP/1.1, and the JDK client's default HTTP/2 (h2c) negotiation otherwise fails ({@code RST_STREAM}). The
 * underlying {@link HttpClient} is thread-safe and reused by both the token manager and the report client.
 */
@Configuration
@EnableConfigurationProperties(B2bProperties.class)
public class B2bConfig {

    @Bean
    public JdkClientHttpRequestFactory b2bRequestFactory() {
        return new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
    }
}
