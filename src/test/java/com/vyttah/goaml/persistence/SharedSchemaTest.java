package com.vyttah.goaml.persistence;

import com.vyttah.goaml.GoamlApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the shared-schema Flyway migration runs and seeds the expected reference rows.
 *
 * <p>Covers Phase 2 sub-step 2.1: the {@code public} schema holds platform tables
 * (tenant, app_user, role, user_role, jurisdiction, tenant_goaml_config, refresh_token)
 * and reference data (the four RBAC roles + UAE jurisdiction).
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class SharedSchemaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void jurisdictionTableSeededWithUae() {
        String name = jdbc.queryForObject(
                "SELECT name FROM public.jurisdiction WHERE code = ?",
                String.class, "AE");
        String currency = jdbc.queryForObject(
                "SELECT currency_code FROM public.jurisdiction WHERE code = ?",
                String.class, "AE");

        assertThat(name).isEqualTo("United Arab Emirates");
        assertThat(currency).isEqualTo("AED");
    }

    @Test
    void roleTableSeededWithFourRbacRoles() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.role WHERE name IN ('SUPER_ADMIN','TENANT_ADMIN','MLRO','ANALYST')",
                Long.class);

        assertThat(count).isEqualTo(4L);
    }

    @Test
    void coreSharedTablesExist() {
        Long tables = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN ('tenant','app_user','role','user_role',
                                       'jurisdiction','tenant_goaml_config','refresh_token')
                """, Long.class);

        assertThat(tables).isEqualTo(7L);
    }
}
