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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (MockMvc + Testcontainers) for the self-service {@code /api/v1/me/goaml-persons} endpoints the
 * AML cockpit drives with its federated JWT. Proves the split-permission model: TENANT_ADMIN gets full CRUD +
 * one-active-default behaviour; MLRO may only read (403 on writes); and a token is scoped to its own tenant.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class MeGoamlPersonsE2ETest {

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
    void tenantAdminManagesPersonsAndMlroCanOnlyRead() throws Exception {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "me-" + UUID.randomUUID().toString().substring(0, 8), "Me Persons FZE", "AE",
                "me-admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "M", "E"));
        String taToken = userInTenant(tenant, "TENANT_ADMIN");
        String mlroToken = userInTenant(tenant, "MLRO");

        // TENANT_ADMIN creates an active reporting person via the self-service endpoint
        MvcResult p1 = mvc.perform(post("/api/v1/me/goaml-persons").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"firstName\":\"Aisha\",\"lastName\":\"Khan\",\"gender\":\"F\","
                                + "\"nationality\":\"AE\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        String id1 = json(p1).get("id").asText();
        assertThat(json(p1).get("active").asBoolean()).isTrue();

        // a second active person demotes the first (one active per tenant)
        MvcResult p2 = mvc.perform(post("/api/v1/me/goaml-persons").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"firstName\":\"Omar\",\"lastName\":\"Saeed\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        String id2 = json(p2).get("id").asText();

        // MLRO may READ the list (and sees the same tenant's persons)
        JsonNode mlroList = json(mvc.perform(get("/api/v1/me/goaml-persons")
                .header("Authorization", "Bearer " + mlroToken)).andExpect(status().isOk()).andReturn());
        int active = 0;
        String activeId = null;
        for (JsonNode n : mlroList) {
            if (n.get("active").asBoolean()) {
                active++;
                activeId = n.get("id").asText();
            }
        }
        assertThat(active).isEqualTo(1);
        assertThat(activeId).isEqualTo(id2);

        // MLRO is forbidden from every write
        mvc.perform(post("/api/v1/me/goaml-persons").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mlroToken)
                        .content("{\"firstName\":\"No\",\"lastName\":\"Way\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/me/goaml-persons/" + id1).contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mlroToken)
                        .content("{\"firstName\":\"No\",\"lastName\":\"Way\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/me/goaml-persons/" + id1)
                .header("Authorization", "Bearer " + mlroToken)).andExpect(status().isForbidden());

        // TENANT_ADMIN reactivates the first, then deletes the second
        mvc.perform(put("/api/v1/me/goaml-persons/" + id1).contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + taToken)
                        .content("{\"firstName\":\"Aisha\",\"lastName\":\"Khan\",\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/me/goaml-persons/" + id2)
                .header("Authorization", "Bearer " + taToken)).andExpect(status().isNoContent());

        JsonNode after = json(mvc.perform(get("/api/v1/me/goaml-persons")
                .header("Authorization", "Bearer " + taToken)).andExpect(status().isOk()).andReturn());
        assertThat(after.toString()).contains(id1).doesNotContain(id2);
    }

    @Test
    void tokenSeesOnlyItsOwnTenantPersons() throws Exception {
        Tenant a = provisioningService.provision(new TenantProvisioningRequest(
                "mea-" + UUID.randomUUID().toString().substring(0, 8), "Tenant A FZE", "AE",
                "mea-admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "A", "A"));
        Tenant b = provisioningService.provision(new TenantProvisioningRequest(
                "meb-" + UUID.randomUUID().toString().substring(0, 8), "Tenant B FZE", "AE",
                "meb-admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "B", "B"));
        String tokenA = userInTenant(a, "TENANT_ADMIN");
        String tokenB = userInTenant(b, "TENANT_ADMIN");

        MvcResult inA = mvc.perform(post("/api/v1/me/goaml-persons").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenA)
                        .content("{\"firstName\":\"Only\",\"lastName\":\"InA\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        String idA = json(inA).get("id").asText();

        // tenant B's token must not see tenant A's person
        JsonNode listB = json(mvc.perform(get("/api/v1/me/goaml-persons")
                .header("Authorization", "Bearer " + tokenB)).andExpect(status().isOk()).andReturn());
        assertThat(listB.toString()).doesNotContain(idA);

        // and B cannot update A's person (not in its tenant) → 404
        mvc.perform(put("/api/v1/me/goaml-persons/" + idA).contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenB)
                        .content("{\"firstName\":\"X\",\"lastName\":\"Y\"}"))
                .andExpect(status().isNotFound());
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
