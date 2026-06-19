package com.vyttah.goaml.service.connection;

import com.vyttah.goaml.model.dto.connection.ConnectionViews.ConnectionView;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.admin.AdminExceptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link DefaultConnectionService}: assembles the read-only connection view from the three
 *  repos, with config + active person, without them, and the unknown-tenant guard. */
class DefaultConnectionServiceTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final TenantGoamlConfigRepository configRepository = mock(TenantGoamlConfigRepository.class);
    private final TenantGoamlPersonRepository personRepository = mock(TenantGoamlPersonRepository.class);
    private final DefaultConnectionService service =
            new DefaultConnectionService(tenantRepository, configRepository, personRepository);

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void buildsFullConnectionWithConfigAndActivePerson() {
        Tenant t = mock(Tenant.class);
        when(t.getId()).thenReturn(tenantId);
        when(t.getSlug()).thenReturn("demo");
        when(t.getName()).thenReturn("Demo Dealers FZE");
        when(t.getJurisdictionCode()).thenReturn("AE");
        when(t.getStatus()).thenReturn("ACTIVE");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));

        TenantGoamlConfig c = mock(TenantGoamlConfig.class);
        when(c.getRentityId()).thenReturn(3177);
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(c));

        TenantGoamlPerson p = mock(TenantGoamlPerson.class);
        when(p.getFirstName()).thenReturn("Aisha");
        when(p.getLastName()).thenReturn("Khan");
        when(p.getOccupation()).thenReturn("MLRO");
        when(personRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(Optional.of(p));

        ConnectionView v = service.getConnection(tenantId);

        assertThat(v.tenant().slug()).isEqualTo("demo");
        assertThat(v.tenant().name()).isEqualTo("Demo Dealers FZE");
        assertThat(v.reportingEntityId()).isEqualTo(3177);
        assertThat(v.fiuConfigured()).isTrue();
        assertThat(v.activeReportingPerson().firstName()).isEqualTo("Aisha");
        assertThat(v.activeReportingPerson().occupation()).isEqualTo("MLRO");
    }

    @Test
    void buildsConnectionWithNoConfigOrActivePerson() {
        Tenant t = mock(Tenant.class);
        when(t.getSlug()).thenReturn("demo");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(personRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(Optional.empty());

        ConnectionView v = service.getConnection(tenantId);

        assertThat(v.fiuConfigured()).isFalse();
        assertThat(v.reportingEntityId()).isNull();
        assertThat(v.activeReportingPerson()).isNull();
    }

    @Test
    void rejectsUnknownTenant() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getConnection(tenantId))
                .isInstanceOf(AdminExceptions.TenantNotFoundException.class);
    }
}
