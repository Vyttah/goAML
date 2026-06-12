package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase C.4a end-to-end (MockMvc): the one-shot "File to goAML" filing endpoint — a complete bundle (legal
 * customer party + a precious-metals deal/goods + header) → a VALID DPMSR draft, idempotent on
 * {@code FIL-<companyId>-<filingRef>}, with the reporting person auto-injected from the tenant default (Phase A).
 * Runs over the full filter chain + Testcontainers Postgres; auth is the screening service assertion.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ScreeningFilingE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired TenantGoamlPersonRepository goamlPersons;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String COMPANY_ID = "601";
    private static KeyPair keys;

    @BeforeEach
    void setUp() throws Exception {
        tenantExternalRefs.deleteAll();
        trustedServices.deleteAll();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();

        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "fil-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Filing E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@fil.test", "Sup3rS3cret!", "Fil", "Admin"));
        // rentity config so a built report can validate to VALID (rentity_id must be positive).
        jdbcTemplate.update("INSERT INTO public.tenant_goaml_config "
                + "(id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode) "
                + "VALUES (?, ?, 'AE', 3177, 'https://fiu.example/b2b', 'goaml/fil/fiu', 'BASIC')",
                UUID.randomUUID(), tenant.getId());

        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "screening", pem(keys.getPublic()), false, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.SCREENING, COMPANY_ID));

        // The tenant default reporting person (Phase A) — so the filed report is VALID without the caller
        // sending an MLRO. Lives in the shared public.tenant_goaml_person (keyed by tenant_id).
        TenantGoamlPerson mlro = new TenantGoamlPerson(UUID.randomUUID(), tenant.getId(), "Aisha", "Khan");
        mlro.setActive(true);
        goamlPersons.save(mlro);
    }

    @Test
    void filesAValidDpmsrFromPartiesPlusGoodsAndIsIdempotent() throws Exception {
        JsonNode res = file(legalFilingPayload(COMPANY_ID, "DEAL-1"));
        assertThat(res.get("filingRef").asText()).isEqualTo("FIL-601-DEAL-1");
        assertThat(res.get("status").asText())
                .as("validation messages: %s", res.get("validationMessages"))
                .isEqualTo("VALID");
        String reportId = res.get("reportId").asText();
        assertThat(reportId).isNotBlank();

        // status fetch echoes the same report
        JsonNode got = getJson("/api/v1/integration/screening/filings/DEAL-1");
        assertThat(got.get("reportId").asText()).isEqualTo(reportId);
        assertThat(got.get("status").asText()).isEqualTo("VALID");

        // idempotent re-file: same source deal → same report (no duplicate)
        JsonNode again = file(legalFilingPayload(COMPANY_ID, "DEAL-1"));
        assertThat(again.get("reportId").asText()).isEqualTo(reportId);
    }

    @Test
    void filedReportXmlIsDownloadableByRef() throws Exception {
        file(legalFilingPayload(COMPANY_ID, "DEAL-9"));

        mvc.perform(get("/api/v1/integration/screening/filings/DEAL-9/report.xml")
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_XML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<report>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FIL-601-DEAL-9")));
    }

    @Test
    void downloadingXmlForUnknownFilingIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/integration/screening/filings/NOPE/report.xml")
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", COMPANY_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingAssertionIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/integration/screening/filings")
                        .contentType(APPLICATION_JSON).content(legalFilingPayload(COMPANY_ID, "DEAL-2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedCompanyIsNotFound() throws Exception {
        // The assertion's org must match the requested company (B11) to reach the tenant-resolution path;
        // company 999 is mapped to no tenant → 404.
        mvc.perform(post("/api/v1/integration/screening/filings")
                        .header("X-Service-Assertion", assertionFor("999"))
                        .contentType(APPLICATION_JSON).content(legalFilingPayload("999", "DEAL-3")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fetchingUnknownFilingIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/integration/screening/filings/NOPE")
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", COMPANY_ID))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private JsonNode file(String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/integration/screening/filings")
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path) throws Exception {
        MvcResult r = mvc.perform(get(path)
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", COMPANY_ID))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    /** A legal-customer bundle + one gold deal — an entity party needs only a name, so this seeds a VALID report. */
    private static String legalFilingPayload(String companyId, String filingRef) {
        return """
                {
                  "companyId": "%s",
                  "filingRef": "%s",
                  "subject": {
                    "companyId": "%s", "customerUid": "CUST-1", "subjectType": "LEGAL",
                    "legal": {"legalName":"Demo Gold Trading FZE","incorporationNumber":"INC-1","incorporationCountry":"AE"}
                  },
                  "goods": [
                    {"itemType":"GOLD","description":"1kg gold bar","estimatedValue":60000,"currencyCode":"AED","statusCode":"SOLD"}
                  ],
                  "reason": "Cash purchase of precious metal above the AED 55,000 threshold",
                  "action": "Filed",
                  "indicators": ["ACTRC"]
                }""".formatted(companyId, filingRef, companyId);
    }

    private static String assertion() {
        return assertionFor(COMPANY_ID);
    }

    /** Mint an assertion whose signed {@code org} claim is {@code org} (B11 cross-check), unique jti per call. */
    private static String assertionFor(String org) {
        return Jwts.builder()
                .issuer("SCREENING")
                .subject("screening-system")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())            // B10 — unique jti (single-use)
                .claim("org", org)                           // B11 — signed org must match the companyId
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
