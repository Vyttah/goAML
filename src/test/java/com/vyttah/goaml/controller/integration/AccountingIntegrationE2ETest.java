package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.federated.ConsumedAssertion;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.repository.federated.ConsumedAssertionRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import io.jsonwebtoken.Jwts;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * 1.5b.4 end-to-end (MockMvc): accounting raw-invoice push → reportability verdict → DPMSR draft, idempotency,
 * status pull, and service-assertion auth — over the full filter chain + Testcontainers Postgres.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class AccountingIntegrationE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired ConsumedAssertionRepository consumedAssertions;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final int COMPANY_ID = 777;
    private static KeyPair keys;
    private Tenant tenant;

    @BeforeEach
    void setUp() throws Exception {
        tenantExternalRefs.deleteAll();
        trustedServices.deleteAll();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();

        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "acct-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Acct E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@acct.test", "Sup3rS3cret!", "Acct", "Admin"));
        // rentity config so a built report can validate to VALID.
        jdbcTemplate.update("INSERT INTO public.tenant_goaml_config "
                + "(id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode) "
                + "VALUES (?, ?, 'AE', 3177, 'https://fiu.example/b2b', 'goaml/acct/fiu', 'BASIC')",
                UUID.randomUUID(), tenant.getId());

        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "accounting", pem(keys.getPublic()), false, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.ACCOUNTING, String.valueOf(COMPANY_ID)));
    }

    @Test
    void reportableInvoiceCreatesValidDraftAndIsIdempotent() throws Exception {
        String body = payload(COMPANY_ID, "SAL-1001", "90000", "METAL", "90000");

        JsonNode res = push(body);
        assertThat(res.get("reportable").asBoolean()).isTrue();
        assertThat(res.get("status").asText()).isEqualTo("VALID");
        String reportId = res.get("reportId").asText();
        assertThat(reportId).isNotBlank();
        assertThat(res.get("goamlRef").asText()).isEqualTo("ACC-777-SAL-1001");

        // status pull
        JsonNode pulled = getJson(get("/api/v1/integration/accounting/transactions/SAL-1001")
                .header("X-Service-Assertion", assertion())
                .param("companyId", String.valueOf(COMPANY_ID)));
        assertThat(pulled.get("reportId").asText()).isEqualTo(reportId);

        // idempotent re-push → same report
        JsonNode again = push(body);
        assertThat(again.get("reportId").asText()).isEqualTo(reportId);
    }

    @Test
    void belowThresholdIsNotReportable() throws Exception {
        JsonNode res = push(payload(COMPANY_ID, "SAL-1002", "10000", "METAL", "10000"));
        assertThat(res.get("reportable").asBoolean()).isFalse();
        assertThat(res.get("status").asText()).isEqualTo("NOT_REPORTABLE");
        assertThat(res.get("reportId").isNull()).isTrue();
    }

    @Test
    void watchWithoutPreciousValueIsNotReportable() throws Exception {
        JsonNode res = push(payload(COMPANY_ID, "SAL-1003", "90000", "WATCH", "0"));
        assertThat(res.get("reportable").asBoolean()).isFalse();
    }

    @Test
    void missingServiceAssertionIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/integration/accounting/transactions")
                        .contentType(APPLICATION_JSON)
                        .content(payload(COMPANY_ID, "SAL-1004", "90000", "METAL", "90000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedCompanyIsNotFound() throws Exception {
        // The assertion's org must match the requested company (B11) to reach the tenant-resolution path;
        // company 999 is mapped to no tenant → 404.
        mvc.perform(post("/api/v1/integration/accounting/transactions")
                        .header("X-Service-Assertion", assertionFor("999"))
                        .contentType(APPLICATION_JSON)
                        .content(payload(999, "SAL-1005", "90000", "METAL", "90000")))
                .andExpect(status().isNotFound());
    }

    @Test
    void matchingOrgClaimIsAccepted() throws Exception {
        // B11 — the signed org claim equals the payload companyId → the push goes through.
        mvc.perform(post("/api/v1/integration/accounting/transactions")
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON)
                        .content(payload(COMPANY_ID, "SAL-1006", "90000", "METAL", "90000")))
                .andExpect(status().isAccepted());
    }

    @Test
    void orgClaimMismatchIsRejected() throws Exception {
        // B11 — a valid ACCOUNTING assertion for org 555 cannot touch company 777's data via the payload.
        mvc.perform(post("/api/v1/integration/accounting/transactions")
                        .header("X-Service-Assertion", assertionFor("555"))
                        .contentType(APPLICATION_JSON)
                        .content(payload(COMPANY_ID, "SAL-1007", "90000", "METAL", "90000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrgClaimIsRejected() throws Exception {
        // B11 — an assertion with no org claim cannot access accounting data at all.
        mvc.perform(post("/api/v1/integration/accounting/transactions")
                        .header("X-Service-Assertion", assertionWithoutOrg())
                        .contentType(APPLICATION_JSON)
                        .content(payload(COMPANY_ID, "SAL-1008", "90000", "METAL", "90000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orgClaimMismatchOnStatusAndListIsRejected() throws Exception {
        mvc.perform(get("/api/v1/integration/accounting/transactions/SAL-1001")
                        .header("X-Service-Assertion", assertionFor("555"))
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/integration/accounting/transactions")
                        .header("X-Service-Assertion", assertionFor("555"))
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredConsumedAssertionsArePurgedDuringVerification() throws Exception {
        // The replay store's opportunistic cleanup runs inside the auth filter (no surrounding transaction)
        // — it must actually delete, not silently fail (the @Modifying query needs its own transaction).
        consumedAssertions.save(new ConsumedAssertion("expired-jti-" + UUID.randomUUID(),
                SourceSystem.ACCOUNTING, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)));
        long before = consumedAssertions.count();
        assertThat(before).isGreaterThanOrEqualTo(1);

        mvc.perform(post("/api/v1/integration/accounting/transactions")
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON)
                        .content(payload(COMPANY_ID, "SAL-1009", "90000", "METAL", "90000")))
                .andExpect(status().isAccepted());

        assertThat(consumedAssertions.findAll())
                .as("the expired row was purged; only the just-consumed jti remains")
                .allSatisfy(a -> assertThat(a.getExpiresAt())
                        .isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
    }

    // --- helpers ---

    private JsonNode push(String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/integration/accounting/transactions")
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode getJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb)
            throws Exception {
        MvcResult r = mvc.perform(rb).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private static String payload(int companyId, String docNo, String cash, String commodityType, String metal) {
        return """
                {
                  "companyId": %d,
                  "sourceDocument": {"documentNumber":"%s","documentType":"SAL","documentDate":"2026-06-08",
                                     "direction":"SALE","moduleType":"BULLION"},
                  "transactionCurrency":"AED",
                  "cashSettlement": {"cashAmountBaseCurrency": %s,"baseCurrency":"AED",
                                     "settlementDate":"2026-06-08","cashDocumentNumbers":["REC-1"]},
                  "party": {"category":"CORPORATE","name":"Acme Gold FZE","tradeLicenseNo":"TL-123",
                            "countryOfIncorporation":"AE"},
                  "goods": [ {"commodityType":"%s","commodityCode":"GLD","description":"22K gold bar",
                              "estimatedValue": %s,"currencyCode":"AED","metalAmount": %s,"stoneAmount":0} ]
                }""".formatted(companyId, docNo, cash, commodityType, cash, metal);
    }

    private static String assertion() {
        return assertionFor(String.valueOf(COMPANY_ID));
    }

    /** Mint an assertion whose signed {@code org} claim is {@code org} (B11 cross-check), unique jti per call. */
    private static String assertionFor(String org) {
        return Jwts.builder()
                .issuer("ACCOUNTING")
                .subject("acct-system")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())            // B10 — unique jti (single-use)
                .claim("org", org)
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String assertionWithoutOrg() {
        return Jwts.builder()
                .issuer("ACCOUNTING")
                .subject("acct-system")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
