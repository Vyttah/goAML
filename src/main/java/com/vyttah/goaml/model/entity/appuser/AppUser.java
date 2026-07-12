package com.vyttah.goaml.model.entity.appuser;

import com.vyttah.goaml.config.tenant.CurrentTenantFilterResolver;
import com.vyttah.goaml.model.entity.role.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Platform user. {@code tenantId} is null for SUPER_ADMIN platform staff and non-null for
 * users belonging to a single client tenant. Roles are joined via {@code public.user_role}.
 *
 * <p>Email is unique <em>per tenant</em> (DB: partial unique indexes, see
 * {@code V9__email_unique_per_tenant.sql}), so the same address can belong to two tenants; login
 * disambiguates via the company id (tenant slug). {@code app_user} lives in shared {@code public}, so
 * schema-per-tenant routing gives it no isolation — the auto-enabled {@code tenantFilter} scopes every
 * query to {@link com.vyttah.goaml.config.tenant.TenantContext#getTenantId() the bound tenant}, driven by
 * {@link CurrentTenantFilterResolver}. The condition short-circuits to all rows for the resolver's
 * {@code UNSCOPED} sentinel (login / SUPER_ADMIN / cross-tenant jobs). Direct {@code find()}-by-id loads
 * are not filtered by Hibernate, so admin lookups by user id still work cross-tenant.
 */
@Getter
@Entity
@Table(name = "app_user", schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "app_user_email_tenant_uk",
                columnNames = {"tenant_id", "email"}))
@FilterDef(name = "tenantFilter", autoEnabled = true,
        parameters = @ParamDef(name = "tenantId", type = UUID.class,
                resolver = CurrentTenantFilterResolver.class))
@Filter(name = "tenantFilter",
        condition = "(:tenantId = cast('00000000-0000-0000-0000-000000000000' as uuid) or tenant_id = :tenantId)")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    // Unique per tenant, not globally — see the class-level @UniqueConstraint + V9 partial indexes.
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role", schema = "public",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected AppUser() {}

    public AppUser(UUID id, UUID tenantId, String email, String passwordHash,
                         String firstName, String lastName, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
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

    public void addRole(Role role) {
        roles.add(role);
    }

    /** Replace the user's role set with exactly one role (TENANT_ADMIN edit). */
    public void setSingleRole(Role role) {
        roles.clear();
        roles.add(role);
    }

    /** Update the editable profile fields (TENANT_ADMIN edit). */
    public void rename(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /** Change the login email (federated upsert from the AML side keeps it in sync). Unique per tenant. */
    public void changeEmail(String email) {
        this.email = email;
    }

    /** ACTIVE / DISABLED. A DISABLED user cannot authenticate ({@link UserPrincipal#isEnabled()}). */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Replace the password hash (admin password reset). Pass an already-encoded hash. */
    public void changePassword(String encodedHash) {
        this.passwordHash = encodedHash;
    }
}
