# Phase 6.2 — Redis-backed B2B token cache + auth + typed errors

> **Status: ✅ DONE (2026-06-04) — commit `50cbcd9`.**
> Part of [../plans/phase-6-aws-and-b2b.md](../plans/phase-6-aws-and-b2b.md).

---

## 1. Goal & why

goAML B2B is asynchronous and per-tenant: each tenant authenticates with its own credentials and gets a
session token (`SqlAuthCookie`) reused across calls. Phase 6.2 builds that **authentication layer** for
`TOKEN`-mode tenants — authenticate once, cache the token in **Redis** (shared across app instances,
TTL-expiring), re-auth on 401 — plus the **typed error** vocabulary every B2B caller needs.

## 2. What was built

| File | Role |
|---|---|
| `b2b/auth/TokenManager` (interface) | `token(cfg)` (cache-or-auth), `refresh(cfg)` (force re-auth after 401), `invalidate(tenantId)`. |
| `b2b/auth/DefaultTokenManager` | Resolves creds via `GoamlSecretsClient`, POSTs `GetToken`, caches `SqlAuthCookie` in Redis under `goaml:b2b:token:<tenantId>` with TTL. |
| `b2b/error/B2bAuthException` | HTTP 401 — re-auth and retry. |
| `b2b/error/B2bValidationException` | HTTP 400 — report rejected; carries the FIU error body. |
| `b2b/error/B2bTransportException` | network / non-2xx-non-400/401 — transient, retry/backoff. |
| `b2b/B2bAuthMode` (enum) | `TOKEN` / `BASIC` (mirrors `tenant_goaml_config.auth_mode`). |
| `b2b/B2bTenantConfig` (record) | `(tenantId, baseUrl, secretsPath, authMode)` — per-tenant coords the caller supplies (keeps b2b free of JPA). |
| `b2b/B2bProperties` + `b2b/B2bConfig` | `goaml.b2b.token-ttl` (default 20m). |
| `build.gradle` | `spring-boot-starter-data-redis` + `wiremock-standalone:3.9.2` (test). |
| `application.yml` | `spring.data.redis.{host,port}`, `goaml.b2b.token-ttl`. |

## 3. Key understanding / decisions

- **Why Redis (not in-memory):** the platform runs multiple instances on EKS; a shared, TTL-expiring token
  cache means one tenant's session is reused across pods and expires safely. (Your call.)
- **HTTP/1.1 pin (important):** the JDK HTTP client defaults to HTTP/2 and tries an h2c upgrade over
  cleartext, which goAML Web (legacy ASP.NET, HTTP/1.1) / our WireMock stub reject with `RST_STREAM`. The
  client is pinned to **HTTP/1.1** via a `JdkClientHttpRequestFactory` — both the fix and the correct
  production transport for goAML.
- **`B2bTenantConfig` decouples b2b from persistence:** Phase 6 has no `tenant_goaml_config` JPA entity yet;
  the caller (Phase 7 orchestration) will load the row and hand the b2b layer this small record. So b2b is
  pure HTTP + Redis, trivially testable.
- **Token-only here:** `TokenManager` handles `TOKEN` mode (session cookie). `BASIC` mode (per-request HTTP
  Basic) is applied by the client in 6.3, not cached.
- **Error taxonomy is the point:** 401 vs 400 vs transport map to *re-auth* vs *fix-the-report* vs
  *retry-with-backoff* — the three actions downstream orchestration (Phase 7/9) must distinguish.

## 4. Verification

- `TokenManagerIT` (3 tests) against compose **Redis** + in-process **WireMock**: auth-once-then-serve-cached
  (exactly 1 `GetToken`), `refresh` re-auths (2 calls), 401 → `B2bAuthException` with nothing cached.
- `@Tag("redis")` + `:6379` reachability gate → runs when Redis is up, skips cleanly otherwise.
- Full `./gradlew test` green; `@SpringBootTest` context tests prove the new beans wire (Lettuce connects
  lazily → no Redis needed at boot).

> **Reordering note:** the `GoamlB2bClient` interface + report operations were deferred to 6.3 (where they
> belong with `postReport`/`getReportStatus`); 6.2 is the auth/token-cache foundation.
