package com.vyttah.goaml.controller.integration;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (MockMvc + Testcontainers) for the tenant external-ref add/remove endpoints
 * ({@code POST/DELETE /api/v1/integration/admin/tenants/external-refs}) the AML admin service calls when an ERP
 * workspace is linked/unlinked (goAML ↔ ERP wiring, Phase 1 / W1). The tenant is always the one the caller's
 * {@code org} claim maps to under {@code SCREENING}; a caller can never touch another tenant's refs.
 */
@SpringBootTest(classes = GoamlApplication.class, properties = "goaml.auth.mode=both")
@AutoConfigureMockMvc
@Testcontainers
class IntegrationExternalRefE2ETest {

    private static final String PATH = "/api/v1/integration/admin/tenants/external-refs";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired TenantExternalRefRepository externalRefs;

    private KeyPair keys;
    private Tenant tenantA;
    private String companyA;

    @BeforeEach
    void setUp() {
        trustedServices.deleteAll();
        keys = newKeys();
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "AML admin", pem(keys.getPublic()), false, "ACTIVE"));

        companyA = "cs-" + UUID.randomUUID().toString().substring(0, 8);
        tenantA = provisionTenant(companyA);
        externalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenantA.getId(), SourceSystem.SCREENING, companyA));
    }

    @Test
    void addThenRemove_accountingRefOnTheCallersTenant() throws Exception {
        String erpCompanyId = "1" + UUID.randomUUID().toString().substring(0, 4).replaceAll("[^0-9]", "7");

        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .contentType(APPLICATION_JSON)
                        .content(body("ACCOUNTING", erpCompanyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()))
                .andExpect(jsonPath("$.sourceSystem").value("ACCOUNTING"))
                .andExpect(jsonPath("$.externalOrgRef").value(erpCompanyId));

        List<TenantExternalRef> refs = externalRefs.findAll().stream()
                .filter(r -> r.getTenantId().equals(tenantA.getId())).toList();
        assertThat(refs).hasSize(2);
        assertThat(refs).extracting(TenantExternalRef::getSourceSystem)
                .containsExactlyInAnyOrder(SourceSystem.SCREENING, SourceSystem.ACCOUNTING);
        assertThat(externalRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, erpCompanyId))
                .get().extracting(TenantExternalRef::getTenantId).isEqualTo(tenantA.getId());

        mvc.perform(delete(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .param("sourceSystem", "ACCOUNTING")
                        .param("externalOrgRef", erpCompanyId))
                .andExpect(status().isNoContent());

        assertThat(externalRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, erpCompanyId)).isEmpty();
        assertThat(externalRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.SCREENING, companyA)).isPresent();
    }

    @Test
    void addRefAlreadyMappedToAnotherTenant_is409() throws Exception {
        String companyB = "cs-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenantB = provisionTenant(companyB);
        String erpCompanyId = "2" + UUID.randomUUID().toString().substring(0, 4).replaceAll("[^0-9]", "3");
        externalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenantB.getId(), SourceSystem.ACCOUNTING, erpCompanyId));

        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .contentType(APPLICATION_JSON)
                        .content(body("ACCOUNTING", erpCompanyId)))
                .andExpect(status().isConflict());

        // The ref still belongs to tenant B — nothing was moved or duplicated.
        assertThat(externalRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, erpCompanyId))
                .get().extracting(TenantExternalRef::getTenantId).isEqualTo(tenantB.getId());
    }

    @Test
    void deleteRefOwnedByAnotherTenant_is404AndLeavesIt() throws Exception {
        String companyB = "cs-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenantB = provisionTenant(companyB);
        String erpCompanyId = "4" + UUID.randomUUID().toString().substring(0, 4).replaceAll("[^0-9]", "5");
        externalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenantB.getId(), SourceSystem.ACCOUNTING, erpCompanyId));

        mvc.perform(delete(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .param("sourceSystem", "ACCOUNTING")
                        .param("externalOrgRef", erpCompanyId))
                .andExpect(status().isNotFound());

        assertThat(externalRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, erpCompanyId)).isPresent();
    }

    @Test
    void deleteAbsentRef_is404() throws Exception {
        mvc.perform(delete(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .param("sourceSystem", "ACCOUNTING")
                        .param("externalOrgRef", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingOrg_is400() throws Exception {
        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", null))
                        .contentType(APPLICATION_JSON)
                        .content(body("ACCOUNTING", "1")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", null))
                        .param("sourceSystem", "ACCOUNTING")
                        .param("externalOrgRef", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unmappedOrg_is404() throws Exception {
        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", "cs-never-provisioned"))
                        .contentType(APPLICATION_JSON)
                        .content(body("ACCOUNTING", "1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void callerCannotRemoveItsOwnMapping_is409() throws Exception {
        mvc.perform(delete(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .param("sourceSystem", "SCREENING")
                        .param("externalOrgRef", companyA))
                .andExpect(status().isConflict());
        assertThat(externalRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.SCREENING, companyA)).isPresent();
    }

    @Test
    void invalidSourceSystemOrBlankRef_is400() throws Exception {
        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .contentType(APPLICATION_JSON)
                        .content(body("BANKING", "1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH)
                        .header("X-Service-Assertion", assertion("SCREENING", "aml-admin", companyA))
                        .contentType(APPLICATION_JSON)
                        .content(body("ACCOUNTING", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsignedRequest_is401() throws Exception {
        mvc.perform(post(PATH).contentType(APPLICATION_JSON).content(body("ACCOUNTING", "1")))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private Tenant provisionTenant(String slug) {
        return provisioningService.provision(new TenantProvisioningRequest(
                slug, "Tenant " + slug, "AE", null, null, null, null));
    }

    private String body(String sourceSystem, String externalOrgRef) throws Exception {
        return objectMapper.writeValueAsString(Map.of("sourceSystem", sourceSystem, "externalOrgRef", externalOrgRef));
    }

    private String assertion(String issuer, String sub, String org) {
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(sub)
                .audience().add("goaml").and()
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString());
        if (org != null) {
            builder.claim("org", org);
        }
        return builder.signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static KeyPair newKeys() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
