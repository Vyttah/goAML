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
import org.junit.jupiter.api.BeforeAll;
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

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer coverage for the SUPER_ADMIN suite-connection + cross-tenant-user surface, which the existing
 * {@code AdminApiE2ETest} doesn't touch: trusted services + company→tenant links (Suite Connections) and the
 * cross-tenant {@code /tenants/{id}/users} management incl. password reset. Proves create/list/update/delete,
 * the duplicate (409) + not-found (404) + invalid-input (400) paths, and that a reset password actually works.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class AdminSuperAdminE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static String publicPem;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        byte[] x509 = gen.generateKeyPair().getPublic().getEncoded();
        publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(x509) + "\n-----END PUBLIC KEY-----";
    }

    // ----- trusted services -----

    @Test
    void trustedServiceFullLifecycleAndDuplicateGuard() throws Exception {
        String token = superAdminToken();

        // create
        MvcResult created = mvc.perform(post("/api/v1/admin/trusted-services").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "sourceSystem", "SCREENING", "description", "AML screening app",
                                "publicKeyPem", publicPem, "jitProvisioning", true, "defaultRole", "ANALYST"))))
                .andExpect(status().isCreated()).andReturn();
        String id = json(created).get("id").asText();
        assertThat(json(created).get("sourceSystem").asText()).isEqualTo("SCREENING");

        // a second SCREENING registration → 409 (one trusted row per source system)
        mvc.perform(post("/api/v1/admin/trusted-services").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "sourceSystem", "SCREENING", "description", "dup",
                                "publicKeyPem", publicPem, "jitProvisioning", true, "defaultRole", "ANALYST"))))
                .andExpect(status().isConflict());

        // list contains it
        JsonNode list = json(mvc.perform(get("/api/v1/admin/trusted-services")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
        assertThat(list.toString()).contains("SCREENING");

        // update (disable)
        MvcResult updated = mvc.perform(put("/api/v1/admin/trusted-services/" + id).contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "description", "disabled", "publicKeyPem", publicPem,
                                "jitProvisioning", false, "defaultRole", "ANALYST", "status", "DISABLED"))))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(updated).get("status").asText()).isEqualTo("DISABLED");

        // delete frees the source system for re-registration
        mvc.perform(delete("/api/v1/admin/trusted-services/" + id)
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/trusted-services").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "sourceSystem", "SCREENING", "description", "re-registered",
                                "publicKeyPem", publicPem, "jitProvisioning", true, "defaultRole", "ANALYST"))))
                .andExpect(status().isCreated());
        // clean up so the row doesn't leak into other tests sharing the public schema
        String reId = json(mvc.perform(get("/api/v1/admin/trusted-services")
                .header("Authorization", "Bearer " + token)).andReturn()).get(0).get("id").asText();
        mvc.perform(delete("/api/v1/admin/trusted-services/" + reId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
    }

    @Test
    void updatingAndDeletingUnknownTrustedServiceIsNotFound() throws Exception {
        String token = superAdminToken();
        mvc.perform(put("/api/v1/admin/trusted-services/" + UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "publicKeyPem", publicPem, "status", "ACTIVE"))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/trusted-services/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    // ----- company → tenant links -----

    @Test
    void tenantExternalRefLifecycleAndDuplicateGuard() throws Exception {
        String token = superAdminToken();
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "ext-" + UUID.randomUUID().toString().substring(0, 8), "Ext FZE", "AE",
                "ext-admin-" + UUID.randomUUID() + "@ext.test", "P@ssw0rd!", "E", "X"));
        String orgRef = "COMP-" + UUID.randomUUID();

        MvcResult created = mvc.perform(post("/api/v1/admin/tenant-external-refs").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "tenantId", tenant.getId().toString(), "sourceSystem", "SCREENING",
                                "externalOrgRef", orgRef))))
                .andExpect(status().isCreated()).andReturn();
        String id = json(created).get("id").asText();

        // same (source, orgRef) again → 409
        mvc.perform(post("/api/v1/admin/tenant-external-refs").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "tenantId", tenant.getId().toString(), "sourceSystem", "SCREENING",
                                "externalOrgRef", orgRef))))
                .andExpect(status().isConflict());

        JsonNode list = json(mvc.perform(get("/api/v1/admin/tenant-external-refs")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
        assertThat(list.toString()).contains(orgRef);

        mvc.perform(delete("/api/v1/admin/tenant-external-refs/" + id)
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
    }

    @Test
    void invalidSourceSystemIsBadRequest() throws Exception {
        String token = superAdminToken();
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "bad-" + UUID.randomUUID().toString().substring(0, 8), "Bad FZE", "AE",
                "bad-admin-" + UUID.randomUUID() + "@ext.test", "P@ssw0rd!", "B", "S"));
        mvc.perform(post("/api/v1/admin/tenant-external-refs").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(objectMapper.writeValueAsString(Map.of(
                                "tenantId", tenant.getId().toString(), "sourceSystem", "BOGUS",
                                "externalOrgRef", "COMP-" + UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
    }

    // ----- cross-tenant user management + reset password -----

    @Test
    void crossTenantUserManagementAndPasswordReset() throws Exception {
        String token = superAdminToken();
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "tu-" + UUID.randomUUID().toString().substring(0, 8), "Tenant Users FZE", "AE",
                "tu-admin-" + UUID.randomUUID() + "@tu.test", "P@ssw0rd!", "T", "U"));

        // list shows the provisioned TENANT_ADMIN
        JsonNode initial = json(mvc.perform(get("/api/v1/admin/tenants/" + tenant.getId() + "/users")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
        assertThat(initial.toString()).contains("tu-admin-");

        // create an MLRO in that tenant
        String email = "tu-mlro-" + UUID.randomUUID() + "@tu.test";
        MvcResult created = mvc.perform(post("/api/v1/admin/tenants/" + tenant.getId() + "/users")
                        .contentType(APPLICATION_JSON).header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "P@ssw0rd!",
                                "firstName", "Mel", "lastName", "Roe", "role", "MLRO"))))
                .andExpect(status().isCreated()).andReturn();
        String userId = json(created).get("id").asText();

        // update role + status
        MvcResult updated = mvc.perform(put("/api/v1/admin/tenants/" + tenant.getId() + "/users/" + userId)
                        .contentType(APPLICATION_JSON).header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Mel", "lastName", "Roe",
                                "role", "ANALYST", "status", "ACTIVE"))))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(updated).get("roles").toString()).contains("ANALYST");

        // reset the password, then prove the NEW password logs in
        mvc.perform(post("/api/v1/admin/tenants/" + tenant.getId() + "/users/" + userId + "/reset-password")
                        .contentType(APPLICATION_JSON).header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of("password", "BrandN3w!Pass"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"BrandN3w!Pass\"}", email)))
                .andExpect(status().isOk());

        // delete
        mvc.perform(delete("/api/v1/admin/tenants/" + tenant.getId() + "/users/" + userId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
    }

    @Test
    void creatingAUserInAnUnknownTenantIsNotFound() throws Exception {
        String token = superAdminToken();
        mvc.perform(post("/api/v1/admin/tenants/" + UUID.randomUUID() + "/users").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of("email", "x-" + UUID.randomUUID() + "@tu.test",
                                "password", "P@ssw0rd!", "firstName", "X", "lastName", "Y", "role", "ANALYST"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void resettingPasswordForUnknownUserIsNotFound() throws Exception {
        String token = superAdminToken();
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "rp-" + UUID.randomUUID().toString().substring(0, 8), "Reset FZE", "AE",
                "rp-admin-" + UUID.randomUUID() + "@tu.test", "P@ssw0rd!", "R", "P"));
        mvc.perform(post("/api/v1/admin/tenants/" + tenant.getId() + "/users/" + UUID.randomUUID()
                        + "/reset-password").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of("password", "Whatever1!"))))
                .andExpect(status().isNotFound());
    }

    // ----- helpers -----

    private String superAdminToken() throws Exception {
        String email = "super-" + UUID.randomUUID() + "@admin.test";
        Role role = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), null, email, passwordEncoder.encode("P@ssw0rd!"),
                "Super", "Admin", "ACTIVE");
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
