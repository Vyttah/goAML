package com.vyttah.goaml.model.mapper.tenant;

import com.vyttah.goaml.model.dto.tenant.TenantDto;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for the {@code tenant} feature — converts the {@link Tenant} entity to its read
 * {@link TenantDto} so controllers never expose the entity. {@code componentModel = "spring"} makes the
 * generated implementation a Spring bean.
 */
@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantDto toDto(Tenant tenant);
}
