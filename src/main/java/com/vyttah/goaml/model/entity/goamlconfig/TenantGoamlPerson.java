package com.vyttah.goaml.model.entity.goamlconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-tenant goAML reporting person — the filing MLRO (shared {@code public.tenant_goaml_person}, alongside
 * {@link TenantGoamlConfig}). Stored as a tenant default so goAML auto-injects it into every report and callers
 * (the AML cockpit, CSV import, the accounting/screening feeds) need not send it — mirrors LexAML's "GoAML
 * Person" setting.
 *
 * <p>Multiple rows may exist per tenant (rotating MLROs), but at most one is {@link #active} (enforced by a
 * partial unique index); the active one is the default injected at report-build time. Maps onto the curated
 * {@code DpmsrCreateRequest.Person} reporting-person slot.
 */
@Getter
@Entity
@Table(name = "tenant_goaml_person", schema = "public")
public class TenantGoamlPerson {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Setter
    @Column(name = "first_name", nullable = false, length = 255)
    private String firstName;

    @Setter
    @Column(name = "last_name", nullable = false, length = 255)
    private String lastName;

    @Setter
    @Column(length = 8)
    private String gender;

    @Setter
    @Column(length = 64)
    private String ssn;

    @Setter
    @Column(name = "id_number", length = 64)
    private String idNumber;

    @Setter
    @Column(length = 8)
    private String nationality;

    @Setter
    @Column(length = 255)
    private String email;

    @Setter
    @Column(length = 255)
    private String occupation;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantGoamlPerson() {}

    public TenantGoamlPerson(UUID id, UUID tenantId, String firstName, String lastName) {
        this.id = id;
        this.tenantId = tenantId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
