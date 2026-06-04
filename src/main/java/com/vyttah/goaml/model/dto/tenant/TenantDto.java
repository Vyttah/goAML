package com.vyttah.goaml.model.dto.tenant;

import java.util.UUID;

/**
 * Read DTO for a {@link com.vyttah.goaml.model.entity.tenant.Tenant} — what admin endpoints return
 * instead of the JPA entity. Mapped by {@link com.vyttah.goaml.model.mapper.tenant.TenantMapper}.
 */
public record TenantDto(UUID id, String slug, String name, String jurisdictionCode,
                        String schemaName, String status) {}
