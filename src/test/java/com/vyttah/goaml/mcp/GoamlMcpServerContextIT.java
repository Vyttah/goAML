package com.vyttah.goaml.mcp;

import com.vyttah.goaml.GoamlApplication;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 12.1 — the Spring AI MCP server auto-configures inside the app (full context + Testcontainers):
 * a SYNC {@link McpSyncServer} bean exists and the goAML {@link ToolCallbackProvider} registers the
 * baseline tools. This is the "MCP server starts; tools are discovered" half of the step's done-criteria;
 * the auth/transport half is proven by {@code GoamlMcpAuthIT}.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class GoamlMcpServerContextIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ApplicationContext ctx;

    @Autowired
    ToolCallbackProvider toolCallbackProvider;

    @Test
    void mcpSyncServerIsAutoConfigured() {
        assertThat(ctx.getBeansOfType(McpSyncServer.class)).isNotEmpty();
    }

    @Test
    void baselineToolsAreRegistered() {
        Set<String> toolNames = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(def -> def.name())
                .collect(Collectors.toSet());

        assertThat(toolNames).contains("goaml_ping", "goaml_whoami");
    }
}
