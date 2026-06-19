package com.vyttah.goaml.model.dto.connection;

import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.tenant.Tenant;

import java.util.UUID;

/**
 * Read-only "my goAML connection" view for the authenticated user (any tenant role). Lets a sibling app (the
 * AML cockpit) show, in its own settings/profile screen, which goAML tenant it's linked to and the active
 * goAML reporting person — without exposing admin-only or secret config (no base URL, no secrets path).
 */
public final class ConnectionViews {

    private ConnectionViews() {}

    public record ConnectionView(TenantSummary tenant, Integer reportingEntityId, boolean fiuConfigured,
                                 ReportingPersonSummary activeReportingPerson) {}

    public record TenantSummary(UUID id, String slug, String name, String jurisdictionCode, String status) {
        public static TenantSummary from(Tenant t) {
            return new TenantSummary(t.getId(), t.getSlug(), t.getName(), t.getJurisdictionCode(), t.getStatus());
        }
    }

    /** The tenant's active goAML reporting person (the MLRO goAML auto-injects into every report). */
    public record ReportingPersonSummary(String firstName, String lastName, String occupation, String email,
                                         String nationality, String idNumber) {
        public static ReportingPersonSummary from(TenantGoamlPerson p) {
            return new ReportingPersonSummary(p.getFirstName(), p.getLastName(), p.getOccupation(),
                    p.getEmail(), p.getNationality(), p.getIdNumber());
        }
    }
}
