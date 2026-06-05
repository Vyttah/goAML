package com.vyttah.goaml.b2b.auth;

import com.vyttah.goaml.b2b.B2bProperties;
import com.vyttah.goaml.b2b.B2bTenantConfig;
import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.integration.aws.GoamlCredentials;
import com.vyttah.goaml.integration.aws.GoamlSecretsClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Redis-backed {@link TokenManager}. Resolves a tenant's credentials via {@link GoamlSecretsClient},
 * authenticates at {@code {baseUrl}/api/Authenticate/GetToken}, and caches the returned {@code SqlAuthCookie}
 * under {@code goaml:b2b:token:<tenantId>} with the configured TTL — shared across app instances.
 */
@Component
public class DefaultTokenManager implements TokenManager {

    static final String KEY_PREFIX = "goaml:b2b:token:";
    private static final String GET_TOKEN_PATH = "/api/Authenticate/GetToken";

    private final GoamlSecretsClient secretsClient;
    private final StringRedisTemplate redis;
    private final B2bProperties properties;
    private final RestClient.Builder restClientBuilder;
    /** Shared HTTP/1.1 factory (see {@link com.vyttah.goaml.b2b.B2bConfig}). */
    private final JdkClientHttpRequestFactory requestFactory;

    public DefaultTokenManager(GoamlSecretsClient secretsClient, StringRedisTemplate redis,
                               B2bProperties properties, RestClient.Builder restClientBuilder,
                               JdkClientHttpRequestFactory b2bRequestFactory) {
        this.secretsClient = secretsClient;
        this.redis = redis;
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.requestFactory = b2bRequestFactory;
    }

    @Override
    public String token(B2bTenantConfig config) {
        String cached = redis.opsForValue().get(key(config.tenantId()));
        return cached != null ? cached : refresh(config);
    }

    @Override
    public String refresh(B2bTenantConfig config) {
        String token = authenticate(config);
        redis.opsForValue().set(key(config.tenantId()), token, properties.tokenTtl());
        return token;
    }

    @Override
    public void invalidate(String tenantId) {
        redis.delete(key(tenantId));
    }

    private String authenticate(B2bTenantConfig config) {
        GoamlCredentials creds = secretsClient.fetch(config.secretsPath());
        RestClient client = restClientBuilder.clone()
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .build();

        String token;
        try {
            token = client.post()
                    .uri(GET_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(authBody(creds))
                    .retrieve()
                    .onStatus(s -> s.value() == 401, (req, res) -> {
                        throw new B2bAuthException(
                                "goAML rejected credentials for tenant " + config.tenantId());
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new B2bTransportException(
                                "goAML auth failed for tenant " + config.tenantId()
                                        + ": HTTP " + res.getStatusCode().value());
                    })
                    .body(String.class);
        } catch (B2bAuthException | B2bTransportException e) {
            throw e;
        } catch (RestClientException e) {
            throw new B2bTransportException(
                    "goAML auth transport failure for tenant " + config.tenantId(), e);
        }

        if (token == null || token.isBlank()) {
            throw new B2bAuthException("goAML auth returned an empty token for tenant " + config.tenantId());
        }
        return token.trim();
    }

    private static Map<String, String> authBody(GoamlCredentials creds) {
        return creds.clientCode() == null
                ? Map.of("userName", creds.username(), "password", creds.password())
                : Map.of("userName", creds.username(), "password", creds.password(),
                        "clientCode", creds.clientCode());
    }

    private static String key(String tenantId) {
        return KEY_PREFIX + tenantId;
    }
}
