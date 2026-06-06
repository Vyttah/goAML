import { describe, expect, it } from 'vitest';
import { clearToken, getToken, setToken } from './tokenStore';
import { TOKEN_STORAGE_KEY } from '../lib/config';

describe('tokenStore', () => {
  it('stores, reads, and clears the token', () => {
    expect(getToken()).toBeNull();
    setToken('abc.def.ghi');
    expect(getToken()).toBe('abc.def.ghi');
    clearToken();
    expect(getToken()).toBeNull();
  });

  it('persists to localStorage', () => {
    setToken('persisted-token');
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('persisted-token');
    clearToken();
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
  });
});
