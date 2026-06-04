package com.vyttah.goaml.b2b;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.vyttah.goaml.b2b.auth.TokenManager;
import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.b2b.error.B2bValidationException;
import com.vyttah.goaml.integration.aws.GoamlCredentials;
import com.vyttah.goaml.integration.aws.GoamlSecretsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link RestGoamlB2bClient}: {@link TokenManager} + {@link GoamlSecretsClient} are
 * mocked and the goAML B2B endpoint is an in-process WireMock server. Covers every operation and outcome
 * (reportkey parse, 400→validation+body, 401→refresh+retry-once, 401-after-retry, transport, status parse
 * variants, empty/unparseable status, delete/message/lookups, and the BASIC-auth path).
 */
class RestGoamlB2bClientTest {

    private static final String POST_REPORT = "/api/Reports/PostReport";
    private static final String ODATA_REPORTS = "/odata/Odata/OdataReports";
    private static final byte[] ZIP = "PK-zip-bytes".getBytes(StandardCharsets.UTF_8);

    private final TokenManager tokenManager = mock(TokenManager.class);
    private final GoamlSecretsClient secrets = mock(GoamlSecretsClient.class);

    private WireMockServer wireMock;
    private RestGoamlB2bClient client;
    private B2bTenantConfig config;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        client = new RestGoamlB2bClient(tokenManager, secrets, RestClient.builder(),
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()),
                new ObjectMapper());
        config = new B2bTenantConfig("t1", wireMock.baseUrl(), "goaml/t1", B2bAuthMode.TOKEN);
        when(tokenManager.token(config)).thenReturn("tok");
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void postReportReturnsTrimmedReportKey() {
        wireMock.stubFor(post(urlEqualTo(POST_REPORT))
                .willReturn(aResponse().withStatus(200).withBody("RK-123\n")));

        assertThat(client.postReport(config, ZIP, "report.zip")).isEqualTo("RK-123");
    }

    @Test
    void postReportValidationErrorCarriesBody() {
        wireMock.stubFor(post(urlEqualTo(POST_REPORT))
                .willReturn(aResponse().withStatus(400).withBody("schema error at line 3")));

        Throwable t = catchThrowable(() -> client.postReport(config, ZIP, "report.zip"));

        assertThat(t).isInstanceOf(B2bValidationException.class);
        assertThat(((B2bValidationException) t).responseBody()).contains("schema error at line 3");
    }

    @Test
    void postReportRefreshesAndRetriesOnce401() {
        when(tokenManager.refresh(config)).thenReturn("tok2");
        wireMock.stubFor(post(urlEqualTo(POST_REPORT)).inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("authed"));
        wireMock.stubFor(post(urlEqualTo(POST_REPORT)).inScenario("retry")
                .whenScenarioStateIs("authed")
                .willReturn(aResponse().withStatus(200).withBody("RK-9")));

        assertThat(client.postReport(config, ZIP, "report.zip")).isEqualTo("RK-9");
        verify(tokenManager).refresh(config);
    }

    @Test
    void postReportAuthFailsAfterRetry() {
        when(tokenManager.refresh(config)).thenReturn("tok2");
        wireMock.stubFor(post(urlEqualTo(POST_REPORT)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.postReport(config, ZIP, "report.zip"))
                .isInstanceOf(B2bAuthException.class);
    }

    @Test
    void postReportTransportError() {
        wireMock.stubFor(post(urlEqualTo(POST_REPORT)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.postReport(config, ZIP, "report.zip"))
                .isInstanceOf(B2bTransportException.class);
    }

    @Test
    void getReportStatusParsesODataArray() {
        wireMock.stubFor(get(urlPathEqualTo(ODATA_REPORTS)).willReturn(aResponse().withStatus(200)
                .withBody("{\"value\":[{\"ReportKey\":\"RK-1\",\"Status\":\"Accepted\",\"Errors\":null}]}")));

        ReportStatus status = client.getReportStatus(config, "RK-1");

        assertThat(status.reportKey()).isEqualTo("RK-1");
        assertThat(status.status()).isEqualTo("Accepted");
        assertThat(status.errors()).isNull();
    }

    @Test
    void getReportStatusFallsBackToRootAndStringifiesErrors() {
        wireMock.stubFor(get(urlPathEqualTo(ODATA_REPORTS)).willReturn(aResponse().withStatus(200)
                .withBody("{\"Status\":\"Rejected\",\"Errors\":[\"e1\",\"e2\"]}")));

        ReportStatus status = client.getReportStatus(config, "RK-7");

        assertThat(status.reportKey()).isEqualTo("RK-7"); // no ReportKey in body → falls back to argument
        assertThat(status.status()).isEqualTo("Rejected");
        assertThat(status.errors()).contains("e1").contains("e2");
    }

    @Test
    void getReportStatusEmptyBodyThrows() {
        wireMock.stubFor(get(urlPathEqualTo(ODATA_REPORTS)).willReturn(aResponse().withStatus(200).withBody("")));

        assertThatThrownBy(() -> client.getReportStatus(config, "RK-1"))
                .isInstanceOf(B2bTransportException.class);
    }

    @Test
    void getReportStatusUnparseableThrows() {
        wireMock.stubFor(get(urlPathEqualTo(ODATA_REPORTS))
                .willReturn(aResponse().withStatus(200).withBody("<<not json>>")));

        assertThatThrownBy(() -> client.getReportStatus(config, "RK-1"))
                .isInstanceOf(B2bTransportException.class);
    }

    @Test
    void deleteReportSucceeds() {
        wireMock.stubFor(delete(urlEqualTo("/api/Reports/RK-1")).willReturn(aResponse().withStatus(200)));

        client.deleteReport(config, "RK-1");

        wireMock.verify(com.github.tomakehurst.wiremock.client.WireMock.exactly(1),
                com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor(urlEqualTo("/api/Reports/RK-1")));
    }

    @Test
    void postMessageReturnsBody() {
        wireMock.stubFor(post(urlEqualTo("/api/MessageBoard"))
                .willReturn(aResponse().withStatus(200).withBody("queued")));

        assertThat(client.postMessage(config, "hello FIU")).isEqualTo("queued");
    }

    @Test
    void getLookupsReturnsBody() {
        wireMock.stubFor(get(urlEqualTo("/odata/Odata/OdataLookups"))
                .willReturn(aResponse().withStatus(200).withBody("[{\"code\":\"AED\"}]")));

        assertThat(client.getLookups(config)).contains("AED");
    }

    @Test
    void basicAuthModeUsesCredentialsNotTokenManager() {
        B2bTenantConfig basic = new B2bTenantConfig("t1", wireMock.baseUrl(), "goaml/t1", B2bAuthMode.BASIC);
        when(secrets.fetch("goaml/t1")).thenReturn(new GoamlCredentials("u", "p", null));
        wireMock.stubFor(post(urlEqualTo(POST_REPORT)).willReturn(aResponse().withStatus(200).withBody("RK-B")));

        assertThat(client.postReport(basic, ZIP, "report.zip")).isEqualTo("RK-B");
        verify(tokenManager, never()).token(any());
        verify(tokenManager, never()).refresh(any());
    }
}
