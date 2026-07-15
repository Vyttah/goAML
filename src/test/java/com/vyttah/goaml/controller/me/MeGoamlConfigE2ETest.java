package com.vyttah.goaml.controller.me;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (MockMvc + Testcontainers) for the self-service {@code /api/v1/me/goaml-config} endpoints the
 * AML cockpit drives with its federated JWT so a tenant admin can set its FIU submission config without
 * opening goAML's admin SPA. Proves: GET is 404 until set, TENANT_ADMIN can upsert then read it back, and
 * MLRO is forbidden from both (TENANT_ADMIN-only, mirroring {@code /api/v1/admin/goaml-config}).
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class MeGoamlConfigE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void tenantAdminUpsertsConfigAndMlroIsForbidden() throws Exception {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "cfg-" + UUID.randomUUID().toString().substring(0, 8), "Config FZE", "AE",
                "cfg-admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "M", "E"));
        String taToken = userInTenant(tenant, "TENANT_ADMIN");
        String mlroToken = userInTenant(tenant, "MLRO");

        // No config yet → 404
        mvc.perform(get("/api/v1/me/goaml-config").header("Authorization", "Bearer " + taToken))
                .andExpect(status().isNotFound());

        // TENANT_ADMIN upserts the config. Send a lowercase jurisdiction ("ae", as the /lookups/jurisdictions
        // dropdown returns) — the service must normalise it to the canonical `jurisdiction` table casing ("AE")
        // so the case-sensitive jurisdiction_code FK is satisfied (regression guard).
        MvcResult saved = mvc.perform(put("/api/v1/me/goaml-config").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"jurisdictionCode\":\"ae\",\"rentityId\":3177,"
                                + "\"baseUrl\":\"https://goaml.uaefiu.gov.ae/b2b\","
                                + "\"secretsPath\":\"goaml/cfg/b2b-credentials\",\"authMode\":\"TOKEN\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(saved).get("rentityId").asInt()).isEqualTo(3177);
        assertThat(json(saved).get("jurisdictionCode").asText()).isEqualTo("AE");

        // ...and reads it back
        JsonNode got = json(mvc.perform(get("/api/v1/me/goaml-config")
                .header("Authorization", "Bearer " + taToken)).andExpect(status().isOk()).andReturn());
        assertThat(got.get("jurisdictionCode").asText()).isEqualTo("AE");
        assertThat(got.get("baseUrl").asText()).isEqualTo("https://goaml.uaefiu.gov.ae/b2b");
        assertThat(got.get("secretsPath").asText()).isEqualTo("goaml/cfg/b2b-credentials");

        // MLRO is forbidden from both read and write (TENANT_ADMIN-only)
        mvc.perform(get("/api/v1/me/goaml-config").header("Authorization", "Bearer " + mlroToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/me/goaml-config").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mlroToken)
                        .content("{\"jurisdictionCode\":\"AE\",\"rentityId\":1,"
                                + "\"baseUrl\":\"https://x\",\"secretsPath\":\"y\",\"authMode\":\"TOKEN\"}"))
                .andExpect(status().isForbidden());
    }

    // ----- helpers -----

    private String userInTenant(Tenant tenant, String roleName) throws Exception {
        String email = roleName.toLowerCase() + "-" + UUID.randomUUID() + "@e2e.test";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenant.getId(), email, passwordEncoder.encode("P@ssw0rd!"),
                "F", "L", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);
        return login(tenant.getSlug(), email, "P@ssw0rd!");
    }

    private String login(String companyId, String email, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(String.format("{\"companyId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                                companyId, email, password)))
                .andExpect(status().isOk()).andReturn();
        return json(res).get("accessToken").asText();
    }

    private JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }
}
