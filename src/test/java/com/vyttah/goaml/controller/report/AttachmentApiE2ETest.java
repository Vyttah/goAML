package com.vyttah.goaml.controller.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.integration.aws.S3StorageClient;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 8.4 end-to-end: create DPMSR → attach a file (multipart) → list → submit (the ZIP carries the
 * attachment) → remove, over real HTTP + Testcontainers Postgres. The goAML B2B client and the S3 client
 * are mocked (their real paths are covered by the b2b tests + S3StorageClientIT), so this proves the
 * web → service → persistence wiring, RBAC, and the submit-pulls-attachments path deterministically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class AttachmentApiE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean GoamlB2bClient b2bClient;
    @MockBean S3StorageClient s3StorageClient;

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    private Tenant tenant;

    private static final String DPMSR_JSON = """
            {
              "entityReference": "%s",
              "submissionDate": "2026-06-02T12:00:00Z",
              "reason": "DPMS threshold met", "action": "Filed",
              "indicators": ["DPMSJ"],
              "reportingPerson": {"firstName": "Sara", "lastName": "Khan"},
              "parties": [{"reason": "Seller", "entity":
                  {"name": "Minimal Trading FZE", "incorporationNumber": "123456", "incorporationCountryCode": "AE"}}],
              "goods": [{"itemType": "GOLD", "estimatedValue": 90000.00, "currencyCode": "AED"}]
            }""";

    @BeforeEach
    void setUp() {
        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "att-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Attachment E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "Adm", "In"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/e2e/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());
    }

    @Test
    void attachListSubmitWithAttachmentThenRemove() {
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-ATT");
        // S3 stores the bytes on add and returns them on submit
        when(s3StorageClient.fetch(any())).thenReturn("INVOICE-BYTES".getBytes(StandardCharsets.UTF_8));
        String mlro = user("mlro", "MLRO");

        String reportId = postJson("/api/v1/reports", String.format(DPMSR_JSON, "PAY-ATT-1"), mlro)
                .getBody().get("reportId").asText();
        String base = "/api/v1/reports/" + reportId + "/attachments";

        // attach a PDF (multipart) → 201
        ResponseEntity<JsonNode> added = upload(base, "invoice.pdf", "application/pdf",
                "INVOICE-BYTES".getBytes(StandardCharsets.UTF_8), mlro);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(added.getBody().get("filename").asText()).isEqualTo("invoice.pdf");
        String attachmentId = added.getBody().get("id").asText();
        verify(s3StorageClient).put(any(), any(), any());

        // list → contains it
        ResponseEntity<JsonNode> list = get(base, mlro);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().toString()).contains("invoice.pdf");

        // submit → the packaged ZIP is the bytes the b2b client receives; the attachment was pulled from S3
        ResponseEntity<JsonNode> submitted = postJson("/api/v1/reports/" + reportId + "/submit", "", mlro);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitted.getBody().get("reportKey").asText()).isEqualTo("RK-ATT");
        verify(s3StorageClient).fetch(any());

        // once submitted, attachments are frozen → remove is 409
        ResponseEntity<JsonNode> frozen = delete(base + "/" + attachmentId, mlro);
        assertThat(frozen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void downloadsAttachmentBytes() {
        when(s3StorageClient.fetch(any())).thenReturn("INVOICE-BYTES".getBytes(StandardCharsets.UTF_8));
        String mlro = user("dl", "MLRO");
        String reportId = postJson("/api/v1/reports", String.format(DPMSR_JSON, "PAY-ATT-DL"), mlro)
                .getBody().get("reportId").asText();
        String base = "/api/v1/reports/" + reportId + "/attachments";

        String attachmentId = upload(base, "invoice.pdf", "application/pdf",
                "INVOICE-BYTES".getBytes(StandardCharsets.UTF_8), mlro).getBody().get("id").asText();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(mlro);
        ResponseEntity<byte[]> dl = rest.exchange(base + "/" + attachmentId + "/content",
                HttpMethod.GET, new HttpEntity<>(h), byte[].class);

        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(dl.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("invoice.pdf");
        assertThat(new String(dl.getBody(), StandardCharsets.UTF_8)).isEqualTo("INVOICE-BYTES");

        // a missing attachment → 404
        ResponseEntity<byte[]> missing = rest.exchange(base + "/" + UUID.randomUUID() + "/content",
                HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsDisallowedExtension() {
        String mlro = user("badext", "MLRO");
        String reportId = postJson("/api/v1/reports", String.format(DPMSR_JSON, "PAY-ATT-2"), mlro)
                .getBody().get("reportId").asText();

        ResponseEntity<JsonNode> resp = upload("/api/v1/reports/" + reportId + "/attachments",
                "malware.exe", "application/octet-stream", new byte[]{1, 2, 3}, mlro);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removeOnDraftDeletesFromS3AndRepo() {
        when(s3StorageClient.fetch(any())).thenReturn("X".getBytes(StandardCharsets.UTF_8));
        String analyst = user("analyst", "ANALYST");
        String reportId = postJson("/api/v1/reports", String.format(DPMSR_JSON, "PAY-ATT-3"), analyst)
                .getBody().get("reportId").asText();
        String base = "/api/v1/reports/" + reportId + "/attachments";

        String attachmentId = upload(base, "doc.pdf", "application/pdf", new byte[]{9}, analyst)
                .getBody().get("id").asText();

        // a VALID report is still editable → remove succeeds (204) and hits S3 delete
        ResponseEntity<JsonNode> removed = delete(base + "/" + attachmentId, analyst);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(s3StorageClient).delete(any());
        assertThat(get(base, analyst).getBody().toString()).doesNotContain("doc.pdf");
    }

    // ----- helpers -----

    private String user(String prefix, String roleName) {
        String email = prefix + "-" + UUID.randomUUID() + "@e2e.test";
        String password = "P@ssw0rd!";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenant.getId(), email,
                passwordEncoder.encode(password), prefix, "User", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);
        return login(email, password);
    }

    private String login(String email, String password) {
        return postJson("/api/v1/auth/login",
                String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password), null)
                .getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> postJson(String path, String body, String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) h.setBearerAuth(jwt);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String jwt) {
        HttpHeaders h = new HttpHeaders();
        if (jwt != null) h.setBearerAuth(jwt);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> delete(String path, String jwt) {
        HttpHeaders h = new HttpHeaders();
        if (jwt != null) h.setBearerAuth(jwt);
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> upload(String path, String filename, String contentType,
                                            byte[] bytes, String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (jwt != null) h.setBearerAuth(jwt);

        ByteArrayResource part = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(part, partHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }
}
