package com.vyttah.goaml.controller.admin;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.2 end-to-end (MockMvc + Testcontainers): SUPER_ADMIN provisions + lists tenants; the provisioned
 * tenant's TENANT_ADMIN creates/lists users and upserts/reads the goAML config; RBAC denials + duplicate
 * email are enforced. Proves the admin web → service → persistence wiring and tenant-scoping.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class AdminApiE2ETest {

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
    void superAdminProvisionsTenantThenTenantAdminManagesUsersAndConfig() throws Exception {
        String superToken = superAdminToken();

        // SUPER_ADMIN provisions a tenant (also creates its initial TENANT_ADMIN)
        String adminEmail = "ta-" + UUID.randomUUID() + "@e2e.test";
        String slug = "adm-" + UUID.randomUUID().toString().substring(0, 8);
        String body = objectMapper.writeValueAsString(new TenantProvisioningRequest(
                slug, "Admin E2E FZE", "AE", adminEmail, "P@ssw0rd!", "Ten", "Admin"));
        MvcResult created = mvc.perform(post("/api/v1/admin/tenants").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + superToken).content(body))
                .andExpect(status().isCreated()).andReturn();
        assertThat(json(created).get("slug").asText()).isEqualTo(slug);

        // SUPER_ADMIN lists tenants → contains it
        JsonNode tenants = json(mvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superToken)).andExpect(status().isOk()).andReturn());
        assertThat(tenants.toString()).contains(slug);

        // the provisioned TENANT_ADMIN logs in and manages users + config
        String taToken = login(adminEmail, "P@ssw0rd!");
        String analystEmail = "an-" + UUID.randomUUID() + "@e2e.test";
        mvc.perform(post("/api/v1/admin/users").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"email\":\"" + analystEmail + "\",\"password\":\"P@ssw0rd!\","
                                + "\"firstName\":\"An\",\"lastName\":\"Alyst\",\"role\":\"ANALYST\"}"))
                .andExpect(status().isCreated());

        JsonNode users = json(mvc.perform(get("/api/v1/admin/users")
                .header("Authorization", "Bearer " + taToken)).andExpect(status().isOk()).andReturn());
        assertThat(users.toString()).contains(analystEmail).contains(adminEmail);

        // duplicate email → 409
        mvc.perform(post("/api/v1/admin/users").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"email\":\"" + analystEmail + "\",\"password\":\"P@ssw0rd!\","
                                + "\"firstName\":\"Dup\",\"lastName\":\"Licate\",\"role\":\"MLRO\"}"))
                .andExpect(status().isConflict());

        // goAML config upsert + read
        mvc.perform(put("/api/v1/admin/goaml-config").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"jurisdictionCode\":\"AE\",\"rentityId\":3177,"
                                + "\"baseUrl\":\"https://goaml.test/uae\",\"secretsPath\":\"goaml/e2e/creds\","
                                + "\"authMode\":\"TOKEN\"}"))
                .andExpect(status().isOk());
        JsonNode cfg = json(mvc.perform(get("/api/v1/admin/goaml-config")
                .header("Authorization", "Bearer " + taToken)).andExpect(status().isOk()).andReturn());
        assertThat(cfg.get("rentityId").asInt()).isEqualTo(3177);
        assertThat(cfg.get("baseUrl").asText()).isEqualTo("https://goaml.test/uae");
    }

    @Test
    void rbacBlocksWrongRoles() throws Exception {
        // a tenant with an ANALYST who may not touch admin
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "rbac-" + UUID.randomUUID().toString().substring(0, 8), "RBAC FZE", "AE",
                "rbac-admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "R", "B"));
        String analystToken = userInTenant(tenant.getId(), "ANALYST");

        // ANALYST cannot provision tenants (SUPER_ADMIN) or manage users (TENANT_ADMIN)
        mvc.perform(post("/api/v1/admin/tenants").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + analystToken).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdminManagesGoamlReportingPersonsWithOneActiveDefault() throws Exception {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "gp-" + UUID.randomUUID().toString().substring(0, 8), "GoAML Person FZE", "AE",
                "gp-admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "G", "P"));
        String taToken = userInTenant(tenant.getId(), "TENANT_ADMIN");

        // create an active reporting person
        MvcResult p1 = mvc.perform(post("/api/v1/admin/goaml-persons").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"firstName\":\"Aisha\",\"lastName\":\"Khan\",\"gender\":\"F\","
                                + "\"nationality\":\"AE\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        String id1 = json(p1).get("id").asText();
        assertThat(json(p1).get("active").asBoolean()).isTrue();

        // a second active person → the first is demoted (one active per tenant, the partial unique index)
        MvcResult p2 = mvc.perform(post("/api/v1/admin/goaml-persons").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"firstName\":\"Omar\",\"lastName\":\"Saeed\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        String id2 = json(p2).get("id").asText();

        JsonNode list = json(mvc.perform(get("/api/v1/admin/goaml-persons")
                .header("Authorization", "Bearer " + taToken)).andExpect(status().isOk()).andReturn());
        int active = 0;
        String activeId = null;
        for (JsonNode n : list) {
            if (n.get("active").asBoolean()) {
                active++;
                activeId = n.get("id").asText();
            }
        }
        assertThat(active).isEqualTo(1);
        assertThat(activeId).isEqualTo(id2);

        // reactivate the first via update (demotes the second)
        mvc.perform(put("/api/v1/admin/goaml-persons/" + id1).contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"firstName\":\"Aisha\",\"lastName\":\"Khan\",\"active\":true}"))
                .andExpect(status().isOk());

        // delete the second
        mvc.perform(delete("/api/v1/admin/goaml-persons/" + id2)
                .header("Authorization", "Bearer " + taToken)).andExpect(status().isNoContent());

        // update a non-existent person → 404
        mvc.perform(put("/api/v1/admin/goaml-persons/" + UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"firstName\":\"X\",\"lastName\":\"Y\"}"))
                .andExpect(status().isNotFound());
    }

    // ----- helpers -----

    private String superAdminToken() throws Exception {
        return userWithRole(null, "SUPER_ADMIN");
    }

    private String userInTenant(UUID tenantId, String role) throws Exception {
        return userWithRole(tenantId, role);
    }

    private String userWithRole(UUID tenantId, String roleName) throws Exception {
        String email = roleName.toLowerCase() + "-" + UUID.randomUUID() + "@e2e.test";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenantId, email, passwordEncoder.encode("P@ssw0rd!"),
                "F", "L", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);
        return login(email, "P@ssw0rd!");
    }

    private String login(String email, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
                .andExpect(status().isOk()).andReturn();
        return json(res).get("accessToken").asText();
    }

    private JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }
}
