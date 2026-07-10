package com.vyttah.goaml.controller.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.notification.Notification;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.notification.NotificationRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-layer coverage for the notification API (Phase 10) — previously only the service + persistence layers
 * were tested. Seeds in-app rows directly in the tenant schema, then drives the REST endpoints as the real
 * recipient: a user sees only their own notifications, the unread filter works, mark-read flips read state,
 * and a user cannot mark another user's notification read (404 — ownership is enforced at the HTTP boundary).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class NotificationControllerE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired NotificationRepository notificationRepository;

    private String token;
    private UUID readId;
    private UUID unreadId;
    private UUID otherUsersNotificationId;

    @BeforeEach
    void setUp() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "ntf-" + UUID.randomUUID().toString().substring(0, 8), "Notif FZE", "AE",
                "ntf-admin-" + UUID.randomUUID() + "@ntf.test", "P@ssw0rd!", "Ntf", "Adm"));

        UUID recipientId = UUID.randomUUID();
        token = createUser(tenant, recipientId, "MLRO", "mlro");
        UUID otherUserId = UUID.randomUUID();
        createUser(tenant, otherUserId, "ANALYST", "analyst");

        readId = UUID.randomUUID();
        unreadId = UUID.randomUUID();
        otherUsersNotificationId = UUID.randomUUID();
        runAsTenant(tenant.getSchemaName(), () -> {
            Notification read = new Notification(readId, recipientId, "REPORT_ACCEPTED", UUID.randomUUID(),
                    "Report accepted", "The FIU accepted your report.");
            read.markRead();
            notificationRepository.saveAndFlush(read);
            notificationRepository.saveAndFlush(new Notification(unreadId, recipientId, "REPORT_REJECTED",
                    UUID.randomUUID(), "Report rejected", "The FIU rejected your report."));
            notificationRepository.saveAndFlush(new Notification(otherUsersNotificationId, otherUserId,
                    "REPORT_FAILED", UUID.randomUUID(), "Submission failed", "A transport error occurred."));
            return null;
        });
    }

    @Test
    void listReturnsOnlyTheCallersNotifications() {
        ResponseEntity<JsonNode> resp = get("/api/v1/notifications");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().toString())
                .contains("Report accepted").contains("Report rejected")
                .doesNotContain("Submission failed"); // belongs to the other user
        assertThat(resp.getBody()).hasSize(2);
    }

    @Test
    void unreadFilterExcludesReadNotifications() {
        ResponseEntity<JsonNode> resp = get("/api/v1/notifications?unread=true");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).get("id").asText()).isEqualTo(unreadId.toString());
    }

    @Test
    void markReadFlipsTheReadState() {
        ResponseEntity<JsonNode> resp = post("/api/v1/notifications/" + unreadId + "/read");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("readAt").isNull()).isFalse();
        // now there are no unread notifications left
        assertThat(get("/api/v1/notifications?unread=true").getBody()).isEmpty();
    }

    @Test
    void cannotMarkAnotherUsersNotificationRead() {
        assertThat(post("/api/v1/notifications/" + otherUsersNotificationId + "/read").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markingAnUnknownNotificationIsNotFound() {
        assertThat(post("/api/v1/notifications/" + UUID.randomUUID() + "/read").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----- helpers -----

    private String createUser(Tenant tenant, UUID userId, String roleName, String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@ntf.test";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser user = new AppUser(userId, tenant.getId(), email, passwordEncoder.encode("P@ssw0rd!"),
                prefix, "User", "ACTIVE");
        user.addRole(role);
        userRepository.save(user);
        return login(tenant.getSlug(), email, "P@ssw0rd!");
    }

    private String login(String companyId, String email, String password) {
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"companyId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                        companyId, email, password), jsonHeaders(null)), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> get(String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(jsonHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>("", jsonHeaders(token)), JsonNode.class);
    }

    private HttpHeaders jsonHeaders(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            h.setBearerAuth(jwt);
        }
        return h;
    }

    private static <T> T runAsTenant(String schema, Supplier<T> body) {
        TenantContext.set(schema);
        try {
            return body.get();
        } finally {
            TenantContext.clear();
        }
    }
}
