/**
 * Runtime config. In dev + same-origin prod the base URL is empty (relative `/api/v1/...` paths,
 * resolved by the Vite proxy in dev or the serving jar in prod). Set VITE_API_BASE_URL to target a
 * remote backend (the backend's env-gated CORS bean must then allow this origin).
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

/** All REST endpoints live under this prefix. */
export const API_PREFIX = '/api/v1';

/** localStorage key for the bearer access token. */
export const TOKEN_STORAGE_KEY = 'goaml.accessToken';
