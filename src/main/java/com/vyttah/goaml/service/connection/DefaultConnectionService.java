package com.vyttah.goaml.service.connection;

import com.vyttah.goaml.model.dto.connection.ConnectionViews.ConnectionView;
import com.vyttah.goaml.model.dto.connection.ConnectionViews.ReportingPersonSummary;
import com.vyttah.goaml.model.dto.connection.ConnectionViews.TenantSummary;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.admin.AdminExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Default {@link ConnectionService}. Reads the caller's tenant (shared {@code public.tenant}), its goAML B2B
 * config (for the reporting-entity id only — never the base URL / secrets path), and its active reporting
 * person. All non-sensitive, so any authenticated tenant role may read it.
 */
@Service
@RequiredArgsConstructor
public class DefaultConnectionService implements ConnectionService {

    private final TenantRepository tenantRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final TenantGoamlPersonRepository personRepository;

    @Override
    public ConnectionView getConnection(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AdminExceptions.TenantNotFoundException("No tenant " + tenantId));
        TenantGoamlConfig config = configRepository.findByTenantId(tenantId).orElse(null);
        TenantGoamlPerson active = personRepository.findByTenantIdAndActiveTrue(tenantId).orElse(null);
        return new ConnectionView(
                TenantSummary.from(tenant),
                config != null ? config.getRentityId() : null,
                config != null,
                active != null ? ReportingPersonSummary.from(active) : null);
    }
}
