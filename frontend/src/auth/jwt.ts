/**
 * Client-side JWT decoding. There is **no** `/me` endpoint — the SPA derives the user's identity
 * straight from the access-token claims the backend signs (see `JwtService`):
 *   sub (user UUID) · email · tenant (UUID|null) · schema · roles (string[]) · exp/iat (epoch secs).
 *
 * This decodes ONLY (no signature verification — that is the backend's job on every request). It is
 * used purely to populate the UI; the server remains the authority for access control.
 */
export interface JwtClaims {
  sub: string;
  email: string;
  tenant: string | null;
  schema: string | null;
  roles: string[];
  iat?: number;
  exp?: number;
  iss?: string;
}

/** The slice of claims the UI treats as the signed-in identity. */
export interface Identity {
  userId: string;
  email: string;
  tenantId: string | null;
  schema: string | null;
  roles: string[];
}

function base64UrlDecode(segment: string): string {
  const padded = segment.replace(/-/g, '+').replace(/_/g, '/');
  const withPad = padded + '==='.slice((padded.length + 3) % 4);
  // atob is available in browsers and in Node 18+ (and jsdom under Vitest).
  const binary = atob(withPad);
  // Decode UTF-8 bytes so non-ASCII emails/names survive.
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** Decode a JWT's payload. Returns null for anything that isn't a well-formed 3-part token. */
export function decodeJwt(token: string | null | undefined): JwtClaims | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const json = base64UrlDecode(parts[1]);
    const claims = JSON.parse(json) as Partial<JwtClaims>;
    if (typeof claims.sub !== 'string') return null;
    return {
      sub: claims.sub,
      email: typeof claims.email === 'string' ? claims.email : '',
      tenant: typeof claims.tenant === 'string' ? claims.tenant : null,
      schema: typeof claims.schema === 'string' ? claims.schema : null,
      roles: Array.isArray(claims.roles) ? claims.roles.filter((r): r is string => typeof r === 'string') : [],
      iat: claims.iat,
      exp: claims.exp,
      iss: claims.iss,
    };
  } catch {
    return null;
  }
}

/** True if the token is absent, malformed, or past its `exp` (with a small clock-skew leeway). */
export function isExpired(claims: JwtClaims | null, nowMs: number = Date.now(), skewSeconds = 30): boolean {
  if (!claims || typeof claims.exp !== 'number') return true;
  return claims.exp * 1000 <= nowMs - skewSeconds * 1000;
}

/** Project claims into the UI identity, or null if the token is missing/expired. */
export function identityFromToken(token: string | null | undefined, nowMs: number = Date.now()): Identity | null {
  const claims = decodeJwt(token);
  if (!claims || isExpired(claims, nowMs)) return null;
  return {
    userId: claims.sub,
    email: claims.email,
    tenantId: claims.tenant,
    schema: claims.schema,
    roles: claims.roles,
  };
}
