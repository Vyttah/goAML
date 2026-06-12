package com.vyttah.goaml.repository.federated;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.ConsumedAssertion;
import com.vyttah.goaml.model.entity.federated.ExternalIdentity;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1.5a.2 — verifies the V3 federated-identity migration + JPA mappings round-trip against a real Postgres,
 * including the unique constraints and the new {@code tenant_goaml_config.auto_submit} default.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class FederatedIdentityRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository appUsers;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired ExternalIdentityRepository externalIdentities;
    @Autowired TenantExternalRefRepository tenantExternalRefs;
    @Autowired TenantGoamlConfigRepository goamlConfigs;
    @Autowired ConsumedAssertionRepository consumedAssertions;

    @Test
    void roundTripsTrustedServiceExternalIdentityAndTenantRef() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "fed-tenant", "Federated FZE", "AE",
                "admin@fed.test", "Sup3rS3cret!", "Fed", "Admin"));
        AppUser admin = appUsers.findByEmail("admin@fed.test").orElseThrow();

        // trusted_service: lookup by source system
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "Vyttah accounting", "-----BEGIN PUBLIC KEY-----\nAAA\n-----END PUBLIC KEY-----",
                true, "ACTIVE"));
        TrustedService found = trustedServices.findBySourceSystem(SourceSystem.ACCOUNTING).orElseThrow();
        assertThat(found.isJitProvisioning()).isTrue();
        assertThat(found.isActive()).isTrue();

        // tenant_external_ref: resolve org ref → tenant
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.ACCOUNTING, "ORG-4711"));
        TenantExternalRef ref = tenantExternalRefs
                .findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, "ORG-4711").orElseThrow();
        assertThat(ref.getTenantId()).isEqualTo(tenant.getId());

        // external_identity: resolve source user → app_user
        externalIdentities.save(new ExternalIdentity(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "ext-1", "user@fed.test", admin.getId()));
        ExternalIdentity ident = externalIdentities
                .findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-1").orElseThrow();
        assertThat(ident.getAppUserId()).isEqualTo(admin.getId());
    }

    @Test
    void externalIdentityIsUniquePerSourceAndUser() {
        provisioningService.provision(new TenantProvisioningRequest(
                "fed-dup", "Dup FZE", "AE",
                "admin@dup.test", "Sup3rS3cret!", "Dup", "Admin"));
        AppUser admin = appUsers.findByEmail("admin@dup.test").orElseThrow();

        externalIdentities.save(new ExternalIdentity(UUID.randomUUID(), SourceSystem.SCREENING,
                "dup-user", null, admin.getId()));

        assertThatThrownBy(() -> externalIdentities.saveAndFlush(new ExternalIdentity(
                UUID.randomUUID(), SourceSystem.SCREENING, "dup-user", null, admin.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteExpiredPurgesOnlyExpiredConsumedAssertions() {
        // Called from the auth filter with no surrounding transaction — the @Transactional on the repository
        // method must open its own, or the @Modifying query throws and cleanup silently never runs.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        consumedAssertions.save(new ConsumedAssertion("it-expired-jti", SourceSystem.ACCOUNTING,
                now.minusMinutes(10)));
        consumedAssertions.save(new ConsumedAssertion("it-live-jti", SourceSystem.ACCOUNTING,
                now.plusMinutes(10)));

        int deleted = consumedAssertions.deleteExpired(now);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(consumedAssertions.existsById("it-expired-jti")).isFalse();
        assertThat(consumedAssertions.existsById("it-live-jti")).isTrue();
    }

    @Test
    void tenantGoamlConfigAutoSubmitDefaultsFalse() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "fed-cfg", "Cfg FZE", "AE",
                "admin@cfg.test", "Sup3rS3cret!", "Cfg", "Admin"));

        TenantGoamlConfig cfg = new TenantGoamlConfig(UUID.randomUUID(), tenant.getId());
        cfg.setJurisdictionCode("AE");
        cfg.setRentityId(3177);
        cfg.setBaseUrl("https://fiu.example/b2b");
        cfg.setSecretsPath("goaml/tenants/fed-cfg/fiu");
        cfg.setAuthMode("BASIC");
        goamlConfigs.save(cfg);

        TenantGoamlConfig reloaded = goamlConfigs.findByTenantId(tenant.getId()).orElseThrow();
        assertThat(reloaded.isAutoSubmit()).isFalse();
    }
}
