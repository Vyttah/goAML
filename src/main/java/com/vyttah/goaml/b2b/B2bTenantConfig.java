package com.vyttah.goaml.b2b;

/**
 * The per-tenant goAML B2B coordinates the client needs for one call, mirroring {@code tenant_goaml_config}.
 * Passed in by the caller (Phase 7 orchestration will load it from the DB) so the B2B layer stays free of
 * JPA and is trivially testable.
 *
 * @param tenantId    the tenant's id (used to key the cached token in Redis)
 * @param baseUrl     this tenant's goAML endpoint base URL (test or prod)
 * @param secretsPath AWS Secrets Manager path to this tenant's credentials
 * @param authMode    {@link B2bAuthMode#TOKEN} or {@link B2bAuthMode#BASIC}
 */
public record B2bTenantConfig(String tenantId, String baseUrl, String secretsPath, B2bAuthMode authMode) {
}
