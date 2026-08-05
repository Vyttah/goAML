package com.vyttah.goaml.model.dto.integration;

import java.util.List;

/**
 * Reply to a bulk tenant-existence query ({@link IntegrationTenantStatusRequest}): the subset of the requested
 * companyIds that already have a provisioned goAML tenant. Values are echoed back in the caller's original
 * casing so the admin service can map them straight back to its companies.
 *
 * @param provisionedCompanyIds requested companyIds that resolve to an existing goAML tenant
 */
public record IntegrationTenantStatusResponse(List<String> provisionedCompanyIds) {
}
