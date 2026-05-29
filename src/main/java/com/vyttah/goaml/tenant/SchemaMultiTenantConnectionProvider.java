package com.vyttah.goaml.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Hands Hibernate a connection whose Postgres {@code search_path} is set to the requested
 * tenant schema, then resets it back to {@code public} on release. Combined with
 * {@link TenantIdentifierResolver} this gives us schema-per-tenant routing on a single
 * shared {@link DataSource}.
 *
 * <p>Shared/admin tables are annotated with {@code @Table(schema = "public")} so they
 * resolve to {@code public} regardless of the current search_path — keeping platform
 * data and per-tenant data cleanly separated.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + quote(tenantIdentifier));
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO public");
        }
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    private static String quote(String identifier) {
        // Defence-in-depth — schema names are derived from a UUID hex so unsafe characters
        // shouldn't occur, but quoting is cheap and prevents any accidental injection.
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
