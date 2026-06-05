# Phase 7.3 — Web REST (controller + DTOs + RBAC + E2E)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-7-persistence-service-web.md](../plans/phase-7-persistence-service-web.md).

---

## 1. Goal & why

Expose the report lifecycle over HTTP. **After this step the DPMSR flow is manually testable via the API**
(curl/Postman): log in → create+validate → list/get → submit (MLRO) → status.

## 2. What was built

- **`controller/report/ReportController`** (thin, `@RequiredArgsConstructor`):
  - `POST /api/v1/reports` — create+validate (`@Valid DpmsrCreateRequest`) → **201** `CreateReportResponse`
    (status + validation messages). RBAC `ANALYST`/`MLRO`.
  - `GET /api/v1/reports` / `GET /api/v1/reports/{id}` — list / fetch (`ReportView`). RBAC
    `ANALYST`/`MLRO`/`TENANT_ADMIN`.
  - `POST /api/v1/reports/{id}/submit` — **MLRO only** → `SubmissionView` (reportkey).
  - `GET /api/v1/reports/{id}/status` — on-demand FIU status (`StatusView`).
  - tenant + actor come from `@AuthenticationPrincipal UserPrincipal`.
- **`model/dto/report/ReportResponses`** — `CreateReportResponse`, `ReportView`, `SubmissionView`,
  `StatusView` (static `from(...)` factories; controllers never return entities/service types).
- **`exception/GlobalExceptionHandler` extended** — not-found → 404, duplicate/not-submittable/
  config-missing → 409, FIU-rejection → 422 (+ `fiuError` body), auth/transport → 502, bad input
  (`MethodArgumentNotValidException`/`IllegalArgumentException`) → 400.

## 3. Verification

`ReportApiE2ETest` (`@SpringBootTest` RANDOM_PORT + Testcontainers Postgres + `TestRestTemplate`, **goAML
B2B client `@MockBean`-ed** so submit needs only Postgres):
- MLRO: login → create (201, **VALID**) → list contains it → get-by-id → missing-id 404 → submit (200,
  reportkey `RK-E2E`, SUBMITTED) → status (Accepted).
- ANALYST: create OK, **submit → 403**.
- duplicate `entity_reference` → **409**.

JaCoCo gate extended to `controller.report`; gated total **98.5% instr / 84.5% branch** (passes). Full
`./gradlew test jacocoTestCoverageVerification` green.

> The real b2b submit path (TokenManager/Redis/Secrets/HTTP) is covered by the 6.x + 7.2 tests; mocking it
> here keeps the E2E to one container and focused on web→service→persistence + RBAC.

## 4. Out of scope (7.4)
Docs/planning sync + the local-seed snippet for hands-on submit (tenant_goaml_config row + LocalStack
secret + stub base_url).

---

## Outcome
✅ The DPMSR report lifecycle is reachable over REST and E2E-proven. Next: 7.4 (docs sync) closes Phase 7.
