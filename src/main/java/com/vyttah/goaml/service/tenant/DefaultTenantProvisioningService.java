package com.vyttah.goaml.service.tenant;

import com.vyttah.goaml.config.tenant.TenantIdentifierResolver;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.jurisdiction.JurisdictionRepository;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Provisions a new tenant atomically:
 *
 * <ol>
 *   <li>validate jurisdiction + slug uniqueness</li>
 *   <li>{@code CREATE SCHEMA tenant_<id_hex>}</li>
 *   <li>run the per-tenant Flyway migrations against that schema</li>
 *   <li>insert the {@link Tenant} row in {@code public.tenant}</li>
 *   <li>create the initial TENANT_ADMIN user</li>
 * </ol>
 *
 * <p>If anything after the {@code CREATE SCHEMA} fails the schema is dropped to avoid orphans.
 * The DB inserts run in a single Spring transaction so they roll back together.
 */
@RequiredArgsConstructor
@Service
public class DefaultTenantProvisioningService implements TenantProvisioningService {

    private static final String INITIAL_ADMIN_ROLE = "TENANT_ADMIN";
    /**
     * A company id (slug) is a URL-safe token — it also becomes the Postgres schema name. Underscores and
     * upper-case are accepted to match the AML suite's company-id format; the value is normalized to
     * lower-case on storage (identity is case-insensitive) so login lookups and the schema name stay
     * consistent.
     */
    private static final java.util.regex.Pattern SLUG_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{3,64}$");
    /** Reserved company ids that can never be a real tenant (collide with login sentinel / default schema). */
    private static final java.util.Set<String> RESERVED_SLUGS = java.util.Set.of(
            TenantIdentifierResolver.PLATFORM_COMPANY_ID.toLowerCase(),
            TenantIdentifierResolver.DEFAULT_TENANT, "default");

    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Tenant provision(TenantProvisioningRequest request) {
        validate(request);

        UUID id = UUID.randomUUID();
        // Normalize to lower-case: company ids are case-insensitive, and login looks up the tenant with the
        // lower-cased company id, so we store the slug (and derive the schema) lower-case.
        String slug = request.slug().toLowerCase();
        // Schema name is derived from the company id (slug) so it's directly identifiable in the DB
        // (tenant_acme_gold) rather than an opaque UUID. Hyphens → underscores keep it a valid identifier;
        // the slug is validated to [A-Za-z0-9_-] then lower-cased, so this yields [a-z0-9_]. Existing tenants
        // keep their historical tenant_<uuid> schema (the name is stored per row).
        String schemaName = "tenant_" + slug.replace('-', '_');

        createSchema(schemaName);
        try {
            migrateTenantSchema(schemaName);
            return persistTenantAndAdmin(request, id, slug, schemaName);
        } catch (RuntimeException ex) {
            dropSchemaSafely(schemaName);
            throw ex;
        }
    }

    // ----- internals -----

    private void validate(TenantProvisioningRequest req) {
        if (req.slug() == null || req.slug().isBlank()) {
            throw new IllegalArgumentException("slug (company id) is required");
        }
        if (!SLUG_PATTERN.matcher(req.slug()).matches()) {
            throw new IllegalArgumentException(
                    "slug (company id) must match " + SLUG_PATTERN.pattern() + ", was: " + req.slug());
        }
        if (RESERVED_SLUGS.contains(req.slug().toLowerCase())) {
            throw new IllegalArgumentException("slug (company id) is reserved: " + req.slug());
        }
        if (!jurisdictionRepository.existsById(req.jurisdictionCode())) {
            throw new IllegalArgumentException(
                    "Unknown jurisdiction code: " + req.jurisdictionCode());
        }
        // Check uniqueness against the normalized (lower-cased) slug — identity is case-insensitive.
        if (tenantRepository.existsBySlug(req.slug().toLowerCase())) {
            throw new IllegalStateException("Tenant slug already in use: " + req.slug());
        }
        // No global admin-email check: email is unique only per tenant now, and this is a brand-new tenant
        // (its own partition is empty). The per-tenant unique index still backstops any duplicate.
    }

    private void createSchema(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA \"" + schemaName.replace("\"", "\"\"") + "\"");
    }

    private void dropSchemaSafely(String schemaName) {
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS \""
                    + schemaName.replace("\"", "\"\"") + "\" CASCADE");
        } catch (RuntimeException ignored) {
            // best-effort cleanup; surface the original failure to the caller
        }
    }

    private void migrateTenantSchema(String schemaName) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration/tenant")
                .load()
                .migrate();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Tenant persistTenantAndAdmin(TenantProvisioningRequest req,
                                                 UUID id, String slug, String schemaName) {
        Tenant tenant = new Tenant(
                id, slug, req.name(), req.jurisdictionCode(), schemaName, "ACTIVE");
        tenantRepository.save(tenant);

        Role adminRole = roleRepository.findByName(INITIAL_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed role missing: " + INITIAL_ADMIN_ROLE));

        AppUser admin = new AppUser(
                UUID.randomUUID(), id, req.adminEmail(),
                passwordEncoder.encode(req.adminPassword()),
                req.adminFirstName(), req.adminLastName(), "ACTIVE");
        admin.addRole(adminRole);
        userRepository.save(admin);

        return tenant;
    }
}
