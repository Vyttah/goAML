package com.vyttah.goaml.b2b.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.vyttah.goaml.b2b.B2bAuthMode;
import com.vyttah.goaml.b2b.B2bProperties;
import com.vyttah.goaml.b2b.B2bTenantConfig;
import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.integration.aws.GoamlCredentials;
import com.vyttah.goaml.integration.aws.GoamlSecretsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link DefaultTokenManager}: Redis is mocked and the goAML auth endpoint is an
 * in-process WireMock server, so every branch (cache hit/miss, store, refresh, invalidate, 401, server
 * error, empty token, connection failure, clientCode body) runs without Docker.
 */
class DefaultTokenManagerTest {

    private static final String GET_TOKEN = "/api/Authenticate/GetToken";

    private final GoamlSecretsClient secrets = mock(GoamlSecretsClient.class);
    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private WireMockServer wireMock;
    private DefaultTokenManager tokenManager;
    private B2bTenantConfig config;
    private String key;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        when(redis.opsForValue()).thenReturn(ops);

        tokenManager = new DefaultTokenManager(secrets, redis, new B2bProperties(Duration.ofMinutes(20)),
                RestClient.builder(),
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()));

        config = new B2bTenantConfig("t1", wireMock.baseUrl(), "goaml/t1", B2bAuthMode.TOKEN);
        key = "goaml:b2b:token:t1";
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void cacheHitSkipsAuth() {
        when(ops.get(key)).thenReturn("cached-token");

        assertThat(tokenManager.token(config)).isEqualTo("cached-token");

        wireMock.verify(exactly(0), postRequestedFor(urlEqualTo(GET_TOKEN)));
        verify(secrets, never()).fetch(any());
    }

    @Test
    void cacheMissAuthenticatesAndStores() {
        when(ops.get(key)).thenReturn(null);
        when(secrets.fetch("goaml/t1")).thenReturn(new GoamlCredentials("u", "p", null));
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN))
                .willReturn(aResponse().withStatus(200).withBody("fresh-token")));

        assertThat(tokenManager.token(config)).isEqualTo("fresh-token");

        verify(ops).set(eq(key), eq("fresh-token"), eq(Duration.ofMinutes(20)));
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(GET_TOKEN)));
    }

    @Test
    void refreshAuthenticatesEvenWithCachePresent() {
        when(secrets.fetch("goaml/t1")).thenReturn(new GoamlCredentials("u", "p", "DXB"));
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN))
                .willReturn(aResponse().withStatus(200).withBody("re-token")));

        assertThat(tokenManager.refresh(config)).isEqualTo("re-token");

        verify(ops).set(eq(key), eq("re-token"), any(Duration.class));
    }

    @Test
    void invalidateDeletesKey() {
        tokenManager.invalidate("t1");
        verify(redis).delete(key);
    }

    @Test
    void unauthorizedThrowsAndDoesNotCache() {
        when(ops.get(key)).thenReturn(null);
        when(secrets.fetch("goaml/t1")).thenReturn(new GoamlCredentials("u", "p", null));
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> tokenManager.token(config)).isInstanceOf(B2bAuthException.class);
        verify(ops, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void serverErrorThrowsTransport() {
        when(ops.get(key)).thenReturn(null);
        when(secrets.fetch("goaml/t1")).thenReturn(new GoamlCredentials("u", "p", null));
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> tokenManager.token(config)).isInstanceOf(B2bTransportException.class);
    }

    @Test
    void emptyTokenThrowsAuth() {
        when(ops.get(key)).thenReturn(null);
        when(secrets.fetch("goaml/t1")).thenReturn(new GoamlCredentials("u", "p", null));
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN))
                .willReturn(aResponse().withStatus(200).withBody("   ")));

        assertThatThrownBy(() -> tokenManager.token(config)).isInstanceOf(B2bAuthException.class);
    }

    @Test
    void connectionFailureThrowsTransport() {
        when(ops.get(key)).thenReturn(null);
        when(secrets.fetch("goaml/t1")).thenReturn(new GoamlCredentials("u", "p", null));
        B2bTenantConfig dead = new B2bTenantConfig("t1", "http://localhost:1", "goaml/t1", B2bAuthMode.TOKEN);

        assertThatThrownBy(() -> tokenManager.token(dead)).isInstanceOf(B2bTransportException.class);
    }
}
