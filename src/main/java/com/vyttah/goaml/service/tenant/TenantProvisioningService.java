package com.vyttah.goaml.service.tenant;

import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.tenant.Tenant;

/**
 * Provisions a new tenant atomically (schema create + Flyway migrate + tenant/admin rows).
 * Implemented by {@link DefaultTenantProvisioningService}.
 */
public interface TenantProvisioningService {

    Tenant provision(TenantProvisioningRequest request);
}
