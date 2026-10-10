package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F3 — the lookup passthrough is reachable under <em>two</em> prefixes, each verified against its own trusted
 * service, because {@code IntegrationAuthFilter} picks the signing key from the path:
 * {@code /integration/accounting/lookups/**} → ACCOUNTING (the ERP's goAML filing wizard, via
 * vyttah-accounting), {@code /integration/lookups/**} → SCREENING (the AML cockpit).
 *
 * <p>The assertions that matter are the <strong>negative</strong> ones: each prefix must reject the other
 * issuer's assertion. Without that, "move the proxy under accounting" would quietly widen who can present a
 * SCREENING key.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class IntegrationLookupDualPathE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ACCOUNTING_PATH = "/api/v1/integration/accounting/lookups/ae/legal_forms";
    private static final String SCREENING_PATH = "/api/v1/integration/lookups/ae/legal_forms";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TrustedServiceRepository trustedServices;

    private static KeyPair accountingKeys;
    private static KeyPair screeningKeys;

    @BeforeEach
    void registerBothServices() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        accountingKeys = gen.generateKeyPair();
        screeningKeys = gen.generateKeyPair();

        // Narrow, not deleteAll(): public.trusted_service is shared across every suite in this run
        // (BLOCKERS.md §F0).
        trustedServices.findBySourceSystem(SourceSystem.ACCOUNTING).ifPresent(trustedServices::delete);
        trustedServices.findBySourceSystem(SourceSystem.SCREENING).ifPresent(trustedServices::delete);
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "accounting", pem(accountingKeys.getPublic()), false, "ACTIVE"));
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "screening", pem(screeningKeys.getPublic()), false, "ACTIVE"));
    }

    @AfterEach
    void tearDown() {
        trustedServices.findBySourceSystem(SourceSystem.ACCOUNTING).ifPresent(trustedServices::delete);
        trustedServices.findBySourceSystem(SourceSystem.SCREENING).ifPresent(trustedServices::delete);
    }

    @Test
    void theAccountingPrefixServesTheSameSetAsTheScreeningOne() throws Exception {
        JsonNode viaAccounting = read(ACCOUNTING_PATH, assertion(accountingKeys.getPrivate(), "ACCOUNTING"));
        JsonNode viaScreening = read(SCREENING_PATH, assertion(screeningKeys.getPrivate(), "SCREENING"));

        assertThat(viaAccounting).isNotEmpty();
        assertThat(viaAccounting.get(0).has("code")).isTrue();
        assertThat(viaAccounting.get(0).has("label")).isTrue();
        assertThat(viaAccounting).as("one handler, so both prefixes answer identically").isEqualTo(viaScreening);
    }

    @Test
    void theAccountingPrefixRejectsAScreeningAssertion() throws Exception {
        mvc.perform(get(ACCOUNTING_PATH)
                        .header("X-Service-Assertion", assertion(screeningKeys.getPrivate(), "SCREENING")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theScreeningPrefixRejectsAnAccountingAssertion() throws Exception {
        mvc.perform(get(SCREENING_PATH)
                        .header("X-Service-Assertion", assertion(accountingKeys.getPrivate(), "ACCOUNTING")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bothPrefixesRejectAMissingAssertion() throws Exception {
        mvc.perform(get(ACCOUNTING_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(SCREENING_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownSetOrJurisdictionIsNotFoundUnderTheAccountingPrefix() throws Exception {
        mvc.perform(get("/api/v1/integration/accounting/lookups/ae/not_a_set")
                        .header("X-Service-Assertion", assertion(accountingKeys.getPrivate(), "ACCOUNTING")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/integration/accounting/lookups/zz/legal_forms")
                        .header("X-Service-Assertion", assertion(accountingKeys.getPrivate(), "ACCOUNTING")))
                .andExpect(status().isNotFound());
    }

    @Test
    void everySetTheFilingWizardNeedsIsServedUnderTheAccountingPrefix() throws Exception {
        // The allowlist vyttah-accounting's proxy offers (07-goaml-filing-wizard.md §F3). If goAML ever renames
        // one, this fails here rather than as an empty dropdown in the wizard.
        for (String set : new String[] { "countries", "currencies", "item_types", "item_status",
                "report_indicators", "legal_forms", "party_roles", "funds" }) {
            JsonNode entries = read("/api/v1/integration/accounting/lookups/ae/" + set,
                    assertion(accountingKeys.getPrivate(), "ACCOUNTING"));
            assertThat(entries).as("lookup set '%s'", set).isNotEmpty();
        }
    }

    private JsonNode read(String path, String assertion) throws Exception {
        MvcResult r = mvc.perform(get(path).header("X-Service-Assertion", assertion))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    /** Unique jti per call — assertions are single-use (B10). */
    private static String assertion(PrivateKey key, String issuer) {
        return Jwts.builder()
                .issuer(issuer)
                .subject("erp-user-42")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
