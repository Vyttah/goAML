# 18 — Test coverage & how to run the gates

> Maps the automated test coverage across the four codebases of the goAML + AML-suite, what the 2026-06-14
> test-hardening pass added, and the exact command to run each gate. Companion: [17 — Suite connections & admin](17-suite-connections-and-admin-guide.md).

## 1. The four gates

| Codebase | Path | Runner | Command |
|---|---|---|---|
| **goAML backend** | `dev/goAML` | Gradle + JUnit + Testcontainers (Docker Postgres) | `docker compose up -d postgres` then `./gradlew test` |
| **goAML SPA** | `dev/goAML/frontend` | Vitest + MSW + RTL (Node 18.16) | `npm test` (`vitest run`) |
| **AML cockpit** | `dev/AML/Frontend_Customer` | Vitest + RTL (Node 18.16) | `npm test` (`vitest run`) |
| **AML backend** | `dev/AML/Backend_Java` | Maven + JUnit (no `mvn` on PATH — use IntelliJ's bundled maven; JDK 21) | `mvn -pl <module> -am test -Dtest=… -Dsurefire.failIfNoSpecifiedTests=false` |

Run a single goAML backend class: `./gradlew test --tests 'com.vyttah.goaml.<pkg>.<Class>'`.

## 2. What's covered (after the 2026-06-14 hardening pass)

The pass added **230 tests** and fixed **1 production bug**. Coverage by area:

**goAML backend** (~680 tests). Service + security layers were already strong; the pass closed the HTTP boundary:
- **Multi-tenant isolation at the HTTP layer** (`security/CrossTenantIsolationE2ETest`): tenant B cannot read,
  submit, or list-attachments on tenant A's report — all 404 — while the owner can.
- **401 sweep** (`security/UnauthenticatedAccessE2ETest`): every authenticated endpoint rejects a no-token request.
- **403 role matrix** (`security/RoleEnforcementE2ETest`): the admin surface, report author/review gates, and
  SUPER-ADMIN-can't-touch-tenant-data.
- **Endpoint coverage**: notifications (incl. ownership), `/me/connection` (no-secret-leak), reportability over
  HTTP, the SUPER_ADMIN admin surface (trusted-services, tenant-external-refs, cross-tenant users + reset-password),
  submit error→HTTP mappings (FIU reject → 422, transport → 502), 404-on-missing-id.

**goAML SPA** (120 tests): the report lifecycle + builder + core admin were covered; the pass added the four
previously-untested admin panels (Suite Connections revoke/remove, goAML persons update/make-active/delete,
goAML config populated branch, Tenant Users CRUD), `ValidationMessages`, and `CodeSelect`.

**AML cockpit** (137 tests): the pass **extracted** the inline DPMSR helpers from `CreateTransactionComponent.tsx`
into testable modules — `dpmsrMappers.ts` (the AML-alpha-3 → goAML-alpha-2 country mapping + id/gender enums) and
`dpmsrValidation.ts` (the completeness gate) — then unit-tested them, plus `relationKyc`, the axios goAML-token
cache, and render tests for Approve / goAML-Filing / Create-Transaction.

**AML backend** (contract regression guards, new test files only): the login JWT carries `roles`+`principalType`
(`JwtServiceTest`), the service assertion carries a unique `jti` (`GoamlAssertionServiceJtiTest`), the filing-role
gate (`CurrentUserServiceTest`), the JWT filter claim parsing (`JwtAuthenticationFilterTest`), and the token-endpoint
error paths (`GoamlTokenControllerErrorPathTest`). These lock down the two cross-service bugs found while wiring SSO.

## 3. The bug the tests found

**SUPER_ADMIN cross-tenant user delete returned 500.** `DefaultAdminService.deleteUser` checks whether a user is
referenced by reports/attachments/imports/notifications before deleting — those are **tenant-scoped** tables, but
the method was `@Transactional`, so the connection's `search_path` was fixed to the *caller's* schema at transaction
start. A TENANT_ADMIN's own schema was already bound (so it worked); a tenantless SUPER_ADMIN deleting cross-tenant
queried a schema without those tables → `SQLGrammarException` → 500. **Fix** (mirrors `DefaultAuditService`): resolve
the target tenant's schema, bind it *before* the transaction, run the locked check+delete in a `TransactionTemplate`.
The unit test couldn't catch it (mocked repos); the HTTP-layer E2E (`AdminSuperAdminE2ETest`) did.

## 4. CI note

The two AML-cockpit deploy workflows do **not** run the Vitest suite — it is a local-only gate today. The goAML
backend gate runs in `ci.yml`. Consider adding a test step to the cockpit CI so a mapping/validation regression
fails the build before reaching a regulator filing.
