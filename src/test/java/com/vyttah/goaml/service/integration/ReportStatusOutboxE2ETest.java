package com.vyttah.goaml.service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
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
import com.vyttah.goaml.service.report.ReportReviewService;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

/**
 * S2 end-to-end over Testcontainers Postgres: a real review transition writes a real row into
 * {@code public.report_status_outbox}, and the publisher drains it to a real HTTP endpoint.
 *
 * <p>This is the only test that can prove three things the unit tests cannot, all of which fail at runtime
 * rather than at compile time: that **{@code V10__report_status_outbox.sql} applies** and matches the entity's
 * column names, that the {@code findDue} JPQL and its {@code Limit} parameter are valid, and that the hooks are
 * actually wired into the services that transition a report. The unit tests own the logic; this owns the wiring.
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

    @Autowired ReportReviewService reviewService;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportStatusOutboxRepository outboxRepository;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ReportStatusPublisher publisher;
    @Autowired JdbcTemplate jdbcTemplate;

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
        // review_required so the VALID → PENDING_REVIEW → APPROVED path is open.
        jdbcTemplate.update("INSERT INTO public.tenant_goaml_config "
                        + "(id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode, "
                        + "review_required) "
                        + "VALUES (?, ?, 'AE', 3177, 'https://fiu.example/b2b', 'goaml/s2/fiu', 'BASIC', true)",
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

    /** A VALID report in the tenant's own schema, as if the ERP had just filed it. */
    private Report filedReport(String reference) {
        TenantContext.set(tenant.getSchemaName());
        try {
            return reportRepository.save(new Report(UUID.randomUUID(), reference, "DPMSR", 3177, "VALID", "{}",
                    UUID.randomUUID()));
        } finally {
            TenantContext.clear();
        }
    }

    private List<ReportStatusOutbox> outboxFor(Report report) {
        return outboxRepository.findByReportIdOrderByOccurredAtDesc(report.getId());
    }

    @Test
    @DisplayName("the V10 migration applied and the review transitions write real rows through to the database")
    void reviewTransitionsAreRecorded() {
        linkToErp();
        Report report = filedReport("ACC-" + ERP_COMPANY_ID + "-REC-2026-000002");
        UUID actor = UUID.randomUUID();

        TenantContext.set(tenant.getSchemaName());
        try {
            reviewService.submitForReview(report.getId(), tenant.getId(), actor, "please review");
            reviewService.approve(report.getId(), tenant.getId(), actor, "looks fine");
        } finally {
            TenantContext.clear();
        }

        List<ReportStatusOutbox> rows = outboxFor(report);
        assertThat(rows).extracting(ReportStatusOutbox::getStatus)
                .containsExactlyInAnyOrder("PENDING_REVIEW", "APPROVED");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getTenantId()).isEqualTo(tenant.getId());
            assertThat(row.getEntityReference()).isEqualTo("ACC-" + ERP_COMPANY_ID + "-REC-2026-000002");
            assertThat(row.getActorUserId()).isEqualTo(actor);
            assertThat(row.getOccurredAt()).isNotNull();
            assertThat(row.getCreatedAt()).isNotNull();
            assertThat(row.getSentAt()).isNull();
        });
    }

    @Test
    @DisplayName("a rejection is recorded as REJECTED even though the report goes back to VALID for rework")
    void rejectionIsRecordedAsRejectedNotValid() {
        linkToErp();
        Report report = filedReport("ACC-" + ERP_COMPANY_ID + "-REC-2026-000003");

        TenantContext.set(tenant.getSchemaName());
        String reportStatusAfterwards;
        try {
            reviewService.submitForReview(report.getId(), tenant.getId(), UUID.randomUUID(), null);
            reviewService.reject(report.getId(), tenant.getId(), UUID.randomUUID(), "the buyer ID is missing");
            reportStatusAfterwards = reportRepository.findById(report.getId()).orElseThrow().getStatus();
        } finally {
            TenantContext.clear();
        }

        // The distinction this whole design turns on: goAML reopens the report, the ERP is told it was refused.
        assertThat(reportStatusAfterwards).isEqualTo("VALID");
        assertThat(outboxFor(report)).extracting(ReportStatusOutbox::getStatus).contains("REJECTED");
        assertThat(outboxFor(report)).filteredOn(r -> "REJECTED".equals(r.getStatus()))
                .singleElement()
                .satisfies(row -> assertThat(row.getRemark()).isEqualTo("the buyer ID is missing"));
    }

    @Test
    @DisplayName("a tenant the ERP does not file for records nothing, however many transitions it makes")
    void tenantWithoutAnErpRecordsNothing() {
        Report report = filedReport("GOAML-OWN-0001");

        TenantContext.set(tenant.getSchemaName());
        try {
            reviewService.submitForReview(report.getId(), tenant.getId(), UUID.randomUUID(), null);
            reviewService.approve(report.getId(), tenant.getId(), UUID.randomUUID(), null);
        } finally {
            TenantContext.clear();
        }

        assertThat(outboxFor(report)).isEmpty();
    }

    @Test
    @DisplayName("the publisher's findDue query and Limit work against the real table, and mark the row sent")
    void publisherDrainsTheRealTable() {
        linkToErp();
        Report report = filedReport("ACC-" + ERP_COMPANY_ID + "-REC-2026-000004");
        AML.stubFor(post(urlEqualTo("/api/v1/integration/goaml/report-status"))
                .willReturn(aResponse().withStatus(200)));

        TenantContext.set(tenant.getSchemaName());
        try {
            reviewService.submitForReview(report.getId(), tenant.getId(), UUID.randomUUID(), "over to you");
        } finally {
            TenantContext.clear();
        }
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
}
