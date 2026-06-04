package com.vyttah.goaml.service.tenant;

import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.jurisdiction.JurisdictionRepository;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.tenant.TenantRepository;
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
@Service
public class DefaultTenantProvisioningService implements TenantProvisioningService {

    private static final String INITIAL_ADMIN_ROLE = "TENANT_ADMIN";

    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DefaultTenantProvisioningService(TenantRepository tenantRepository,
                                     AppUserRepository userRepository,
                                     RoleRepository roleRepository,
                                     JurisdictionRepository jurisdictionRepository,
                                     DataSource dataSource,
                                     JdbcTemplate jdbcTemplate,
                                     PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Tenant provision(TenantProvisioningRequest request) {
        validate(request);

        UUID id = UUID.randomUUID();
        String schemaName = "tenant_" + id.toString().replace("-", "");

        createSchema(schemaName);
        try {
            migrateTenantSchema(schemaName);
            return persistTenantAndAdmin(request, id, schemaName);
        } catch (RuntimeException ex) {
            dropSchemaSafely(schemaName);
            throw ex;
        }
    }

    // ----- internals -----

    private void validate(TenantProvisioningRequest req) {
        if (req.slug() == null || req.slug().isBlank()) {
            throw new IllegalArgumentException("slug is required");
        }
        if (!jurisdictionRepository.existsById(req.jurisdictionCode())) {
            throw new IllegalArgumentException(
                    "Unknown jurisdiction code: " + req.jurisdictionCode());
        }
        if (tenantRepository.existsBySlug(req.slug())) {
            throw new IllegalStateException("Tenant slug already in use: " + req.slug());
        }
        if (userRepository.existsByEmail(req.adminEmail())) {
            throw new IllegalStateException("Email already in use: " + req.adminEmail());
        }
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
                                                 UUID id, String schemaName) {
        Tenant tenant = new Tenant(
                id, req.slug(), req.name(), req.jurisdictionCode(), schemaName, "ACTIVE");
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
