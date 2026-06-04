package com.vyttah.goaml.model.entity.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Shared-schema row: one of the four fixed RBAC roles
 * (SUPER_ADMIN, TENANT_ADMIN, MLRO, ANALYST). Seeded by V2 migration.
 */
@Entity
@Table(name = "role", schema = "public")
public class Role {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    @Column(nullable = false)
    private String description;

    protected Role() {}

    public Short getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
