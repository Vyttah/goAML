package com.vyttah.goaml.model.dto.integration;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Bulk tenant-existence query. The AML admin service sends the companyIds it wants to check (typically the
 * companies on one admin-panel list page); goAML replies with the subset that already have a provisioned
 * tenant. Lets the admin panel show a per-company goAML status without an assertion+call per row.
 *
 * <p>Unlike the provisioning/user endpoints this is <strong>not</strong> tenant-scoped — it deliberately spans
 * many companyIds, so the caller does not rely on the assertion's {@code org} claim here.
 *
 * @param companyIds the AML companyIds to check (matched case-insensitively against goAML tenant slugs)
 */
public record IntegrationTenantStatusRequest(@NotNull List<String> companyIds) {
}
