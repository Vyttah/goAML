package com.vyttah.goaml.mcp;

import com.vyttah.goaml.mcp.tool.AdminTools;
import com.vyttah.goaml.mcp.tool.IngestionTools;
import com.vyttah.goaml.mcp.tool.LookupTools;
import com.vyttah.goaml.mcp.tool.ReportTools;
import com.vyttah.goaml.mcp.tool.SubmissionTools;
import com.vyttah.goaml.mcp.tool.SystemTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the goAML MCP tool surface.
 *
 * <p>Every {@code @Tool}-annotated bean handed to the {@link MethodToolCallbackProvider} is
 * auto-discovered by the Spring AI MCP server starter and exposed over the SSE/HTTP transport. The
 * tools delegate to existing engine/services — the MCP layer adds <em>no</em> business logic, so
 * REST, MCP, and CLI stay at parity (same validation, same RBAC, same audit). Later Phase-12 steps
 * append their tool objects to this provider.
 */
@Configuration
public class GoamlMcpServerConfig {

    @Bean
    public ToolCallbackProvider goamlMcpTools(SystemTools systemTools, LookupTools lookupTools,
                                              ReportTools reportTools, SubmissionTools submissionTools,
                                              IngestionTools ingestionTools, AdminTools adminTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(systemTools, lookupTools, reportTools, submissionTools, ingestionTools, adminTools)
                .build();
    }
}
