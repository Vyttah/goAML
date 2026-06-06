import type { JwtClaims } from '../auth/jwt';

function base64Url(input: string): string {
  return btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Build an unsigned-but-well-formed JWT for tests (header.payload.signature). The client never
 * verifies the signature, so a dummy one is fine. `exp` defaults to one hour out.
 */
export function makeToken(claims: Partial<JwtClaims> = {}): string {
  const nowSec = Math.floor(Date.now() / 1000);
  const payload: JwtClaims = {
    sub: 'user-1',
    email: 'user@tenant.test',
    tenant: 'tenant-1',
    schema: 'tenant_demo',
    roles: ['ANALYST'],
    iat: nowSec,
    exp: nowSec + 3600,
    iss: 'goaml',
    ...claims,
  };
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = base64Url(JSON.stringify(payload));
  return `${header}.${body}.test-signature`;
}
