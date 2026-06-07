package com.vyttah.goaml.mcp;

import com.vyttah.goaml.config.tenant.TenantContext;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Hooks;

/**
 * Bridges the request's auth + tenant context across the MCP server's reactive thread hop.
 *
 * <p>The Spring AI MCP WebMvc transport handles a tool call by {@code McpServerSession.handle(msg).block()}
 * <em>on the servlet thread</em> — the thread on which {@code JwtAuthFilter} has set the SecurityContext
 * and {@link TenantContext}. But the sync server executes the actual {@code @Tool} method on a Reactor
 * scheduler thread, where those {@code ThreadLocal}s are absent. Without this bridge, tools would run
 * unauthenticated / untenanted (and JPA would route to {@code public}).
 *
 * <p>Reactor's automatic context propagation captures the registered {@code ThreadLocal}s into the
 * reactive {@code Context} at subscription (the servlet thread, where they are set) and restores them
 * around operators that run on other threads (where the tool executes). We register accessors for the
 * Spring SecurityContext and for {@link TenantContext}, then enable the hook. Registration is keyed and
 * idempotent, so repeated application contexts (e.g. across tests) are safe.
 */
@Configuration
public class McpContextPropagationConfig {

    static final String SECURITY_CONTEXT_KEY = "goaml.security.context";
    static final String TENANT_CONTEXT_KEY = "goaml.tenant.schema";

    @PostConstruct
    void registerAccessorsAndEnablePropagation() {
        ContextRegistry registry = ContextRegistry.getInstance();

        registry.registerThreadLocalAccessor(
                SECURITY_CONTEXT_KEY,
                SecurityContextHolder::getContext,
                SecurityContextHolder::setContext,
                SecurityContextHolder::clearContext);

        registry.registerThreadLocalAccessor(
                TENANT_CONTEXT_KEY,
                TenantContext::get,
                TenantContext::set,
                TenantContext::clear);

        // Global, idempotent: instruments Reactor operators to carry the captured ThreadLocals across
        // scheduler boundaries. Required for the MCP tool thread to see the caller's tenant + role.
        Hooks.enableAutomaticContextPropagation();
    }
}
