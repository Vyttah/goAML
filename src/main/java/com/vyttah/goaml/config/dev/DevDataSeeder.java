package com.vyttah.goaml.config.dev;

import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>It also seeds the <strong>suite-integration</strong> fixtures so the AML→goAML push can be exercised
 * locally end-to-end: the AML screening software as a {@code trusted_service} (a fixed dev RSA public key),
 * a {@code company_id → demo tenant} mapping, and an active goAML reporting person (so reports auto-fill the
 * MLRO — Phase A). The matching dev <em>private</em> key lives with the AML customer-service dev config; it
 * authorises pushes to a <em>local dev seed only</em> and has zero production value.
 *
 * <p><strong>Gated OFF by default</strong> — only runs when {@code goaml.dev.seed.enabled=true}
 * (env {@code GOAML_DEV_SEED=true}). NEVER enable in a deployed environment. Idempotent: every block
 * re-checks existence, so re-runs are no-ops. The fixed password below is intentionally weak and local-only.
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

    /**
     * Fixed DEV-ONLY RSA public key for the AML screening service assertion (the private half is in the AML
     * customer-service dev config). Local-only; regenerate both halves together if rotated. NOT a secret.
     */
    private static final String DEV_SCREENING_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAop98Vt2bu85t2bURDaIq
            rmj1GHsegKdqq07kVRT1ce3a/OqJB76ZMhZo/NMASIjWVO8A5vIfj7jq1h0B0dIg
            fZtxyAQ8MOnvkQM7io4GiCfrFDRGH4O533lzO6oyz324chHlgSeehOwJ2pF+0Klp
            BA3By4jZzE2ZajX++HjPPSJlVOTLuvytuP3f1eZPYDuYLNitjxDmwFkQD/YWNAQf
            tykiWiOtskHD6MWDFU+N05krlGHrsWA1Q1XkiF+s51gq/eTeywyQJGv1z2aeJoVK
            K/ZVTnT2z722Op87V/ZRk8Lu3VXaMPiC13sPfRpk4AQmOGny3cpUNL+fm6gmt0Be
            3QIDAQAB
            -----END PUBLIC KEY-----""";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TenantProvisioningService provisioning;
    private final TrustedServiceRepository trustedServices;
    private final TenantExternalRefRepository tenantExternalRefs;
    private final TenantGoamlPersonRepository goamlPersons;
    private final TenantGoamlConfigRepository goamlConfigs;

    /**
     * The AML company id mapped to the demo tenant (screening-push tenant resolution). Defaults to {@code 1001};
     * set {@code GOAML_DEV_SCREENING_COMPANY_ID} to your real AML company id (a UUID) for an end-to-end test.
     */
    @Value("${goaml.dev.screening-company-id:1001}")
    private String screeningCompanyId;

    @Override
    public void run(String... args) {
        if (!users.existsByEmail(SUPER_ADMIN_EMAIL)) {
            createUser(null, SUPER_ADMIN_EMAIL, "Super", "Admin", "SUPER_ADMIN");
            log.info("[dev-seed] created SUPER_ADMIN {}", SUPER_ADMIN_EMAIL);
        }

        // The demo tenant: provisioning creates the tenant schema + its initial TENANT_ADMIN.
        UUID tenantId = resolveOrProvisionDemoTenant();

        // Tenant users that can actually exercise the report UI (MLRO builds + submits; ANALYST builds).
        if (!users.existsByEmail(MLRO_EMAIL)) {
            createUser(tenantId, MLRO_EMAIL, "Demo", "Mlro", "MLRO");
            createUser(tenantId, ANALYST_EMAIL, "Demo", "Analyst", "ANALYST");
            log.info("[dev-seed] created tenant users {} (MLRO) and {} (ANALYST)", MLRO_EMAIL, ANALYST_EMAIL);
        }

        seedSuiteIntegration(tenantId);
        warnIfNoGoamlConfig(tenantId);
        logBanner();
    }

    /**
     * D8 foot-gun guard: the seeder deliberately does NOT create a {@code tenant_goaml_config} row (the real
     * {@code rentity_id} + FIU endpoint are environment-specific and must be set via the admin UI). Without
     * one, a built report gets {@code rentity_id=0} and validates INVALID — silently, in fresh envs. Emit a
     * loud WARN so a reviewer knows to set the config before expecting a VALID report.
     */
    private void warnIfNoGoamlConfig(UUID tenantId) {
        if (goamlConfigs.findByTenantId(tenantId).isEmpty()) {
            log.warn("[dev-seed] demo tenant {} has NO tenant_goaml_config — reports will validate INVALID "
                    + "(rentity_id=0) until an admin sets one (POST /api/v1/admin/goaml-config). This is "
                    + "expected on a fresh dev seed; set a real rentity_id + FIU base URL to file successfully.",
                    tenantId);
        }
    }

    private UUID resolveOrProvisionDemoTenant() {
        return users.findByEmail(TENANT_ADMIN_EMAIL)
                .map(AppUser::getTenantId)
                .orElseGet(() -> {
                    Tenant tenant = provisioning.provision(new TenantProvisioningRequest(
                            DEMO_SLUG, "Demo Dealers FZE", "AE",
                            TENANT_ADMIN_EMAIL, PASSWORD, "Demo", "Admin"));
                    log.info("[dev-seed] provisioned demo tenant '{}' ({})", DEMO_SLUG, tenant.getId());
                    return tenant.getId();
                });
    }

    /** Idempotent suite-integration fixtures so the AML→goAML screening push works against the local seed. */
    private void seedSuiteIntegration(UUID tenantId) {
        if (trustedServices.findBySourceSystem(SourceSystem.SCREENING).isEmpty()) {
            // A5: jit_provisioning=true + default_role=ANALYST. A federated cockpit user hitting
            // POST /auth/federated/token is auto-created as an ANALYST (least privilege) in the demo tenant —
            // they can build/validate but NOT approve or submit. MLRO is granted only by explicit goAML admin
            // action, so segregation-of-duties is preserved on the live cockpit path (no auto-MLRO).
            trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                    "AML screening software (dev)", DEV_SCREENING_PUBLIC_KEY_PEM, true, "ACTIVE", "ANALYST"));
            log.info("[dev-seed] registered SCREENING trusted_service (dev public key, JIT→ANALYST)");
        }
        if (tenantExternalRefs.findBySourceSystemAndExternalOrgRef(
                SourceSystem.SCREENING, screeningCompanyId).isEmpty()) {
            tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenantId,
                    SourceSystem.SCREENING, screeningCompanyId));
            log.info("[dev-seed] mapped SCREENING company {} -> demo tenant", screeningCompanyId);
        }
        if (goamlPersons.findByTenantIdAndActiveTrue(tenantId).isEmpty()) {
            TenantGoamlPerson person = new TenantGoamlPerson(UUID.randomUUID(), tenantId, "Demo", "Mlro");
            person.setGender("M");
            person.setNationality("AE");
            person.setIdNumber("784-DEV-0001");
            person.setEmail(MLRO_EMAIL);
            person.setOccupation("Compliance Officer");
            goamlPersons.save(person);
            log.info("[dev-seed] set active goAML reporting person for the demo tenant");
        }
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
                 Login takes companyId + email + password (POST /api/v1/auth/login)
                ------------------------------------------------------------
                  SUPER_ADMIN   companyId 'PLATFORM'  {}   (platform admin; no tenant)
                  TENANT_ADMIN  companyId '{}'      {}        (manage users + goAML config)
                  MLRO          companyId '{}'      {}         (build + submit reports)
                  ANALYST       companyId '{}'      {}      (build reports)
                 Suite integration: SCREENING company id '{}' -> demo tenant
                   (AML screening push: POST /api/v1/integration/screening/subjects)
                 NEVER enable goaml.dev.seed in a deployed environment.
                ============================================================""",
                PASSWORD, SUPER_ADMIN_EMAIL, DEMO_SLUG, TENANT_ADMIN_EMAIL, DEMO_SLUG, MLRO_EMAIL,
                DEMO_SLUG, ANALYST_EMAIL, screeningCompanyId);
    }
}
