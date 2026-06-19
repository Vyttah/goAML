package com.vyttah.goaml.model.entity.appuser;

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
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Platform user. {@code tenantId} is null for SUPER_ADMIN platform staff and non-null for
 * users belonging to a single client tenant. Roles are joined via {@code public.user_role}.
 */
@Getter
@Entity
@Table(name = "app_user", schema = "public")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, unique = true)
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

    /** ACTIVE / DISABLED. A DISABLED user cannot authenticate ({@link UserPrincipal#isEnabled()}). */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Replace the password hash (admin password reset). Pass an already-encoded hash. */
    public void changePassword(String encodedHash) {
        this.passwordHash = encodedHash;
    }
}
