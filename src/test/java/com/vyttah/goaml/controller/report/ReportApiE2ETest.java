package com.vyttah.goaml.controller.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 7.3 end-to-end: login → create DPMSR → validate → submit → status, over real HTTP + Testcontainers
 * Postgres. The goAML B2B client is mocked (its real path is covered by the b2b + 7.2 tests), so this proves
 * the web → service → persistence wiring, RBAC, and idempotency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class ReportApiE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean GoamlB2bClient b2bClient;

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;
    private final ReportMarshaller marshaller = new ReportMarshaller();
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
                "rep-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Report E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "Adm", "In"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/e2e/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());
    }

    @Test
    void mlroCreatesValidatesSubmitsAndChecksStatus() {
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-E2E");
        when(b2bClient.getReportStatus(any(), any())).thenReturn(new ReportStatus("RK-E2E", "Accepted", null));
        String mlro = user("mlro", "MLRO");

        // create + validate
        ResponseEntity<JsonNode> created = post("/api/v1/reports", String.format(DPMSR_JSON, "PAY-E2E-1"), mlro);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status").asText()).isEqualTo("VALID");
        String reportId = created.getBody().get("reportId").asText();

        // it shows up in the list
        ResponseEntity<JsonNode> list = get("/api/v1/reports", mlro);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().toString()).contains("PAY-E2E-1");

        // and by id
        ResponseEntity<JsonNode> byId = get("/api/v1/reports/" + reportId, mlro);
        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byId.getBody().get("entityReference").asText()).isEqualTo("PAY-E2E-1");

        // a missing id → 404
        assertThat(get("/api/v1/reports/" + UUID.randomUUID(), mlro).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // submit (MLRO) → reportkey
        ResponseEntity<JsonNode> submitted = post("/api/v1/reports/" + reportId + "/submit", "", mlro);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitted.getBody().get("reportKey").asText()).isEqualTo("RK-E2E");
        assertThat(submitted.getBody().get("status").asText()).isEqualTo("SUBMITTED");

        // status refresh → ACCEPTED
        ResponseEntity<JsonNode> status = get("/api/v1/reports/" + reportId + "/status", mlro);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().get("status").asText()).isEqualTo("Accepted");
    }

    @Test
    void xmlEndpointReturnsMarshalledGoamlXml() {
        String mlro = user("xml", "MLRO");
        ResponseEntity<JsonNode> created = post("/api/v1/reports", String.format(DPMSR_JSON, "PAY-XML-1"), mlro);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reportId = created.getBody().get("reportId").asText();

        ResponseEntity<String> xml = rest.exchange("/api/v1/reports/" + reportId + "/xml",
                HttpMethod.GET, new HttpEntity<>(headers(mlro)), String.class);
        assertThat(xml.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xml.getHeaders().getContentType().toString()).startsWith(MediaType.APPLICATION_XML_VALUE);
        assertThat(xml.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("PAY-XML-1.xml");
        assertThat(xml.getBody())
                .contains("<report_code>DPMSR</report_code>")
                .contains("<entity_reference>PAY-XML-1</entity_reference>")
                .contains("<rentity_id>3177</rentity_id>");

        // a missing report → 404
        assertThat(rest.exchange("/api/v1/reports/" + UUID.randomUUID() + "/xml",
                HttpMethod.GET, new HttpEntity<>(headers(mlro)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void detailReturnsStoredInputValidationAndAnEmptyReviewTrail() {
        String mlro = user("detail", "MLRO");
        ResponseEntity<JsonNode> created = post("/api/v1/reports", String.format(DPMSR_JSON, "PAY-DETAIL-1"), mlro);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reportId = created.getBody().get("reportId").asText();

        ResponseEntity<JsonNode> detail = get("/api/v1/reports/" + reportId + "/detail", mlro);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = detail.getBody();
        // summary
        assertThat(body.get("entityReference").asText()).isEqualTo("PAY-DETAIL-1");
        assertThat(body.get("status").asText()).isEqualTo("VALID");
        assertThat(body.get("hasXml").asBoolean()).isTrue();
        // the stored filing input is returned as a JSON tree (not a stringified blob)
        assertThat(body.get("input").isObject()).isTrue();
        assertThat(body.get("input").toString())
                .contains("Minimal Trading FZE")
                .contains("GOLD");
        assertThat(body.get("validationMessages").isArray()).isTrue();
        // not yet reviewed
        assertThat(body.get("reviewedBy").isNull()).isTrue();
        assertThat(body.get("reviewedAt").isNull()).isTrue();
    }

    @Test
    void analystCanCreateButCannotSubmit() {
        String analyst = user("analyst", "ANALYST");

        ResponseEntity<JsonNode> created = post("/api/v1/reports", String.format(DPMSR_JSON, "PAY-E2E-2"), analyst);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reportId = created.getBody().get("reportId").asText();

        ResponseEntity<JsonNode> submit = post("/api/v1/reports/" + reportId + "/submit", "", analyst);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateEntityReferenceIsConflict() {
        String mlro = user("dup", "MLRO");
        assertThat(post("/api/v1/reports", String.format(DPMSR_JSON, "PAY-DUP"), mlro).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(post("/api/v1/reports", String.format(DPMSR_JSON, "PAY-DUP"), mlro).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void curatedDpmsrEndpointCreatesValidatesAndPersists() {
        String analyst = user("curated", "ANALYST");

        // the AML cockpit assembles the curated DpmsrCreateRequest and POSTs it directly to goAML
        ResponseEntity<JsonNode> created =
                post("/api/v1/reports/dpmsr", String.format(DPMSR_JSON, "PAY-CURATED-1"), analyst);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status").asText()).isEqualTo("VALID");
        String reportId = created.getBody().get("reportId").asText();

        // it is the same report store as POST /reports — shows up in the list + carries the right XML
        assertThat(get("/api/v1/reports", analyst).getBody().toString()).contains("PAY-CURATED-1");
        ResponseEntity<String> xml = rest.exchange("/api/v1/reports/" + reportId + "/xml",
                HttpMethod.GET, new HttpEntity<>(headers(analyst)), String.class);
        assertThat(xml.getBody())
                .contains("<report_code>DPMSR</report_code>")
                .contains("<entity_reference>PAY-CURATED-1</entity_reference>")
                .contains("<item_type>GOLD</item_type>");

        // idempotency holds on the curated path too
        assertThat(post("/api/v1/reports/dpmsr", String.format(DPMSR_JSON, "PAY-CURATED-1"), analyst)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void fullFidelityPayloadPersistsEveryFieldIntoTheXml() throws Exception {
        String mlro = user("full", "MLRO");

        // build the full-fidelity payload from the real (anonymized) third-party DPMSR sample
        Report sample = marshaller.unmarshal(readResource("samples/USG-dpmsr-activity.xml"));
        ActivityType activity = sample.getReportActivity();
        DpmsrReportPayload payload = new DpmsrReportPayload(
                sample.getRentityBranch(), "PAY-FULL-1", sample.getSubmissionDate(), sample.getFiuRefNumber(),
                sample.getReportingPerson(), sample.getLocation(), sample.getReason(), sample.getAction(),
                List.of("DPMSJ"), activity.getReportParties().getReportParty(),
                activity.getGoodsServices().getItem());

        ResponseEntity<JsonNode> created =
                post("/api/v1/reports", objectMapper.writeValueAsString(payload), mlro);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reportId = created.getBody().get("reportId").asText();

        // the marshalled goAML XML carries every field that the curated contract used to drop
        ResponseEntity<String> xml = rest.exchange("/api/v1/reports/" + reportId + "/xml",
                HttpMethod.GET, new HttpEntity<>(headers(mlro)), String.class);
        assertThat(xml.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xml.getBody())
                .contains("<disposed_value>10050000.00</disposed_value>")
                .contains("<registration_number>SAMPLE0000001</registration_number>")
                .contains("<identification_number>REC0000000001</identification_number>")
                .contains("<status_comments>CASH RECEIVED AGAINST 95KG GOLD SOLD</status_comments>")
                .contains("<ssn>784199000000001</ssn>")
                .contains("<passport_number>S0000001</passport_number>")
                .contains("<incorporation_date>2023-06-05")
                .contains("<role>PRTNR</role>");
    }

    private static byte[] readResource(String path) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("resource %s present", path).isNotNull();
            return in.readAllBytes();
        }
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
        ResponseEntity<JsonNode> resp = post("/api/v1/auth/login",
                String.format("{\"companyId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                        tenant.getSlug(), email, password), null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> post(String path, String body, String jwt) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(jwt)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String jwt) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(jwt)), JsonNode.class);
    }

    private HttpHeaders headers(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            h.setBearerAuth(jwt);
        }
        return h;
    }
}
