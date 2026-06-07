package com.vyttah.goaml.mcp;

/**
 * Thrown by an MCP tool when the caller is unauthenticated or lacks the role the action requires. The
 * MCP framework surfaces it to the client as a tool error (the message is safe to show — it names the
 * required roles, never any secret). This is the MCP edge's equivalent of the REST controllers'
 * {@code @PreAuthorize} denial.
 */
public class McpAccessDeniedException extends RuntimeException {

    public McpAccessDeniedException(String message) {
        super(message);
    }
}
