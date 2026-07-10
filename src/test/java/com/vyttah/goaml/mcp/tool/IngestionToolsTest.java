package com.vyttah.goaml.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.ingestion.ImportJobView;
import com.vyttah.goaml.model.entity.ingestion.ImportJob;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.ingestion.IngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IngestionTools}: the {@link IngestionService} is mocked, a real {@link ObjectMapper}
 * deserialises the job results. Verifies delegation (with tenant/actor from the identity) and RBAC.
 */
class IngestionToolsTest {

    private final IngestionService ingestionService = mock(IngestionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final IngestionTools tools = new IngestionTools(ingestionService, objectMapper);

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticate(List<String> roles) {
        UserPrincipal principal = new UserPrincipal(USER_ID, TENANT_ID, "officer@demo.local", "", true, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set("tenant_demo");
    }

    private ImportJob job(String status) {
        ImportJob job = mock(ImportJob.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(job.getStatus()).thenReturn(status);
        when(job.getResults()).thenReturn(null); // → empty row list
        return job;
    }

    @Test
    void importXmlDelegatesWithTenantAndActor() {
        authenticate(List.of("ANALYST"));
        ImportJob completed = job("COMPLETED");
        when(ingestionService.importXml(any(), eq("batch.xml"), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(completed);

        ImportJobView view = tools.importXml("batch.xml", "<reports/>");

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(ingestionService).importXml(any(), eq("batch.xml"), eq(TENANT_ID), eq(USER_ID));
    }

    @Test
    void importCsvDelegates() {
        authenticate(List.of("MLRO"));
        ImportJob partial = job("PARTIAL");
        when(ingestionService.importCsv(any(), eq("dpmsr.csv"), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(partial);

        ImportJobView view = tools.importCsv("dpmsr.csv", "entity_reference,...\nX,...");

        assertThat(view.status()).isEqualTo("PARTIAL");
        verify(ingestionService).importCsv(any(), eq("dpmsr.csv"), eq(TENANT_ID), eq(USER_ID));
    }

    @Test
    void importAllowedForTenantAdmin() {
        authenticate(List.of("TENANT_ADMIN"));
        ImportJob completed = job("COMPLETED");
        when(ingestionService.importXml(any(), eq("b.xml"), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(completed);

        ImportJobView view = tools.importXml("b.xml", "<x/>");

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(ingestionService).importXml(any(), eq("b.xml"), eq(TENANT_ID), eq(USER_ID));
    }

    @Test
    void listImportsDelegates() {
        authenticate(List.of("TENANT_ADMIN"));
        ImportJob completed = job("COMPLETED");
        when(ingestionService.list()).thenReturn(List.of(completed));

        assertThat(tools.listImports()).hasSize(1);
    }

    @Test
    void getImportDelegates() {
        authenticate(List.of("ANALYST"));
        UUID id = UUID.randomUUID();
        ImportJob completed = job("COMPLETED");
        when(ingestionService.get(id)).thenReturn(completed);

        assertThat(tools.getImport(id.toString()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void getImportRejectsNonUuid() {
        authenticate(List.of("ANALYST"));

        assertThatThrownBy(() -> tools.getImport("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }
}
