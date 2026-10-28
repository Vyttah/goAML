package com.vyttah.goaml.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.error.B2bValidationException;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.federated.ReportStatusOutbox;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.federated.ReportStatusOutboxRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.scheduler.ReportStatusPublisher;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionExceptions;
import com.vyttah.goaml.service.submission.SubmissionService;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * S2 end-to-end over Testcontainers Postgres: a real submission transition writes a real row into
 * {@code public.report_status_outbox}, and the publisher drains it to a real HTTP endpoint.
 *
 * <p>This is the only test that can prove three things the unit tests cannot, all of which fail at runtime
 * rather than at compile time: that **{@code V10__report_status_outbox.sql} applies** and matches the entity's
 * column names, that the {@code findDue} JPQL and its {@code Limit} parameter are valid, and that the hooks are
 * actually wired into the service that transitions a report. The unit tests own the logic; this owns the wiring.
 *
 * <p>Z1 (step 16, D41): the review gate is gone, so the transitions exercised here are the submission ones —
 * {@code SUBMITTED} on a successful FIU post and {@code REJECTED} when the FIU refuses — with the B2B client
 * mocked, exactly as {@code SubmissionErrorMappingE2ETest} does over HTTP. The same class also proves the
 * Z1 migrations landed (shared {@code V11}, tenant {@code V12}), since both run on this context's start.
 *
 * <p>The fixture follows {@code AccountingReportFilingE2ETest}: narrow set-up and a teardown that puts
 * {@code public} back exactly as it was. The whole suite runs in one JVM against one database, and
 * {@code BLOCKERS.md} §F0 records what a {@code deleteAll()} or a leftover tenant does to other classes here —
 * {@code app_user_tenant_id_fkey} is the one FK to {@code public.tenant} without {@code ON DELETE CASCADE}, so a
 * tenant left behind breaks {@code TenantSchemaMigratorTest}. Hence: delete this class's own rows, its app
 * users, its tenant, then drop its schema.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class ReportStatusOutboxE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final WireMockServer AML = new WireMockServer(options().dynamicPort());

    private static final KeyPair KEYS = generateKeys();

    private static final String ERP_COMPANY_ID = "9341";

    private static final String AML_COMPANY_ID = "acme-aml-s2";

    /** The curated DPMSR shape the other E2Es post; above the DPMS threshold so it validates to VALID. */
    private static final String DPMSR_JSON = """
            {
              "entityReference": "%s",
              "submissionDate": "2026-06-02T12:00:00Z",
              "reason": "DPMS threshold met", "action": "Filed",
              "indicators": ["DPMSJ"],
              "reportingPerson": {"firstName": "Sara", "lastName": "Khan"},
              "parties": [{"reason": "Seller", "entity":
                  {"name": "Minimal Trading FZE", "incorporationNumber": "123456", "incorporationCountryCode": "AE"}}],
              "goods": [{"itemType": "GOLD", "estimatedValue": 90000.00, "currencyCode": "AED"}]
            }""";

    @MockBean GoamlB2bClient b2bClient;

    @Autowired SubmissionService submissionService;
    @Autowired ReportService reportService;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportStatusOutboxRepository outboxRepository;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ReportStatusPublisher publisher;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    private Tenant tenant;

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The outbound integration is off by default (a standalone goAML has no AML platform), so the hooks record
     * nothing unless it is switched on — which makes switching it on part of this test's fixture. The scheduler
     * master switch is left alone; {@link #publisher} is driven directly instead of waiting on a timer.
     */
    @DynamicPropertySource
    static void outbound(DynamicPropertyRegistry registry) {
        AML.start();
        registry.add("goaml.integration.outbound.enabled", () -> true);
        registry.add("goaml.integration.outbound.aml-base-url", AML::baseUrl);
        registry.add("goaml.integration.outbound.private-key-pem", () -> "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(KEYS.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
    }

    @AfterAll
    static void stopWireMock() {
        AML.stop();
    }

    @BeforeEach
    void setUp() {
        AML.resetAll();
        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "s2-outbox-" + UUID.randomUUID().toString().substring(0, 8), "S2 Outbox FZE", "AE",
                "admin-" + UUID.randomUUID() + "@s2outbox.test", "Sup3rS3cret!", "S2", "Admin"));
        // The FIU config the submit path resolves (base URL / secrets path / auth mode); the B2B call itself is
        // mocked. No review_required — the column no longer exists (shared V11).
        jdbcTemplate.update("INSERT INTO public.tenant_goaml_config "
                        + "(id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode) "
                        + "VALUES (?, ?, 'AE', 3177, 'https://fiu.example/b2b', 'goaml/s2/fiu', 'BASIC')",
                UUID.randomUUID(), tenant.getId());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM public.report_status_outbox WHERE tenant_id = ?", tenant.getId());
        jdbcTemplate.update("DELETE FROM public.app_user WHERE tenant_id = ?", tenant.getId());
        jdbcTemplate.update("DELETE FROM public.tenant WHERE id = ?", tenant.getId());
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + tenant.getSchemaName() + "\" CASCADE");
        TenantContext.clear();
    }

    private void linkToErp() {
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.ACCOUNTING, ERP_COMPANY_ID));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.SCREENING, AML_COMPANY_ID));
    }

    /**
     * A VALID report in the tenant's own schema with real, XSD-conformant XML — built through the same
     * {@code ReportService.create} the API uses, because {@code submit} re-validates the stored XML before it
     * will talk to the FIU (a hand-made {@code "{}"} row would be refused as "no stored XML").
     */
    private Report filedReport(String reference) {
        TenantContext.set(tenant.getSchemaName());
        try {
            DpmsrCreateRequest request = objectMapper.readValue(
                    String.format(DPMSR_JSON, reference), DpmsrCreateRequest.class);
            ReportResult result = reportService.create(request, tenant.getId(), UUID.randomUUID());
            assertThat(result.status()).as("fixture must validate to VALID").isEqualTo("VALID");
            return reportRepository.findById(result.reportId()).orElseThrow();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            TenantContext.clear();
        }
    }

    private List<ReportStatusOutbox> outboxFor(Report report) {
        return outboxRepository.findByReportIdOrderByOccurredAtDesc(report.getId());
    }

    private UUID submitAs(Report report, UUID actor) {
        TenantContext.set(tenant.getSchemaName());
        try {
            submissionService.submit(report.getId(), tenant.getId(), actor);
        } finally {
            TenantContext.clear();
        }
        return actor;
    }

    @Test
    @DisplayName("the V10 migration applied and a submission writes a real SUBMITTED row through to the database")
    void submissionTransitionIsRecorded() {
        linkToErp();
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-S2-1");
        Report report = filedReport("ACC-" + ERP_COMPANY_ID + "-REC-2026-000002");
        UUID actor = submitAs(report, UUID.randomUUID());

        List<ReportStatusOutbox> rows = outboxFor(report);
        assertThat(rows).extracting(ReportStatusOutbox::getStatus).containsExactly("SUBMITTED");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getTenantId()).isEqualTo(tenant.getId());
            assertThat(row.getEntityReference()).isEqualTo("ACC-" + ERP_COMPANY_ID + "-REC-2026-000002");
            assertThat(row.getActorUserId()).isEqualTo(actor);
            assertThat(row.getRemark()).isNull();
            assertThat(row.getOccurredAt()).isNotNull();
            assertThat(row.getCreatedAt()).isNotNull();
            assertThat(row.getSentAt()).isNull();
        });
    }

    @Test
    @DisplayName("an FIU refusal is recorded as REJECTED even though the report goes back to VALID for rework")
    void rejectionIsRecordedAsRejectedNotValid() {
        linkToErp();
        when(b2bClient.postReport(any(), any(), any()))
                .thenThrow(new B2bValidationException("FIU rejected", "<error>the buyer ID is missing</error>"));
        Report report = filedReport("ACC-" + ERP_COMPANY_ID + "-REC-2026-000003");

        TenantContext.set(tenant.getSchemaName());
        String reportStatusAfterwards;
        try {
            assertThatThrownBy(() -> submissionService.submit(report.getId(), tenant.getId(), UUID.randomUUID()))
                    .isInstanceOf(SubmissionExceptions.SubmissionRejectedException.class);
            reportStatusAfterwards = reportRepository.findById(report.getId()).orElseThrow().getStatus();
        } finally {
            TenantContext.clear();
        }

        // The distinction this whole design turns on: goAML reopens the report, the ERP is told it was refused.
        assertThat(reportStatusAfterwards).isEqualTo("VALID");
        assertThat(outboxFor(report)).extracting(ReportStatusOutbox::getStatus).containsExactly("REJECTED");
        assertThat(outboxFor(report)).singleElement()
                .satisfies(row -> assertThat(row.getRemark()).isEqualTo("<error>the buyer ID is missing</error>"));
    }

    @Test
    @DisplayName("a tenant the ERP does not file for records nothing, however many transitions it makes")
    void tenantWithoutAnErpRecordsNothing() {
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-S2-OWN");
        Report report = filedReport("GOAML-OWN-0001");

        submitAs(report, UUID.randomUUID());

        assertThat(outboxFor(report)).isEmpty();
    }

    @Test
    @DisplayName("the publisher's findDue query and Limit work against the real table, and mark the row sent")
    void publisherDrainsTheRealTable() {
        linkToErp();
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-S2-4");
        Report report = filedReport("ACC-" + ERP_COMPANY_ID + "-REC-2026-000004");
        AML.stubFor(post(urlEqualTo("/api/v1/integration/goaml/report-status"))
                .willReturn(aResponse().withStatus(200)));

        submitAs(report, UUID.randomUUID());
        assertThat(outboxFor(report)).hasSize(1);

        int delivered = publisher.publishDue();

        assertThat(delivered).isGreaterThanOrEqualTo(1);
        assertThat(AML.findAll(postRequestedFor(urlEqualTo("/api/v1/integration/goaml/report-status"))))
                .isNotEmpty();
        assertThat(outboxFor(report)).singleElement().satisfies(row -> {
            assertThat(row.getSentAt()).isNotNull();
            assertThat(row.getLastError()).isNull();
        });

        // Delivered rows are not reconsidered — the partial index and the sentAt predicate agree.
        assertThat(publisher.publishDue()).isZero();
    }

    @Test
    @DisplayName("Z1: shared V11 dropped review_required and tenant V12 dropped the reviewer columns + the two statuses")
    void reviewGateMigrationsApplied() {
        Integer sharedReviewColumns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'tenant_goaml_config' "
                        + "AND column_name = 'review_required'", Integer.class);
        assertThat(sharedReviewColumns).isZero();

        Integer tenantReviewColumns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = 'report' "
                        + "AND column_name IN ('reviewed_by', 'reviewed_at', 'review_remark')",
                Integer.class, tenant.getSchemaName());
        assertThat(tenantReviewColumns).isZero();

        // The rewritten CHECK refuses the two dead values but still admits the submit claim.
        Report report = filedReport("GOAML-CHK-0001");
        String table = "\"" + tenant.getSchemaName() + "\".report";
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE " + table + " SET status = 'PENDING_REVIEW' WHERE id = ?", report.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE " + table + " SET status = 'APPROVED' WHERE id = ?", report.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.update(
                "UPDATE " + table + " SET status = 'SUBMITTING' WHERE id = ?", report.getId())).isEqualTo(1);

        // And the notification vocabulary no longer admits the awaiting-review ping.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO \"" + tenant.getSchemaName() + "\".notification "
                        + "(id, recipient_user_id, type, report_id, title, body, created_at) "
                        + "VALUES (?, ?, 'REPORT_PENDING_REVIEW', ?, 't', 'b', now())",
                UUID.randomUUID(), UUID.randomUUID(), report.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
