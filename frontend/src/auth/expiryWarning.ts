import type { JwtClaims } from './jwt';

/**
 * Pure helpers for the pre-expiry warning. The access token is short-lived (15 min) with no refresh,
 * so we warn the user a couple of minutes before `exp` to save their work and re-login. These are
 * side-effect-free so they can be unit-tested; the wiring (timer + AntD notification) lives in the
 * `ExpiryWarning` component.
 */

/** How long before `exp` to surface the warning. */
export const WARNING_LEAD_MS = 2 * 60 * 1000;

/**
 * Milliseconds from `now` until the warning should fire (i.e. `exp - lead`).
 *  - returns a non-negative delay when the warning is still in the future,
 *  - returns `0` when we are already inside the warning window (fire immediately),
 *  - returns `null` when there is no usable `exp` (nothing to schedule).
 */
export function msUntilWarning(
  claims: JwtClaims | null,
  nowMs: number = Date.now(),
  leadMs: number = WARNING_LEAD_MS,
): number | null {
  if (!claims || typeof claims.exp !== 'number') return null;
  const warnAt = claims.exp * 1000 - leadMs;
  const delay = warnAt - nowMs;
  return delay > 0 ? delay : 0;
}

/**
 * True when the token is at/inside the warning window (and not yet expired) — the predicate the
 * banner uses to decide whether to show right now. False once the token has fully expired (the
 * 401→login flow takes over there) or when there's no `exp`.
 */
export function shouldWarnNow(
  claims: JwtClaims | null,
  nowMs: number = Date.now(),
  leadMs: number = WARNING_LEAD_MS,
): boolean {
  if (!claims || typeof claims.exp !== 'number') return false;
  const expMs = claims.exp * 1000;
  if (nowMs >= expMs) return false;
  return nowMs >= expMs - leadMs;
}
