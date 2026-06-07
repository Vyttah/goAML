package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.mcp.McpIdentity;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Baseline MCP tools that prove the harness is wired end-to-end: a liveness {@code goaml_ping} and a
 * {@code goaml_whoami} that echoes the tenant + role the server resolved from the caller's token.
 *
 * <p>These establish the transport + auth + tenant/RBAC contract first; the real domain tools
 * (build / validate / preview / submit) land in later Phase-12 steps and reuse the same
 * {@link McpIdentity} mechanism. Tools return typed records so the model reasons over structured
 * JSON rather than prose.
 */
@Component
public class SystemTools {

    private final String serverName;
    private final String serverVersion;

    public SystemTools(
            @Value("${spring.ai.mcp.server.name:goaml-mcp}") String serverName,
            @Value("${spring.ai.mcp.server.version:0.1.0}") String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    /** Result of {@link #ping()}. */
    public record PingResult(String server, String version, String status) {}

    /** Result of {@link #whoami()} — the identity resolved from the caller's token. */
    public record WhoAmIResult(boolean authenticated, String email, String tenantSchema, List<String> roles) {}

    @Tool(name = "goaml_ping",
            description = "Liveness check for the goAML MCP server. Returns the server name, version, and "
                    + "'ok'. Safe and read-only; use it to confirm the connection works.")
    public PingResult ping() {
        return new PingResult(serverName, serverVersion, "ok");
    }

    @Tool(name = "goaml_whoami",
            description = "Returns the identity the goAML MCP server resolved from your token: your email, "
                    + "tenant schema, and roles. Call this first to confirm you are connected as the right "
                    + "tenant and have the role you expect before building or submitting reports.")
    public WhoAmIResult whoami() {
        return McpIdentity.current()
                .map(identity -> new WhoAmIResult(true, identity.email(), identity.tenantSchema(), identity.roles()))
                .orElse(new WhoAmIResult(false, null, null, List.of()));
    }
}
