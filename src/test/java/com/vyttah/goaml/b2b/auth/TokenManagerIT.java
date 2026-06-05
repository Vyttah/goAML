package com.vyttah.goaml.b2b.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.vyttah.goaml.b2b.B2bAuthMode;
import com.vyttah.goaml.b2b.B2bProperties;
import com.vyttah.goaml.b2b.B2bTenantConfig;
import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.integration.aws.GoamlCredentials;
import com.vyttah.goaml.integration.aws.GoamlSecretsClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for {@link DefaultTokenManager} against the docker-compose Redis ({@code :6379}) with the
 * goAML auth endpoint stubbed by WireMock. Tagged {@code redis} and reachability-gated, so it runs when Redis
 * is up (`docker compose up -d redis`) and skips cleanly otherwise. The credentials lookup is a small stub —
 * no LocalStack needed here.
 */
@Tag("redis")
class TokenManagerIT {

    private static final String GET_TOKEN = "/api/Authenticate/GetToken";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private WireMockServer wireMock;
    private DefaultTokenManager tokenManager;
    private B2bTenantConfig config;

    @BeforeAll
    static void redisUp() {
        assumeTrue(reachable("localhost", 6379), "Redis not reachable on :6379 — skipping");
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void redisDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        GoamlSecretsClient secrets = secretsPath ->
                new GoamlCredentials("re-3177", "s3cr3t!", null);

        tokenManager = new DefaultTokenManager(
                secrets, redis, new B2bProperties(Duration.ofMinutes(20)), RestClient.builder(),
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()));

        // Unique tenant id per test so the Redis key never collides across runs.
        config = new B2bTenantConfig("t-" + UUID.randomUUID(), wireMock.baseUrl(),
                "goaml/test/creds", B2bAuthMode.TOKEN);
    }

    @AfterEach
    void tearDown() {
        if (redis != null && config != null) {
            redis.delete("goaml:b2b:token:" + config.tenantId());
        }
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void authenticatesOnceThenServesCachedToken() {
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN))
                .willReturn(aResponse().withStatus(200).withBody("session-token-abc")));

        String first = tokenManager.token(config);
        String second = tokenManager.token(config);

        assertThat(first).isEqualTo("session-token-abc");
        assertThat(second).isEqualTo("session-token-abc");
        // second call served from Redis — only ONE call to goAML
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(GET_TOKEN)));
    }

    @Test
    void refreshReAuthenticates() {
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN))
                .willReturn(aResponse().withStatus(200).withBody("session-token-abc")));

        tokenManager.token(config);
        tokenManager.refresh(config);

        wireMock.verify(exactly(2), postRequestedFor(urlEqualTo(GET_TOKEN)));
    }

    @Test
    void unauthorizedThrowsB2bAuthException() {
        wireMock.stubFor(post(urlEqualTo(GET_TOKEN))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> tokenManager.token(config))
                .isInstanceOf(B2bAuthException.class);
        assertThat(redis.opsForValue().get("goaml:b2b:token:" + config.tenantId())).isNull();
    }

    private static boolean reachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
