package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.ExternalIdentity;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.ExternalIdentityRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
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
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1.5b.3 end-to-end (MockMvc): the <b>embedded-consumer</b> flow (Model 1). An external app (accounting / the
 * AML software) authenticates its user via the federated token exchange (1.5a) and then drives the existing
 * {@code /api/v1/reports*} API with that goAML JWT — create → validate → list → get → xml → submit → status —
 * exactly as a native user would. Proves goAML is the single system-of-record for an embedded client and that
 * <b>MLRO submit-gating is preserved for federated users</b> (a JIT-provisioned ANALYST cannot submit).
 *
 * <p>The B2B client is mocked (its real path is covered by the b2b + Phase 7 tests); this asserts the
 * web → service → persistence wiring under a federated identity, with {@code goaml.auth.mode=both}.
 */
@SpringBootTest(classes = GoamlApplication.class, properties = "goaml.auth.mode=both")
@AutoConfigureMockMvc
@Testcontainers
class EmbeddedConsumerE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean GoamlB2bClient b2bClient;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository appUsers;
    @Autowired RoleRepository roles;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired ExternalIdentityRepository externalIdentities;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired JdbcTemplate jdbcTemplate;

    private static KeyPair keys;
    private Tenant tenant;

    private static final String DPMSR_JSON = """
            {
              "entityReference": "%s",
              "submissionDate": "2026-06-09T12:00:00Z",
              "reason": "DPMS threshold met", "action": "Filed",
              "indicators": ["DPMSJ"],
              "reportingPerson": {"firstName": "Sara", "lastName": "Khan"},
              "parties": [{"reason": "Seller", "entity":
                  {"name": "Minimal Trading FZE", "incorporationNumber": "123456", "incorporationCountryCode": "AE"}}],
              "goods": [{"itemType": "GOLD", "estimatedValue": 90000.00, "currencyCode": "AED"}]
            }""";

    @BeforeEach
    void setUp() throws Exception {
        externalIdentities.deleteAll();
        tenantExternalRefs.deleteAll();
        trustedServices.deleteAll();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();

        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "emb-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Embedded E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@emb.test", "Sup3rS3cret!", "Emb", "Admin"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/emb/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());

        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "accounting", pem(keys.getPublic()), true, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.ACCOUNTING, "ORG-1"));
    }

    @Test
    void federatedMlroDrivesFullReportLifecycle() throws Exception {
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-EMB");
        when(b2bClient.getReportStatus(any(), any())).thenReturn(new ReportStatus("RK-EMB", "Accepted", null));

        // an existing MLRO in the tenant, mapped to the external accounting user
        AppUser mlro = saveUser("MLRO");
        externalIdentities.save(new ExternalIdentity(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "ext-mlro", mlro.getEmail(), mlro.getId()));
        String jwt = exchange("ext-mlro", mlro.getEmail());

        // create + validate
        JsonNode created = postJson("/api/v1/reports", String.format(DPMSR_JSON, "EMB-1"), jwt);
        assertThat(created.get("status").asText()).isEqualTo("VALID");
        String reportId = created.get("reportId").asText();

        // list + get
        assertThat(getString("/api/v1/reports", jwt)).contains("EMB-1");
        JsonNode byId = getJson("/api/v1/reports/" + reportId, jwt);
        assertThat(byId.get("entityReference").asText()).isEqualTo("EMB-1");

        // xml
        MvcResult xml = mvc.perform(get("/api/v1/reports/" + reportId + "/xml")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(xml.getResponse().getContentType()).startsWith(APPLICATION_XML_VALUE);
        assertThat(xml.getResponse().getContentAsString())
                .contains("<entity_reference>EMB-1</entity_reference>")
                .contains("<rentity_id>3177</rentity_id>");

        // submit (MLRO) → reportkey, then status refresh
        JsonNode submitted = postJson("/api/v1/reports/" + reportId + "/submit", "", jwt);
        assertThat(submitted.get("reportKey").asText()).isEqualTo("RK-EMB");
        assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");

        JsonNode statusNode = getJson("/api/v1/reports/" + reportId + "/status", jwt);
        assertThat(statusNode.get("status").asText()).isEqualTo("Accepted");
    }

    @Test
    void federatedAnalystCanCreateButCannotSubmit() throws Exception {
        // JIT-provisioned (unknown external user) → defaults to ANALYST
        String jwt = exchange("ext-analyst", "analyst@emb.test");

        JsonNode created = postJson("/api/v1/reports", String.format(DPMSR_JSON, "EMB-2"), jwt);
        assertThat(created.get("status").asText()).isEqualTo("VALID");
        String reportId = created.get("reportId").asText();

        mvc.perform(post("/api/v1/reports/" + reportId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(APPLICATION_JSON).content(""))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private AppUser saveUser(String roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenant.getId(),
                roleName.toLowerCase() + "-" + UUID.randomUUID() + "@emb.test", "x", roleName, "User", "ACTIVE");
        u.addRole(role);
        return appUsers.save(u);
    }

    private String exchange(String externalUserId, String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/federated/token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"sourceSystem\":\"ACCOUNTING\",\"assertion\":\""
                                + assertion(externalUserId, email) + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode postJson(String path, String body, String jwt) throws Exception {
        MvcResult res = mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(APPLICATION_JSON).content(body))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isLessThan(300);
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, String jwt) throws Exception {
        return objectMapper.readTree(getString(path, jwt));
    }

    private String getString(String path, String jwt) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String assertion(String sub, String email) {
        return Jwts.builder()
                .issuer("ACCOUNTING")
                .subject(sub)
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .claim("email", email)
                .claim("org", "ORG-1")
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
