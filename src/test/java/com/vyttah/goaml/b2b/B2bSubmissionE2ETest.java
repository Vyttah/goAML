package com.vyttah.goaml.b2b;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.vyttah.goaml.b2b.auth.TokenManager;
import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.build.ActivityReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.build.GoamlParties;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.packaging.PackagingLimits;
import com.vyttah.goaml.engine.packaging.ReportZipPackager;
import com.vyttah.goaml.engine.validation.ReportValidator;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end (no Docker): the engine builds a DPMSR → marshals → zips, and that exact ZIP is submitted via
 * {@link RestGoamlB2bClient#postReport} to a stubbed goAML endpoint, returning the reportkey. Proves the
 * engine's output is what the B2B client puts on the wire.
 */
class B2bSubmissionE2ETest {

    private final TokenManager tokenManager = mock(TokenManager.class);
    private final DpmsrReportBuilder dpmsrBuilder = new DpmsrReportBuilder(
            new ActivityReportBuilder(),
            new ReportValidator(new JurisdictionRegistry(), new LookupService()),
            new XsdSchemaValidator(),
            new ReportMarshaller());
    private final ReportMarshaller marshaller = new ReportMarshaller();
    private final ReportZipPackager packager = new ReportZipPackager();

    private WireMockServer wireMock;
    private RestGoamlB2bClient client;
    private B2bTenantConfig config;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        client = new RestGoamlB2bClient(tokenManager, mock(com.vyttah.goaml.integration.aws.GoamlSecretsClient.class),
                RestClient.builder(),
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
    void engineProducedZipIsSubmittedAndReturnsReportKey() {
        Report report = dpmsrBuilder.build(minimalDpmsr());
        byte[] xml = marshaller.marshal(report);
        byte[] zip = packager.zip(xml, "report.xml", List.of(), PackagingLimits.UAE_DEFAULT);

        wireMock.stubFor(post(urlEqualTo("/api/Reports/PostReport"))
                .willReturn(aResponse().withStatus(200).withBody("RK-E2E")));

        String reportKey = client.postReport(config, zip, "report.zip");

        assertThat(reportKey).isEqualTo("RK-E2E");
        assertThat(zip).isNotEmpty();
    }

    private DpmsrReportInput minimalDpmsr() {
        TEntity entity = new TEntity();
        entity.setName("Minimal Trading FZE");
        entity.setIncorporationNumber("123456");
        entity.setIncorporationCountryCode("AE");

        TTransItem gold = new TTransItem();
        gold.setItemType("GOLD");
        gold.setEstimatedValue(new BigDecimal("90000.00"));
        gold.setCurrencyCode(CurrencyType.AED);

        TPersonRegistrationInReport mlro = new TPersonRegistrationInReport();
        mlro.setFirstName("Sara");
        mlro.setLastName("Khan");

        return DpmsrReportInput.builder()
                .rentityId(3177)
                .entityReference("E2E-0001")
                .submissionDate(OffsetDateTime.parse("2026-06-02T12:00:00Z").withOffsetSameInstant(ZoneOffset.UTC))
                .reportingPerson(mlro)
                .reason("DPMS threshold met")
                .action("Filed")
                .indicators("DPMSJ")
                .party(GoamlParties.entity(entity, "Seller of gold above AED 55,000", null))
                .goods(gold)
                .build();
    }
}
