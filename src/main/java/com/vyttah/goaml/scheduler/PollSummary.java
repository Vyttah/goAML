package com.vyttah.goaml.scheduler;

/**
 * Outcome of one status-poll cycle, for logging + tests.
 *
 * @param tenantsScanned ACTIVE tenants visited
 * @param reportsPolled  SUBMITTED reports a refresh was attempted for
 * @param succeeded      refreshes that returned without error
 * @param skipped        reports/tenants skipped after a (post-retry) failure
 */
public record PollSummary(int tenantsScanned, int reportsPolled, int succeeded, int skipped) {
}
