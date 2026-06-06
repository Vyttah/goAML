package com.vyttah.goaml.config.dev;

import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * LOCAL-ONLY developer convenience: on startup, seed a SUPER_ADMIN, a demo tenant, and ready-to-use
 * tenant logins so the SPA can be reviewed without the provisioning chicken-and-egg (no users are
 * seeded by Flyway, and provisioning a tenant otherwise needs an existing SUPER_ADMIN token).
 *
 * <p><strong>Gated OFF by default</strong> — only runs when {@code goaml.dev.seed.enabled=true}
 * (env {@code GOAML_DEV_SEED=true}). NEVER enable in a deployed environment. Idempotent: re-runs are
 * no-ops once the demo users exist. The fixed password below is intentionally weak and local-only.
 */
@Component
@ConditionalOnProperty(name = "goaml.dev.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** Shared local password for every seeded account. */
    private static final String PASSWORD = "Passw0rd!";
    private static final String SUPER_ADMIN_EMAIL = "superadmin@goaml.local";
    private static final String TENANT_ADMIN_EMAIL = "admin@demo.local";
    private static final String MLRO_EMAIL = "mlro@demo.local";
    private static final String ANALYST_EMAIL = "analyst@demo.local";
    private static final String DEMO_SLUG = "demo";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TenantProvisioningService provisioning;

    @Override
    public void run(String... args) {
        if (users.existsByEmail(MLRO_EMAIL)) {
            log.info("[dev-seed] demo data already present — skipping");
            logBanner();
            return;
        }

        if (!users.existsByEmail(SUPER_ADMIN_EMAIL)) {
            createUser(null, SUPER_ADMIN_EMAIL, "Super", "Admin", "SUPER_ADMIN");
            log.info("[dev-seed] created SUPER_ADMIN {}", SUPER_ADMIN_EMAIL);
        }

        // The demo tenant: provisioning creates the tenant schema + its initial TENANT_ADMIN.
        UUID tenantId;
        if (!users.existsByEmail(TENANT_ADMIN_EMAIL)) {
            Tenant tenant = provisioning.provision(new TenantProvisioningRequest(
                    DEMO_SLUG, "Demo Dealers FZE", "AE",
                    TENANT_ADMIN_EMAIL, PASSWORD, "Demo", "Admin"));
            tenantId = tenant.getId();
            log.info("[dev-seed] provisioned demo tenant '{}' ({})", DEMO_SLUG, tenantId);
        } else {
            tenantId = users.findByEmail(TENANT_ADMIN_EMAIL).orElseThrow().getTenantId();
        }

        // Tenant users that can actually exercise the report UI (MLRO builds + submits; ANALYST builds).
        createUser(tenantId, MLRO_EMAIL, "Demo", "Mlro", "MLRO");
        createUser(tenantId, ANALYST_EMAIL, "Demo", "Analyst", "ANALYST");
        log.info("[dev-seed] created tenant users {} (MLRO) and {} (ANALYST)", MLRO_EMAIL, ANALYST_EMAIL);

        logBanner();
    }

    private void createUser(UUID tenantId, String email, String firstName, String lastName, String roleName) {
        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        AppUser user = new AppUser(UUID.randomUUID(), tenantId, email,
                passwordEncoder.encode(PASSWORD), firstName, lastName, "ACTIVE");
        user.addRole(role);
        users.save(user);
    }

    private void logBanner() {
        log.info("""

                ============================================================
                 goAML DEV SEED — local login credentials (password: {})
                ------------------------------------------------------------
                  SUPER_ADMIN   {}   (platform admin; no tenant)
                  TENANT_ADMIN  {}        (manage users + goAML config)
                  MLRO          {}         (build + submit reports)
                  ANALYST       {}      (build reports)
                 Demo tenant slug: '{}'  ·  POST /api/v1/auth/login
                 NEVER enable goaml.dev.seed in a deployed environment.
                ============================================================""",
                PASSWORD, SUPER_ADMIN_EMAIL, TENANT_ADMIN_EMAIL, MLRO_EMAIL, ANALYST_EMAIL, DEMO_SLUG);
    }
}
