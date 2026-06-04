package com.vyttah.goaml.model.entity.jurisdiction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Shared-schema row: one per FIU instance the platform can target.
 * Only {@code AE} ships in v1 (seeded by V2 migration).
 */
@Entity
@Table(name = "jurisdiction", schema = "public")
public class Jurisdiction {

    @Id
    @Column(length = 8, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Jurisdiction() {}

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCurrencyCode() { return currencyCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
