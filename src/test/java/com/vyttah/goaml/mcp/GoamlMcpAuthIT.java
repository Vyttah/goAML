package com.vyttah.goaml.mcp;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 12.1 — the end-to-end auth + transport + tenant/RBAC parity test. A <em>real</em> MCP SSE client
 * connects over HTTP to the running server and:
 *
 * <ol>
 *   <li>with a valid tenant-scoped bearer token, lists the tools and calls {@code goaml_whoami} —
 *       which must echo the token's tenant schema + roles, proving the tool executed under the caller's
 *       {@code TenantContext} + SecurityContext (the same routing the REST API uses); and</li>
 *   <li>without a token, is rejected — proving the MCP transport is not publicly reachable.</li>
 * </ol>
 *
 * <p>The token is minted directly here (mirroring {@code JwtService}'s claims) so the test does not depend
 * on a provisioned tenant/user — {@code goaml_whoami} only reads the resolved identity, no DB row needed.
 */
@SpringBootTest(classes = GoamlApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GoamlMcpAuthIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    JwtProperties jwtProperties;

    private String mintToken(List<String> roles, String schema) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("email", "mcp-user@demo.local")
                .claim("tenant", null)
                .claim("schema", schema)
                .claim("roles", roles)
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private HttpClientSseClientTransport transport(String bearer) {
        var builder = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .sseEndpoint("/api/v1/mcp/sse");
        if (bearer != null) {
            builder.customizeRequest(req -> req.header("Authorization", "Bearer " + bearer));
        }
        return builder.build();
    }

    @Test
    void authedClientListsToolsAndResolvesTenantAndRole() {
        String token = mintToken(List.of("MLRO", "ANALYST"), "public");

        try (McpSyncClient client = McpClient.sync(transport(token))
                .requestTimeout(Duration.ofSeconds(30))
                .build()) {

            client.initialize();

            List<String> toolNames = client.listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .toList();
            assertThat(toolNames).contains("goaml_ping", "goaml_whoami");

            McpSchema.CallToolResult result =
                    client.callTool(new McpSchema.CallToolRequest("goaml_whoami", Map.of()));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String text = firstText(result);
            // The tool ran under the caller's resolved identity (tenant schema + bare roles + email).
            assertThat(text)
                    .contains("public")
                    .contains("MLRO")
                    .contains("ANALYST")
                    .contains("mcp-user@demo.local");
        }
    }

    @Test
    void unauthenticatedClientIsRejected() {
        try (McpSyncClient client = McpClient.sync(transport(null))
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            // The SSE stream sits behind /api/** → 401; the client surfaces the failed handshake.
            assertThatThrownBy(client::initialize).isInstanceOf(Exception.class);
        }
    }

    @Test
    void readToolReturnsStructuredDataOverMcp() {
        try (McpSyncClient client = connect(mintToken(List.of("ANALYST"), "public"))) {
            McpSchema.CallToolResult result =
                    client.callTool(new McpSchema.CallToolRequest("goaml_list_jurisdictions", Map.of()));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            // The UAE jurisdiction config is structured JSON: code + accepted report codes.
            assertThat(firstText(result)).contains("ae").contains("DPMSR");
        }
    }

    @Test
    void validateDpmsrToolDeserializesRequestWithDatesAndReturnsVerdict() {
        try (McpSyncClient client = connect(mintToken(List.of("ANALYST"), "public"))) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "goaml_validate_dpmsr", Map.of("request", sampleDpmsrRequest())));

            // The complex request (incl. an OffsetDateTime submissionDate) deserialized and the engine ran:
            // no tenant config in this test → INVALID with a rentity_id message. The point is a STRUCTURED
            // verdict came back, not a deserialization error.
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(firstText(result)).contains("status").contains("INVALID").contains("rentity_id");
        }
    }

    @Test
    void toolEnforcesRbacOverTheWire() {
        // TENANT_ADMIN may read reports but NOT validate/build — the tool must refuse over MCP.
        try (McpSyncClient client = connect(mintToken(List.of("TENANT_ADMIN"), "public"))) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "goaml_validate_dpmsr", Map.of("request", sampleDpmsrRequest())));

            assertThat(result.isError()).isEqualTo(Boolean.TRUE);
            assertThat(firstText(result)).contains("requires one of roles");
        }
    }

    private McpSyncClient connect(String token) {
        McpSyncClient client = McpClient.sync(transport(token)).requestTimeout(Duration.ofSeconds(30)).build();
        client.initialize();
        return client;
    }

    /** A DPMSR request as nested maps (what an MCP client would send), incl. a date-typed field. */
    private static Map<String, Object> sampleDpmsrRequest() {
        return Map.of(
                "entityReference", "MCP-" + UUID.randomUUID(),
                "submissionDate", "2026-06-02T12:00:00Z",
                "reason", "DPMS threshold met",
                "action", "Filed",
                "indicators", List.of("DPMSJ"),
                "reportingPerson", Map.of("firstName", "Sara", "lastName", "Khan"),
                "parties", List.of(Map.of(
                        "reason", "Seller of gold above AED 55,000",
                        "entity", Map.of("name", "Minimal Trading FZE", "incorporationCountryCode", "AE"))),
                "goods", List.of(Map.of(
                        "itemType", "GOLD", "estimatedValue", 90000, "currencyCode", "AED")));
    }

    private static String firstText(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no text content in tool result: " + result));
    }
}
