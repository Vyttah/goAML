package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F0 end-to-end (MockMvc): the ERP's goAML filing wizard posts a fully-assembled DPMSR to
 * {@code POST /api/v1/integration/accounting/reports} under the ACCOUNTING assertion → a VALID report in the
 * mapped tenant. Covers the DoD ("the exact JSON the wizard will send creates a VALID report"), idempotency
 * on {@code entityReference}, an INVALID filing with messages, the status pull, and the B11 org guard — over
 * the full filter chain + Testcontainers Postgres.
 *
 * <p>The fixture {@code samples/erp-wizard-wire-payload.json} is the wire shape produced by
 * {@code dpmsrPayload.ts} — the module {@code 07-goaml-filing-wizard.md} has the wizard copy verbatim — so it
 * carries that module's {@code reportDate} spelling of {@code submission_date} on purpose.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class AccountingReportFilingE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final int COMPANY_ID = 881;
    private static final String PATH = "/api/v1/integration/accounting/reports";
    private static KeyPair keys;
    private Tenant tenant;

    /**
     * Narrow, not {@code deleteAll()}: `public.trusted_service` and `public.tenant_external_ref` are shared
     * across every suite in this single-JVM run, and wiping them (which the older
     * {@code AccountingIntegrationE2ETest} does) makes other suites' expectations depend on class ordering.
     * This class touches only the ACCOUNTING row and its own company's ref, and {@link #tearDown()} removes
     * everything it inserted — including the `tenant_goaml_*` rows, whose FKs to `tenant` would otherwise
     * block {@code TenantSchemaMigratorTest}'s tenant deletes.
     */
    @BeforeEach
    void setUp() throws Exception {
        tenantExternalRefs.findBySourceSystemAndExternalOrgRef(
                SourceSystem.ACCOUNTING, String.valueOf(COMPANY_ID)).ifPresent(tenantExternalRefs::delete);
        trustedServices.findBySourceSystem(SourceSystem.ACCOUNTING).ifPresent(trustedServices::delete);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();

        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "erp-file-" + UUID.randomUUID().toString().substring(0, 8), "ERP Filing FZE", "AE",
                "admin-" + UUID.randomUUID() + "@erpfile.test", "Sup3rS3cret!", "Erp", "Admin"));
        // rentity config so a built report can validate to VALID.
        jdbcTemplate.update("INSERT INTO public.tenant_goaml_config "
                        + "(id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode) "
                        + "VALUES (?, ?, 'AE', 3177, 'https://fiu.example/b2b', 'goaml/erp/fiu', 'BASIC')",
                UUID.randomUUID(), tenant.getId());

        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "accounting", pem(keys.getPublic()), false, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.ACCOUNTING, String.valueOf(COMPANY_ID)));
    }

    /**
     * Give the tenant provisioned above back, so this class leaves {@code public} as it found it. The one FK
     * to {@code public.tenant} without {@code ON DELETE CASCADE} is {@code app_user_tenant_id_fkey}
     * ({@code V2__shared_core.sql:55}) — a provisioned tenant keeps its admin user, and a leftover tenant with
     * a user makes {@code TenantSchemaMigratorTest}'s {@code tenantRepository.deleteAll()} fail with exactly
     * that constraint. So the app users go first, then the tenant (which cascades `tenant_goaml_config`,
     * `tenant_goaml_person` and `tenant_external_ref` away), then the tenant's own schema.
     */
    @AfterEach
    void tearDown() {
        trustedServices.findBySourceSystem(SourceSystem.ACCOUNTING).ifPresent(trustedServices::delete);
        jdbcTemplate.update("DELETE FROM public.app_user WHERE tenant_id = ?", tenant.getId());
        jdbcTemplate.update("DELETE FROM public.tenant WHERE id = ?", tenant.getId());
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + tenant.getSchemaName() + "\" CASCADE");
    }

    @Test
    void wizardPayloadCreatesAValidReport() throws Exception {
        String ref = "ACC-" + COMPANY_ID + "-REC-0001";

        MvcResult r = mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON)
                        .content(request(COMPANY_ID, ref)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode res = objectMapper.readTree(r.getResponse().getContentAsString());

        assertThat(res.get("entityReference").asText()).isEqualTo(ref);
        assertThat(res.get("status").asText()).isEqualTo("VALID");
        assertThat(res.get("duplicate").asBoolean()).isFalse();
        assertThat(res.get("reportId").asText()).isNotBlank();
    }

    @Test
    void theReportDateSpellingBindsOntoSubmissionDate() throws Exception {
        // dpmsrPayload.ts emits `reportDate`; the contract's field is `submissionDate`. The alias is what
        // makes the wizard's verbatim payload file at all — without it submissionDate is null and @NotNull
        // answers 400 before any tenant work. Proven by reading it back off the persisted input.
        String ref = "ACC-" + COMPANY_ID + "-REC-0002";
        JsonNode res = fileOk(ref);

        String input = jdbcTemplate.queryForObject(
                "SELECT input::text FROM " + tenant.getSchemaName() + ".report WHERE id = ?",
                String.class, UUID.fromString(res.get("reportId").asText()));
        JsonNode stored = objectMapper.readTree(input);
        assertThat(stored.get("submissionDate").asText()).startsWith("2026-10-07");
    }

    @Test
    void sameEntityReferenceReturnsTheExistingReport() throws Exception {
        String ref = "ACC-" + COMPANY_ID + "-REC-0003";
        JsonNode first = fileOk(ref);

        // A retry (accounting's call timed out, the operator pressed File twice) must not create a second
        // report, and must not surface the create path's 409 either.
        MvcResult again = mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON)
                        .content(request(COMPANY_ID, ref)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode second = objectMapper.readTree(again.getResponse().getContentAsString());

        assertThat(second.get("reportId").asText()).isEqualTo(first.get("reportId").asText());
        assertThat(second.get("duplicate").asBoolean()).isTrue();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tenant.getSchemaName() + ".report WHERE entity_reference = ?",
                Integer.class, ref);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void anIncompletePayloadIsFiledAsInvalidWithMessages() throws Exception {
        // No goods and no parties: schema-valid JSON, but the engine's mandatory rules fail → the filing
        // succeeds (the report exists for the operator to fix) with status INVALID and messages.
        String ref = "ACC-" + COMPANY_ID + "-REC-0004";
        String body = """
                {"companyId": %d, "payload": {"entityReference":"%s","reportDate":"2026-10-07T00:00:00Z",
                 "parties":[], "goods":[]}}""".formatted(COMPANY_ID, ref);

        MvcResult r = mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode res = objectMapper.readTree(r.getResponse().getContentAsString());

        assertThat(res.get("status").asText()).isEqualTo("INVALID");
        assertThat(res.get("validationMessages")).isNotEmpty();
        assertThat(res.get("reportId").asText()).isNotBlank();
    }

    @Test
    void statusPullReturnsTheFiledReport() throws Exception {
        String ref = "ACC-" + COMPANY_ID + "-REC-0005";
        JsonNode filed = fileOk(ref);

        MvcResult r = mvc.perform(get(PATH + "/{ref}", ref)
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode res = objectMapper.readTree(r.getResponse().getContentAsString());

        assertThat(res.get("reportId").asText()).isEqualTo(filed.get("reportId").asText());
        assertThat(res.get("status").asText()).isEqualTo("VALID");
    }

    @Test
    void statusOfAnUnknownReferenceIsNotFound() throws Exception {
        mvc.perform(get(PATH + "/{ref}", "ACC-" + COMPANY_ID + "-NOPE")
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theConfiguredGoamlPersonBecomesTheReportingPerson() throws Exception {
        // The wizard never sends a reporting person — goAML fills the tenant's active tenant_goaml_person.
        jdbcTemplate.update("INSERT INTO public.tenant_goaml_person "
                        + "(id, tenant_id, first_name, last_name, gender, id_number, nationality, occupation,"
                        + " is_active, created_at, updated_at) "
                        + "VALUES (?, ?, 'Nadia', 'Khan', 'F', '784197000000002', 'AE', 'MLRO', true, now(), now())",
                UUID.randomUUID(), tenant.getId());

        String ref = "ACC-" + COMPANY_ID + "-REC-0006";
        JsonNode res = fileOk(ref);

        String input = jdbcTemplate.queryForObject(
                "SELECT input::text FROM " + tenant.getSchemaName() + ".report WHERE id = ?",
                String.class, UUID.fromString(res.get("reportId").asText()));
        JsonNode person = objectMapper.readTree(input).get("reportingPerson");
        assertThat(person.get("firstName").asText()).isEqualTo("Nadia");
        assertThat(person.get("lastName").asText()).isEqualTo("Khan");
    }

    @Test
    void theFilingIsAuditedWithTheErpActor() throws Exception {
        String ref = "ACC-" + COMPANY_ID + "-REC-0009";
        fileOk(ref);

        String summary = jdbcTemplate.queryForObject(
                "SELECT summary FROM " + tenant.getSchemaName()
                        + ".audit_log WHERE action = 'REPORT.FILED_BY_ERP' AND actor_email = 'erp-user-42'",
                String.class);
        assertThat(summary).contains(ref).contains("status=VALID");
    }

    @Test
    void missingServiceAssertionIsUnauthorized() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(APPLICATION_JSON)
                        .content(request(COMPANY_ID, "ACC-" + COMPANY_ID + "-REC-0007")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orgClaimMismatchIsRejected() throws Exception {
        // B11 — a valid ACCOUNTING assertion for org 555 cannot file into company 881's tenant.
        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertionFor("555"))
                        .contentType(APPLICATION_JSON)
                        .content(request(COMPANY_ID, "ACC-" + COMPANY_ID + "-REC-0008")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/{ref}", "ACC-" + COMPANY_ID + "-REC-0001")
                        .header("X-Service-Assertion", assertionFor("555"))
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedCompanyIsNotFound() throws Exception {
        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertionFor("999"))
                        .contentType(APPLICATION_JSON)
                        .content(request(999, "ACC-999-REC-0001")))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private JsonNode fileOk(String ref) throws Exception {
        MvcResult r = mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON)
                        .content(request(COMPANY_ID, ref)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    /** The wizard's wire payload, wrapped in the F0 request record. */
    private static String request(int companyId, String entityReference) throws Exception {
        return "{\"companyId\": " + companyId + ", \"payload\": "
                + wizardPayload(entityReference) + "}";
    }

    private static String wizardPayload(String entityReference) throws Exception {
        try (InputStream in = AccountingReportFilingE2ETest.class.getClassLoader()
                .getResourceAsStream("samples/erp-wizard-wire-payload.json")) {
            assertThat(in).as("samples/erp-wizard-wire-payload.json on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__REF__", entityReference);
        }
    }

    private static String assertion() {
        return assertionFor(String.valueOf(COMPANY_ID));
    }

    /** Mint an ACCOUNTING assertion whose signed {@code org} claim is {@code org}; unique jti per call. */
    private static String assertionFor(String org) {
        return Jwts.builder()
                .issuer("ACCOUNTING")
                .subject("erp-user-42")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())            // B10 — unique jti (single-use)
                .claim("org", org)
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
