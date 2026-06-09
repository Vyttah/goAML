package com.vyttah.goaml.controller.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1.5c.3 end-to-end (MockMvc): the AML screening software pushes a customer (server-to-server), then a goAML
 * user browses the screened subject and seeds a DPMSR draft from it — proving the screened parties flow into
 * a real, valid report and appear in the marshalled goAML XML.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ScreeningSeedE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String COMPANY_ID = "601";
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
                "seed-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Seed E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@seed.test", "Sup3rS3cret!", "Seed", "Admin"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/seed/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());

        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "screening", pem(keys.getPublic()), false, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.SCREENING, COMPANY_ID));
    }

    @Test
    void screenedSubjectSeedsAValidDpmsrWithItsParties() throws Exception {
        // 1) screening pushes a legal customer + a legal (corporate) shareholder (server-to-server).
        //    Both map to goAML entity parties, which are fully satisfiable → the seeded report is VALID.
        //    (A natural-person party additionally requires a complete ID document + tax flag that a
        //    screening profile doesn't carry, so person-seeded reports are drafts an analyst completes.)
        mvc.perform(post("/api/v1/integration/screening/subjects")
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON).content("""
                                {
                                  "companyId": "%s", "customerUid": "LEG-9", "subjectType": "LEGAL",
                                  "legal": {"legalName":"Seeded Trading FZE","incorporationNumber":"INC-7",
                                            "incorporationCountry":"AE"},
                                  "shareholders": [
                                    {"partyType":"LEGAL","legalName":"Holdco Ltd","incorporationNumber":"H-1",
                                     "incorporationCountry":"GB","shareholdingPercent":100}
                                  ]
                                }""".formatted(COMPANY_ID)))
                .andExpect(status().isAccepted());

        // 2) a goAML user browses the screened subjects
        String jwt = mlroJwt();
        JsonNode subjects = getJson("/api/v1/screening/subjects", jwt);
        assertThat(subjects).hasSize(1);
        assertThat(subjects.get(0).get("subjectRef").asText()).isEqualTo("SCR-601-LEG-9");

        // 3) seed a DPMSR draft from the subject (parties from screening + goods supplied here)
        JsonNode seeded = postJson("/api/v1/screening/subjects/SCR-601-LEG-9/seed-report", """
                {
                  "entityReference": "SEED-RPT-1",
                  "submissionDate": "2026-06-09T12:00:00Z",
                  "reason": "Screened customer dealing", "action": "Filed",
                  "indicators": ["DPMSJ"],
                  "reportingPerson": {"firstName": "Sara", "lastName": "Khan"},
                  "goods": [{"itemType": "GOLD", "estimatedValue": 90000.00, "currencyCode": "AED"}]
                }""", jwt);
        String reportId = seeded.get("reportId").asText();
        assertThat(seeded.get("status").asText()).isEqualTo("VALID");

        // 4) the screened parties appear in the marshalled goAML XML
        MvcResult xml = mvc.perform(get("/api/v1/reports/" + reportId + "/xml")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk()).andReturn();
        String body = xml.getResponse().getContentAsString();
        assertThat(body)
                .contains("<entity_reference>SEED-RPT-1</entity_reference>")
                .contains("Seeded Trading FZE")   // the screened customer entity
                .contains("Holdco Ltd");          // the screened shareholder entity
    }

    // --- helpers ---

    private String mlroJwt() throws Exception {
        String email = "mlro-" + UUID.randomUUID() + "@seed.test";
        Role role = roleRepository.findByName("MLRO").orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenant.getId(), email,
                passwordEncoder.encode("P@ssw0rd!"), "Mlro", "User", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);

        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"P@ssw0rd!\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode getJson(String path, String jwt) throws Exception {
        MvcResult r = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String body, String jwt) throws Exception {
        MvcResult r = mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(APPLICATION_JSON).content(body))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isLessThan(300);
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private static String assertion() {
        return Jwts.builder()
                .issuer("SCREENING")
                .subject("screening-system")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
