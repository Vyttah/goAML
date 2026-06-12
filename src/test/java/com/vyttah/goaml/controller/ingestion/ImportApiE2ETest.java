package com.vyttah.goaml.controller.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 11.4 end-to-end (MockMvc): import a DPMSR CSV (one good + one bad row) → a PARTIAL job + one created
 * report; import the golden goAML XML → a COMPLETED job + one report; an empty upload → 400. Driven through
 * MockMvc (in-process — no embedded-socket flakiness) so it deterministically exercises the full filter
 * chain (JwtAuthFilter + tenant routing) → service → importer → persistence + RBAC over Testcontainers.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ImportApiE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String CSV_HEADER = "entity_reference,submission_date,indicators,"
            + "reporting_person_first_name,reporting_person_last_name,party_type,party_reason,"
            + "entity_name,entity_incorporation_number,entity_incorporation_country,"
            + "good_item_type,good_estimated_value,good_currency_code,good_status_code";

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "imp-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Import E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "Adm", "In"));
        // rentity_id 101 matches golden/DPMSR.xml — the importer rejects files whose rentity_id differs
        // from the tenant's configured reporting entity.
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 101, 'https://goaml.test/uae', 'goaml/imp/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());
    }

    @Test
    void csvImportCreatesPartialJobAndReport() throws Exception {
        String analyst = user("analyst", "ANALYST");
        String csv = CSV_HEADER + "\n"
                + "CSV-REF-1,2026-05-26,DPMSJ,Sara,Khan,ENTITY,seller,Gold Traders LLC,CN-1,AE,GOLD,90000,AED,SOLD\n"
                + "CSV-REF-2,2026-05-27,DPMSJ,Sara,Khan,ENTITY,seller,Bad Co,CN-2,AE,GOLD,NOT_A_NUMBER,AED,SOLD\n";

        MvcResult res = mvc.perform(multipart("/api/v1/imports/csv")
                        .file(new MockMultipartFile("file", "sales.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + analyst))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode job = json(res);
        assertThat(job.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(job.get("totalRows").asInt()).isEqualTo(2);
        assertThat(job.get("succeeded").asInt()).isEqualTo(1);
        assertThat(job.get("failed").asInt()).isEqualTo(1);
        assertThat(job.get("results").toString()).contains("good_estimated_value");

        JsonNode reports = json(mvc.perform(get("/api/v1/reports")
                .header("Authorization", "Bearer " + analyst)).andExpect(status().isOk()).andReturn());
        assertThat(reports.toString()).contains("CSV-REF-1").doesNotContain("CSV-REF-2");

        String jobId = job.get("id").asText();
        JsonNode fetched = json(mvc.perform(get("/api/v1/imports/" + jobId)
                .header("Authorization", "Bearer " + analyst)).andExpect(status().isOk()).andReturn());
        assertThat(fetched.get("sourceType").asText()).isEqualTo("DPMSR_CSV");
    }

    @Test
    void xmlImportCreatesCompletedJob() throws Exception {
        String mlro = user("mlro", "MLRO");
        byte[] xml = getClass().getClassLoader().getResourceAsStream("golden/DPMSR.xml").readAllBytes();

        MvcResult res = mvc.perform(multipart("/api/v1/imports/xml")
                        .file(new MockMultipartFile("file", "DPMSR.xml", "application/xml", xml))
                        .header("Authorization", "Bearer " + mlro))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode job = json(res);
        assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.get("succeeded").asInt()).isEqualTo(1);

        JsonNode reports = json(mvc.perform(get("/api/v1/reports")
                .header("Authorization", "Bearer " + mlro)).andExpect(status().isOk()).andReturn());
        assertThat(reports.toString()).contains("DPMSR-2026-001");
    }

    @Test
    void emptyUploadIsRejected() throws Exception {
        String analyst = user("empty", "ANALYST");
        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
                        .header("Authorization", "Bearer " + analyst))
                .andExpect(status().isBadRequest());
    }

    // ----- helpers -----

    private String user(String prefix, String roleName) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@e2e.test";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenant.getId(), email,
                passwordEncoder.encode("P@ssw0rd!"), prefix, "User", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);

        MvcResult res = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"P@ssw0rd!\"}", email)))
                .andExpect(status().isOk()).andReturn();
        return json(res).get("accessToken").asText();
    }

    private JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }
}
