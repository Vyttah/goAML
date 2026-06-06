import { TOKEN_STORAGE_KEY } from '../lib/config';

/**
 * Bearer-token store: an in-memory cache backed by localStorage (survives refresh; the token is
 * short-lived — 15 min, no refresh token — so a reload re-reads it and the 401→login interceptor
 * handles expiry). The api client's request interceptor reads `getToken()`; the response interceptor
 * calls `clearToken()` on 401.
 */
let inMemoryToken: string | null = null;

function safeLocalStorage(): Storage | null {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null;
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  if (inMemoryToken !== null) return inMemoryToken;
  const stored = safeLocalStorage()?.getItem(TOKEN_STORAGE_KEY) ?? null;
  inMemoryToken = stored;
  return stored;
}

export function setToken(token: string): void {
  inMemoryToken = token;
  safeLocalStorage()?.setItem(TOKEN_STORAGE_KEY, token);
}

export function clearToken(): void {
  inMemoryToken = null;
  safeLocalStorage()?.removeItem(TOKEN_STORAGE_KEY);
}
