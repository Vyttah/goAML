package com.vyttah.goaml.model.mapper.tenant;

import com.vyttah.goaml.model.dto.tenant.TenantDto;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the MapStruct wiring end-to-end: the annotation processor generates a working
 * {@code TenantMapperImpl} and can read the Lombok-generated getters on {@link Tenant}.
 * No Spring context / DB needed — pure POJO mapping.
 */
class TenantMapperTest {

    private final TenantMapper mapper = Mappers.getMapper(TenantMapper.class);

    @Test
    void mapsTenantEntityToDto() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant(id, "alpha-jewellers", "Alpha Jewellers FZE", "AE",
                "tenant_abc123", "ACTIVE");

        TenantDto dto = mapper.toDto(tenant);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.slug()).isEqualTo("alpha-jewellers");
        assertThat(dto.name()).isEqualTo("Alpha Jewellers FZE");
        assertThat(dto.jurisdictionCode()).isEqualTo("AE");
        assertThat(dto.schemaName()).isEqualTo("tenant_abc123");
        assertThat(dto.status()).isEqualTo("ACTIVE");
    }
}
