/**
 * RBAC roles. These mirror the backend role names exactly as they appear in the JWT `roles` claim
 * (bare names — Spring prefixes `ROLE_` internally, the token does not). Source of truth is the
 * backend `role` table seeded by Flyway.
 */
export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  TENANT_ADMIN: 'TENANT_ADMIN',
  MLRO: 'MLRO',
  ANALYST: 'ANALYST',
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

export const ALL_ROLES: Role[] = [ROLES.SUPER_ADMIN, ROLES.TENANT_ADMIN, ROLES.MLRO, ROLES.ANALYST];

/** True if `roles` contains at least one of `allowed`. */
export function hasAnyRole(roles: readonly string[], allowed: readonly Role[]): boolean {
  return allowed.some((r) => roles.includes(r));
}

/** Tenant-scoped roles — the users who work with reports/notifications inside a tenant. */
export const TENANT_ROLES: Role[] = [ROLES.ANALYST, ROLES.MLRO, ROLES.TENANT_ADMIN];

/**
 * Post-login landing route for a set of roles. A platform SUPER_ADMIN has no tenant, so the tenant
 * Dashboard (Reports) doesn't apply — send them to Admin. Everyone else lands on the Dashboard.
 */
export function landingPathFor(roles: readonly string[]): string {
  return hasAnyRole(roles, TENANT_ROLES) ? '/dashboard' : '/admin';
}
