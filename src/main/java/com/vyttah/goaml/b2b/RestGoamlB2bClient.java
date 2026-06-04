package com.vyttah.goaml.b2b;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.b2b.auth.TokenManager;
import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.b2b.error.B2bValidationException;
import com.vyttah.goaml.integration.aws.GoamlCredentials;
import com.vyttah.goaml.integration.aws.GoamlSecretsClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@link RestClient}-based {@link GoamlB2bClient}. Builds a per-tenant client (the tenant's base URL + the
 * shared HTTP/1.1 factory), applies auth (a {@code SqlAuthCookie} from {@link TokenManager} for TOKEN-mode
 * tenants, HTTP Basic for BASIC-mode), maps 400/401/other failures to the typed errors, and retries once
 * after a 401 with a freshly-refreshed token.
 */
@Component
public class RestGoamlB2bClient implements GoamlB2bClient {

    private static final String POST_REPORT = "/api/Reports/PostReport";
    private static final String ODATA_REPORTS = "/odata/Odata/OdataReports";
    private static final String DELETE_REPORT = "/api/Reports/{reportKey}";
    private static final String MESSAGE_BOARD = "/api/MessageBoard";
    private static final String ODATA_LOOKUPS = "/odata/Odata/OdataLookups";
    private static final String AUTH_COOKIE = "SqlAuthCookie";

    private final TokenManager tokenManager;
    private final GoamlSecretsClient secretsClient;
    private final RestClient.Builder restClientBuilder;
    private final JdkClientHttpRequestFactory requestFactory;
    private final ObjectMapper objectMapper;

    public RestGoamlB2bClient(TokenManager tokenManager, GoamlSecretsClient secretsClient,
                              RestClient.Builder restClientBuilder,
                              JdkClientHttpRequestFactory b2bRequestFactory, ObjectMapper objectMapper) {
        this.tokenManager = tokenManager;
        this.secretsClient = secretsClient;
        this.restClientBuilder = restClientBuilder;
        this.requestFactory = b2bRequestFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public String postReport(B2bTenantConfig config, byte[] zipBytes, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("report", resource(zipBytes, filename));

        String reportKey = withAuth(config, auth -> mapErrors(client(config).post()
                        .uri(POST_REPORT)
                        .headers(auth)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve())
                .body(String.class));
        return reportKey == null ? null : reportKey.trim();
    }

    @Override
    public ReportStatus getReportStatus(B2bTenantConfig config, String reportKey) {
        String json = withAuth(config, auth -> mapErrors(client(config).get()
                        .uri(b -> b.path(ODATA_REPORTS)
                                .queryParam("$filter", "ReportKey eq '" + reportKey + "'").build())
                        .headers(auth)
                        .retrieve())
                .body(String.class));
        return parseStatus(json, reportKey);
    }

    @Override
    public void deleteReport(B2bTenantConfig config, String reportKey) {
        withAuth(config, auth -> {
            mapErrors(client(config).delete()
                    .uri(DELETE_REPORT, reportKey)
                    .headers(auth)
                    .retrieve())
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public String postMessage(B2bTenantConfig config, String message) {
        return withAuth(config, auth -> mapErrors(client(config).post()
                        .uri(MESSAGE_BOARD)
                        .headers(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(java.util.Map.of("message", message))
                        .retrieve())
                .body(String.class));
    }

    @Override
    public String getLookups(B2bTenantConfig config) {
        return withAuth(config, auth -> mapErrors(client(config).get()
                        .uri(ODATA_LOOKUPS)
                        .headers(auth)
                        .retrieve())
                .body(String.class));
    }

    // ---------- auth + error plumbing ----------

    /**
     * Run {@code op} with auth applied. TOKEN mode: use the cached token, and on a 401 refresh once and
     * retry. BASIC mode: attach HTTP Basic (a 401 is terminal). Transport-level {@link RestClientException}s
     * become {@link B2bTransportException}; the typed B2B errors propagate unchanged.
     */
    private <T> T withAuth(B2bTenantConfig config, java.util.function.Function<Consumer<HttpHeaders>, T> op) {
        if (config.authMode() == B2bAuthMode.BASIC) {
            Consumer<HttpHeaders> basic = basicAuth(config);
            return run(() -> op.apply(basic));
        }
        String token = tokenManager.token(config);
        try {
            return run(() -> op.apply(cookieAuth(token)));
        } catch (B2bAuthException firstAuthFailure) {
            String refreshed = tokenManager.refresh(config);
            return run(() -> op.apply(cookieAuth(refreshed)));
        }
    }

    private static <T> T run(Supplier<T> call) {
        try {
            return call.get();
        } catch (B2bAuthException | B2bValidationException | B2bTransportException e) {
            throw e;
        } catch (RestClientException e) {
            throw new B2bTransportException("goAML B2B transport failure", e);
        }
    }

    private Consumer<HttpHeaders> cookieAuth(String token) {
        return headers -> headers.add(HttpHeaders.COOKIE, AUTH_COOKIE + "=" + token);
    }

    private Consumer<HttpHeaders> basicAuth(B2bTenantConfig config) {
        GoamlCredentials creds = secretsClient.fetch(config.secretsPath());
        return headers -> headers.setBasicAuth(creds.username(), creds.password());
    }

    private RestClient.ResponseSpec mapErrors(RestClient.ResponseSpec spec) {
        return spec
                .onStatus(s -> s.value() == 401, (req, res) -> {
                    throw new B2bAuthException("goAML B2B returned 401 (auth expired/invalid)");
                })
                .onStatus(s -> s.value() == 400, (req, res) -> {
                    throw new B2bValidationException("goAML B2B rejected the request (400)", bodyAsString(res));
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new B2bTransportException(
                            "goAML B2B returned HTTP " + res.getStatusCode().value());
                });
    }

    private RestClient client(B2bTenantConfig config) {
        return restClientBuilder.clone()
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private ReportStatus parseStatus(String json, String reportKey) {
        if (json == null || json.isBlank()) {
            throw new B2bTransportException("goAML returned an empty status body for " + reportKey);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode value = root.path("value");
            JsonNode node = value.isArray() && !value.isEmpty() ? value.get(0) : root;
            String key = node.hasNonNull("ReportKey") ? node.get("ReportKey").asText() : reportKey;
            return new ReportStatus(key, text(node, "Status"), text(node, "Errors"));
        } catch (JsonProcessingException e) {
            throw new B2bTransportException("goAML returned an unparseable status body for " + reportKey, e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        JsonNode v = node.get(field);
        return v.isValueNode() ? v.asText() : v.toString();
    }

    private static String bodyAsString(ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static ByteArrayResource resource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
