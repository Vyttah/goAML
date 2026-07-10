/**
 * Runtime config. In dev + same-origin prod at the root context the base URL is empty (relative
 * `/api/v1/...` paths, resolved by the Vite proxy in dev or the serving jar in prod).
 *
 * When served under a non-root context (the WAR at Tomcat's `/goaml`), Vite's `import.meta.env.BASE_URL`
 * is that path (e.g. `/goaml/`); we strip the trailing slash so axios prepends it to `${API_PREFIX}/...`
 * → `/goaml/api/v1/...`, keeping the SPA same-origin. Set VITE_API_BASE_URL to override entirely (e.g. to
 * target a remote backend; the backend's env-gated CORS bean must then allow this origin).
 */
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? import.meta.env.BASE_URL.replace(/\/$/, '');

/** All REST endpoints live under this prefix. */
export const API_PREFIX = '/api/v1';

/** localStorage key for the bearer access token. */
export const TOKEN_STORAGE_KEY = 'goaml.accessToken';
