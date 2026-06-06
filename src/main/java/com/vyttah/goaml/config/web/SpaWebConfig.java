package com.vyttah.goaml.config.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the React SPA (Phase 13) from {@code classpath:/static/} with a client-side-routing fallback:
 * a real static file is served as-is; any other (non-API, non-actuator) path returns {@code index.html}
 * so deep links like {@code /reports/123} load the SPA shell. API + actuator paths fall through to their
 * controllers (this resolver returns {@code null} for them). The built {@code frontend/dist} is wired into
 * {@code static/} by the Gradle node task (added with the frontend scaffold); a placeholder
 * {@code index.html} ships until then.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location)
                            throws IOException {
                        // Root ("") → the SPA shell (createRelative("") would resolve the static dir).
                        if (resourcePath.isEmpty() || resourcePath.equals("index.html")) {
                            return INDEX.exists() ? INDEX : null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Let API / actuator routes 404 through their own handlers, not the SPA shell.
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        return INDEX.exists() ? INDEX : null;
                    }
                });
    }
}
