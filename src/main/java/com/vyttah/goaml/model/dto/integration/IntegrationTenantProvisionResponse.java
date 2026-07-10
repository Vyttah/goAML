package com.vyttah.goaml.model.dto.integration;

import com.vyttah.goaml.model.dto.admin.AdminViews.TenantView;

/**
 * Response for the integration tenant-provisioning endpoint.
 *
 * @param tenant  the newly created tenant
 * @param warning non-null when the provided email was already in use and a placeholder was substituted;
 *                the TENANT_ADMIN email should be corrected via the admin panel before first login
 */
public record IntegrationTenantProvisionResponse(TenantView tenant, String warning) {
}
