import { describe, expect, it } from 'vitest';
import { decodeJwt, identityFromToken, isExpired } from './jwt';
import { makeToken } from '../test/util';

describe('jwt', () => {
  it('decodes claims from a well-formed token', () => {
    const token = makeToken({ email: 'mlro@acme.test', roles: ['MLRO', 'ANALYST'], tenant: 't-9' });
    const claims = decodeJwt(token);
    expect(claims?.email).toBe('mlro@acme.test');
    expect(claims?.roles).toEqual(['MLRO', 'ANALYST']);
    expect(claims?.tenant).toBe('t-9');
  });

  it('returns null for malformed tokens', () => {
    expect(decodeJwt(null)).toBeNull();
    expect(decodeJwt('')).toBeNull();
    expect(decodeJwt('not-a-jwt')).toBeNull();
    expect(decodeJwt('a.b')).toBeNull();
  });

  it('treats a SUPER_ADMIN (null tenant) token as valid', () => {
    const token = makeToken({ tenant: null, schema: null, roles: ['SUPER_ADMIN'] });
    const id = identityFromToken(token);
    expect(id?.tenantId).toBeNull();
    expect(id?.roles).toEqual(['SUPER_ADMIN']);
  });

  it('flags expired and missing-exp tokens', () => {
    const nowSec = Math.floor(Date.now() / 1000);
    const expired = makeToken({ exp: nowSec - 3600 });
    expect(isExpired(decodeJwt(expired))).toBe(true);
    expect(identityFromToken(expired)).toBeNull();

    const fresh = makeToken({ exp: nowSec + 3600 });
    expect(isExpired(decodeJwt(fresh))).toBe(false);
    expect(identityFromToken(fresh)).not.toBeNull();
  });
});
