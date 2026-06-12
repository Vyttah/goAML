# Audit remediation progress — 2026-06-11

Source: `e2e-audit-2026-06-11.md`. Execution: waves of agents with disjoint file ownership.
**No commits until the user asks.** Items intentionally NOT done autonomously:
- **A6 partial**: deleting `dev/goaml-prePII-backup.bundle` (destructive — user must delete) and adding the
  git remote (URL unknown — user must provide).
- **A8**: retiring the AML deal-module UI (open product decision per STATE.md checkpoint). Both paths are
  being FIXED instead; the converge decision stays with the user.
- **D5**: orphan INVALID report cleanup path (product decision: delete vs archive semantics).

## Migration number assignments (to avoid collisions)
- goAML shared: V7 = consumed_assertion (replay store, Wave 1/G1); V8 = constraints + cleanup (Wave 3/G3)
- goAML tenant: V8 = report.client_metadata (Wave 2/G2); V9 = CHECKs + submitted_by + indexes (Wave 3/G3)
- AML customer-service: next free V10x = goaml_transaction filing-attempt column (Wave 1/A-BE)

## Cross-repo contract decisions (fixed now, both sides implement)
1. **clientMetadata**: `POST /api/v1/reports` body gains optional top-level `clientMetadata` (JSON object,
   ≤16KB) → persisted verbatim to `report.client_metadata` JSONB → returned in `GET /{id}/detail` as
   `clientMetadata`. NEVER marshalled into the goAML XML.
2. **org claim**: service assertions carry `org` = AML companyId; goAML screening integration REQUIRES it
   and rejects when it ≠ the `companyId` request param.
3. **registrationDate**: goAML accepts BOTH `yyyy-MM-dd` and ISO date-time on goods registrationDate
   (lenient deserializer); AML keeps sending LocalDate.
4. **Poller metric**: counter name `goaml.poller.errors` (Micrometer; G3 adds it, G-OPS alerts on
   `goaml_poller_errors_total`).

## Wave status
- [x] Wave 1 (parallel): **G1 ✅** (B2/B10/B11/B14/B16/C1/D2/C4 — incl. C4 cd.yml workflow_run-gated + Trivy + CI redis, finished by orchestrator) · **G-SPA ✅** (edits on disk; gates run in W4) · **A-FE ✅** (edits on disk; gates run in W4) · **A-BE ✅** · **G-OPS ✅**
  - NOTE: G1/G-SPA/A-FE were cut off by a session limit mid-flight but their file edits all landed; verification gates deferred to Wave 4. AML backend pom/application.yml diffs are the USER's pre-existing local-dev work (bulk-onboarding, localhost defaults) — NOT ours; never commit them.
- [x] Wave 2: **G2 ✅** (A1 countries 253 + consistency test, A3-backend clientMetadata, B3, B4, B5+B17, B13, C8, docs/15); C9 moved to G3
- [x] Wave 3: **G3 ✅** (A2 tenant upgrade runner, A5-goAML review gate, B6, B8, B15, C2/C3/D6 migrations, C9, C11, D8, jacoco, testHeap)
- [x] Wave 4: **GATES GREEN ✅** — goAML gradle **526 tests, 0 failures, 1 skipped, jacoco passes** (fixed 17-test fallout: IntegrationAuthFilter matched empty servletPath under Servlet 6 → skipped all integration auth → fixed to request-URI; prometheus test split for D2; one stale org test). goAML SPA Node18: tsc 0 / lint 0 / vitest **91/91**. AML FE Node22: tsc 0 / next-lint clean / vitest **17/17**. AML BE: not runnable here (no maven/JDK21) — new `ci.yml` runs `mvn verify` on push; A-BE static-verified.
- [x] Wave 5: **VERIFIED ✅** — independent gate re-run BUILD SUCCESSFUL; adversarial greps confirm lenient person (not my_client), clientMetadata never reaches engine/XML, submit re-validates XSD, countries enforced, tenant upgrade runner present.

## STILL DEFERRED (need the user — NOT done autonomously)
- **A6:** delete `dev/goaml-prePII-backup.bundle` (destructive) + add a git remote (URL unknown) + first push.
- **A8:** the two-filing-paths product decision (both paths now FIXED; converging is the user's call).
- **D5:** orphan INVALID report cleanup (delete vs archive — product decision).
- **AML residuals:** `CustomerLegal.businessActivity` has no goAML wire slot (needs goAML-side field first); STAFF `X-Company-Id` trust needs a staff→company mapping table that doesn't exist there; B4 screening phone/address type-codes are an AML-side enrichment.
- **Nothing committed** — commit only when asked; in the AML repos commit ONLY our goaml-integration files via pathspecs (the poms/application.yml diffs there are the user's own work).

## Finding → wave map
A1→W2 · A2→W3 · A3→W1(FE)+W2(BE) · A4→W1 · A5→W1(AML)+W3(goAML) · A7→W1 · A9→W1+W3 ·
B1→W1 · B2→W1 · B3→W2 · B4→W2 · B5→W2 · B6→W3 · B7→W1 · B8→W3 · B9→W1 · B10→W1 · B11→W1(both) ·
B12→W1 · B13→W2 · B14→W1 · B15→W3 · B16→W1 · B17→W2 ·
C1→W1 · C2→W3 · C3→W3 · C4→W1 · C5→W1 · C6→W1(CI)+W3(jacoco) · C7→W1 · C8→W2 · C9→W2 · C10→W1 ·
C11→W3 · C12→W1 · D1→W1 · D2→W1 · D3→W1 · D4→W1 · D6→W3 · D7→W1 · D8→W3

## A-BE done (AML Backend goaml-integration — `Backend_Java`, branch feature/goaml-integration)

- **B3 (registrationDate type mismatch)** — `GoamlFilingPayload.Goods.registrationDate` changed
  `LocalDate`→`OffsetDateTime`; `GoamlFilingService.toGoods()` now widens the deal's `LocalDate` to UTC
  midnight (`atUtcMidnight()`), so the wire shape is a full ISO offset date-time (`2026-06-11T00:00:00Z`)
  that matches goAML `DpmsrCreateRequest.Goods.registrationDate` (`OffsetDateTime`) — works regardless of
  whether goAML's lenient bare-date deserialiser lands. Test:
  `GoamlFilingServiceTest.registrationDateIsSerialisedAsUtcMidnightOffsetDateTime`.
- **B4 (KYC field carriage)** — verified AML already CARRIES all KYC fields the goAML side consumes: the wire
  DTO `GoamlScreeningPayload` (Natural/Legal/Identification/Address) mirrors goAML's `ScreeningPartyPayload`
  field-for-field, and `GoamlCustomerPushService` populates email, alias, identifications (natural) and trn,
  dateOfIncorporation, address, phone, licenseNumber (legal). No field was being dropped before leaving AML;
  added two regression tests (`GoamlCustomerPushServiceTest.legalCustomerPayloadCarriesAllKycFields` /
  `…naturalCustomerPayloadCarriesEmailAliasAndIdentifications`). **Residual gap (not patched):**
  `CustomerLegal.businessActivity` has no slot in goAML's wire `ScreeningPartyPayload.LegalCustomer`, so
  business activity is not carried — adding it AML-side would be un-consumed (Jackson ignores unknown).
  Needs a goAML-side field before it can be wired.
- **B11 (org claim)** — already in place: `GoamlAssertionService.mint()` sets `org`, and every
  `GoamlScreeningClient` call passes `companyId` as `org`. `GoamlAssertionServiceTest` already asserts `org`
  present + omitted-when-null. No change needed beyond confirmation.
- **B12 (X-Company-Id trust)** — `CurrentUserService.requireCompanyId()` already prefers the trusted signed
  `principal.companyId()` and only falls back to the `X-Company-Id` header for STAFF principals. No
  server-side staff→company assignment table exists in this codebase, so for staff the header remains the
  source (residual risk — documented; a safe fix needs a mapping table owned by another team / data model).
- **A5 (ungated goAML token endpoint)** — added a configurable role gate (`goaml.integration.filing-roles`,
  default `MLRO,COMPLIANCE_OFFICER,COMPLIANCE,SUPER_ADMIN,ADMIN,ADMIN2`) via
  `CurrentUserService.requireGoamlFilingRole()` (canonicalises the comma-separated `roles` claim the same
  way the rest of the codebase does). Gated `POST /api/v1/goaml/token` and the direct
  `GoamlFilingService.file()` FIU-write path → non-privileged user 403, privileged 200. Note: customer-service
  has no `@PreAuthorize`/method-security infra (auth is `anyRequest().authenticated()`), so the gate is an
  explicit service-layer check. Tests: `GoamlTokenControllerTest` (403/200), plus filing-service stub update.
- **B7 (CI never runs tests)** — added `.github/workflows/ci.yml` running `mvn -B -ntp verify` on push/PR
  (JDK 21 temurin, maven cache, surefire artifact upload). Deploy-*.yml left untouched (their `-DskipTests`
  is their infra choice). All AML unit tests are Mockito-based (no DB/Docker), so `verify` runs cleanly.
- **Controller tests (Medium)** — added MockMvc slice tests (standaloneSetup + GlobalExceptionHandler,
  mirroring the repo's `UboControllerTest`) for `GoamlTokenController`, `GoamlFilingController`,
  `GoamlPushController`, `GoamlLookupController`, `GoamlTransactionController` — each covers happy-path +
  a 4xx, with the filing/token security gate exercised (403/200/409).

**Verification status (honest):** Maven is not installed on this machine and only JDK 17 is present (project
needs JDK 21), so I could NOT run `mvn -pl customer-service -am test`. Changes were verified by careful static
review against the live DTO/entity/record signatures (arg order, getter names, record component names) and the
existing test harness (the goaml unit tests are pure Mockito and have surefire reports from prior runs, so the
stack is known-good). The new `ci.yml` is the live gate that will actually run `mvn verify` on push/PR.

Files changed:
- `customer-service/.../integration/goaml/GoamlFilingPayload.java` — registrationDate → OffsetDateTime
- `customer-service/.../integration/goaml/GoamlFilingService.java` — UTC-midnight widening + A5 role gate
- `customer-service/.../security/CurrentUserService.java` — filing-role gate helpers (A5)
- `customer-service/.../controllers/GoamlTokenController.java` — A5 role gate on /goaml/token
- `customer-service/.../controllers/{GoamlToken,GoamlFiling,GoamlPush,GoamlLookup,GoamlTransaction}ControllerTest.java` — NEW
- `customer-service/.../integration/goaml/GoamlFilingServiceTest.java` — B3 + A5 stub
- `customer-service/.../integration/goaml/GoamlCustomerPushServiceTest.java` — B4 completeness tests
- `.github/workflows/ci.yml` — NEW test gate (B7)

## G-OPS done (Wave 1 — infra + ops/planning docs; no commits)

Owner: G-OPS. Scope: docs, planning files, Helm/observability. **No Java, no `.github/workflows/*`, no
`application*.yml`** (G1 owns those). All Helm templates eyeball-checked + brace/control-block balanced (no
`helm` binary available); `values.yaml` validated with `python3 -c "import yaml"`.

- **A7 (HIGH) — DR/backup/retention policy:** created **`docs/16-operations-dr-retention.md`** — RPO/RTO
  (proposed ≤5min/≤1h, flagged needs sign-off), RDS automated backups + PITR + restore drill, S3
  versioning/lifecycle, per-tenant `pg_dump`/`pg_restore` export, a **≥5-year UAE AML records-retention
  schedule** per record class (flagged needs legal/FIU sign-off; notes there is **no automated disposal job
  yet** — currently keep-everything), Flyway forward-only rollback stance, and the single-poller topology.
  Pointers added from `CLAUDE.md` ("Start here" map) + `docs/README.md` (doc index).
- **A9 + alerting (MED) — Helm alerting:** added **`templates/prometheusrule.yaml`** (gated
  `prometheusRule.enabled`, default false): `GoamlPollerErrors` (on `goaml_poller_errors_total`),
  `GoamlTargetDown` (`absent(up==1)` heartbeat — proxy for stale-SUBMITTED / poller-not-running, since no
  stale gauge exists), `GoamlHttp5xxRate` (Micrometer `http_server_requests_seconds_count` 5xx ratio),
  `GoamlReadinessFlapping` (`changes(up[...])`). Added **`templates/servicemonitor.yaml`** (gated
  `serviceMonitor.enabled`, default false) scraping `/actuator/prometheus`. Both require Prometheus-Operator
  CRDs; default install unchanged. New `values.yaml` keys: `prometheusRule.*`, `serviceMonitor.*`.
- **B8 (HIGH) — duplicate-poller fix (infra side):** `values.yaml` adds `scheduler.enabled: true` and
  `poller.dedicated: false` (+ `poller.resources`). `templates/deployment.yaml` now sets env
  **`GOAML_SCHEDULER_ENABLED`** = `false` when `poller.dedicated`, else `scheduler.enabled`, and labels the
  web pods `goaml.io/role: web`. New **`templates/poller-deployment.yaml`** (gated `poller.dedicated`): a
  single-replica, no-HPA poller Deployment with `GOAML_SCHEDULER_ENABLED=true`, labelled
  `goaml.io/role: poller`. `service.yaml` selector now pins `goaml.io/role: web` so the Service never routes
  user traffic to the poller pod. Topology documented in `docs/16` §9.
- **D3 (nice-to-have) — chart extras:** **`templates/poddisruptionbudget.yaml`** (gated `pdb.enabled`,
  default **true**, `minAvailable: 1`, selects `goaml.io/role: web`) + **`templates/networkpolicy.yaml`**
  (gated `networkPolicy.enabled`, default **false**: default-deny ingress, allow ingress-controller +
  Prometheus namespaces on :8080; egress intentionally unrestricted, documented). New `values.yaml` keys
  `pdb.*`, `networkPolicy.*`.
- **A9/runbook rollback (MED):** appended a **"Phase G — Rollback & incident operations"** section to
  `.planning/plans/go-live-integration-runbook.md`: `helm rollback` + image-SHA pinning, Flyway forward-only
  (corrective-migration / restore-not-rollback) stance, and an **MLRO playbook for an extended FIU outage**
  (502 handling, do-not-mass-resubmit, leave SUBMITTED for the poller, comms/escalation).
- **C5 (MED) — doc-rot sweep (owned parts):** `CLAUDE.md` (Phase 1.5 → **DONE/merged**, REST-not-RabbitMQ,
  frontend-direct track, docs/16 pointer); `STATE.md` (1.5 deferred→done in Current Position / Next Action /
  Progress table; stale Blockers "no XSD gate / RBAC not enforced" → both built+enforced; stale branch
  `phase-14/infra` → `feature/goaml-frontend-direct`); `PROJECT.md` ("v4.0 XML all report types" → XSD-first
  5.0.2 + phased DPMSR-first + backlog pointer; "Accounting via RabbitMQ" → REST, marked SUPERSEDED & built);
  `docs/02` (four-surfaces table + package table 7/12/13/b2b/integration/aws → built; §4 lead-in;
  "(partial)" markers cleared); `docs/08` (zero-Mockito/WireMock + "not tested yet" §6 → corrected to current
  coverage); `docs/05`:63 + `docs/CONVENTIONS.md`:136 stale RabbitMQ refs annotated (REST, not RabbitMQ);
  `docs/README.md` status block. **Left `docs/15` my_client wording to the domain agent** (did not touch the
  file — no marker needed).
- **C12 (MED) — silent report-type drop:** appended a **"Backlog — remaining report types (the other 16 of
  17)"** section to `.planning/ROADMAP.md` (verified the 17 `report_type` enum codes against
  `goAMLSchema.xsd` 5.0.2; DPMSR is the only full lifecycle today; lists per-type work needed).
- **D8 (nice-to-have) — dev-seeder foot-gun:** documented in `docs/16` §8 (the `DevDataSeeder` creates no
  `tenant_goaml_config` → fresh env `rentity_id=0` → INVALID). **Did NOT edit the Java** (G3 owns it) — see
  the NOTE below.

### NOTE → G3 (Java/platform agent) — two action items handed over

1. **`GOAML_SCHEDULER_ENABLED` must gate the `@Scheduled` poller.** The Helm chart (deployment.yaml +
   poller-deployment.yaml) now injects env **`GOAML_SCHEDULER_ENABLED`** (`true`/`false`). For B8 to actually
   prevent duplicate polling, the Java `@Scheduled` poll method must honour it. **Discrepancy to resolve:**
   the existing property is **`goaml.scheduler.status-poll.enabled`** (`SchedulerProperties.statusPoll().enabled()`,
   read in `SubmissionStatusPoller.scheduledPoll()`), but the audit-decision env is `GOAML_SCHEDULER_ENABLED`
   → Spring relaxed-binding maps that to **`goaml.scheduler.enabled`** (a *different* key). Pick one and make
   them line up — recommended: add a top-level `goaml.scheduler.enabled` (default true) that the poller checks
   first (so `GOAML_SCHEDULER_ENABLED=false` disables it), keeping `status-poll.enabled` as the finer-grained
   toggle. (Do NOT change the env var name — the chart is built around `GOAML_SCHEDULER_ENABLED`.)
2. **Dev-seeder `rentity_id` foot-gun (D8).** In `config/dev/DevDataSeeder.java`: either seed a
   **loudly-marked placeholder** `tenant_goaml_config` (an obviously-non-real `rentity_id`, e.g. a sentinel
   like `999999` with a comment, NOT a plausible value) **or** emit a startup **WARN** when an ACTIVE tenant
   has no goAML config / `rentity_id<=0`. Documented for operators in `docs/16` §8.

## G2 done (Wave 2 — goAML domain/FIU batch; no commits)

Owner: G2. Scope: the "silent data loss into FIU filings" cluster (audit Dim 3 + Dim 7). Guardrail held —
filed XML stays XSD-valid and loses no user-supplied field. All edits verified with `compileJava`/
`compileTestJava` and the named new tests run green (full suite deferred to Wave 4).

- **A1b (CRITICAL) — countries ⊆ XSD enforced.** Added `countriesLookupIsSubsetOfCountryType()` to
  `LookupXsdConsistencyTest` (countries.json → `country_type`), pinning the already-regenerated 253-code file
  so it can never silently drift. The XSD `country_type` enum is **253** codes (incl. `-`) and the lookup is
  exactly 253 — they match (the audit's "506" was a miscount). Test class green (6 assertions).
- **A3-backend (HIGH) — clientMetadata persisted + returned, never filed.** New tenant migration
  `V8__report_client_metadata.sql` (`client_metadata jsonb NULL`); `Report.clientMetadata` mapped as the
  raw-JSON-String JSONB pattern; optional `clientMetadata` (`JsonNode`) on `DpmsrCreateRequest` +
  `DpmsrReportPayload` (back-compat convenience constructors keep all existing call sites); persisted verbatim
  in `DefaultReportService` create paths, returned in `GET /{id}/detail` (`ReportDetail`/`ReportDetailView`),
  >16 KiB → `ClientMetadataTooLargeException` → 422. It never reaches the engine (`toInput` ignores it). Tests:
  `clientMetadataIsPersistedReturnedInDetailButNeverInTheXml`, `noClientMetadataLeavesColumnNullWithoutError`,
  `oversizedClientMetadataIsRejected`.
- **B3 (HIGH) — lenient registrationDate.** New field-localized `LenientOffsetDateTimeDeserializer` accepts
  BOTH a bare `yyyy-MM-dd` (→ UTC midnight) and full ISO date-time; annotated via `@JsonDeserialize` on the
  curated request date fields (`submissionDate`, goods `registrationDate`, person/director `birthdate`,
  identification `issueDate`/`expiryDate`). No global Jackson change; the full-fidelity payload is unaffected.
  Tests: `LenientOffsetDateTimeDeserializerTest` (4).
- **B4 (HIGH) — screening curated path stops dropping fields + fabricating "Unknown".** Extended the curated
  `DpmsrCreateRequest.Person` (+`email`,`alias`) and `Entity` (+`incorporationDate`,`taxRegNumber`,`business`,
  `address`) and wired them through the mapper to `TPerson`/`TEntity`. `ScreeningPartyMapper` now carries
  person email/alias/identifications and entity trn/incorporationDate/address, and OMITS (null) instead of
  fabricating name→"Unknown" or `countryOfBirth`/`residence`←nationality. Tests:
  `fullyPopulatedScreeningPayloadCarriesAllFieldsIntoTheXml`, `sparseScreeningPayloadOmitsCleanly…`,
  `sparseNaturalCustomerOmitsCleanly…`, `missingCustomerNamesAreOmittedNotFabricated`, plus updated existing.
  **Residual (not fixable here):** the screening wire DTO's `Phone`/`Address` carry no goAML contact-/address-
  type slot and `Identification.type` is passed verbatim, so a rich screening payload's phone/address aren't
  themselves XSD-VALID until the screening side sends coded types — an AML-side DTO change (B4 noted earlier
  that `businessActivity` likewise has no goAML-side slot; we added `business`, so that one is now wired).
- **B5+B17 (HIGH/MED) — curated person → lenient TPerson + address.** `DpmsrRequestMapper.party()` maps person
  parties to lenient `TPerson` (via `GoamlParties.person`), not `TPersonMyClient` — only first/last name are
  XSD-mandatory, so a minimal `CsvImporter` row is now VALID. `taxRegNumber` stays mappable but optional; the
  party `address` (B17) is mapped (was dropped); optional blocks (phones/addresses/identifications) emit only
  when present. Tests: `mapsPersonPartyAsLenientPerson`, `minimalPersonPartyOmitsOptionalBlocksAndStaysLenient`,
  `minimalCsvShapedPersonPartyProducesAValidReport` (status VALID), `personPartyWithAddressEmitsAddressesInTheXml`.
- **B13 (MED) — wire-key drift safety net.** New `DpmsrCuratedWireFidelityTest`: a MAXIMAL curated request
  (entity+director+person+goods+address+phone+identification) round-trips through JSON, the real engine, and
  the XSD gate, asserting every field re-appears in the VALID XML. (Curated DTO has no `account` subject — the
  account path's fidelity stays covered by `DpmsrFullFieldFidelityTest`/`ReportApiE2ETest`.)
- **C8 (MED) — XSD name-pattern trap.** New engine `NameNormalizer` maps the common illegal punctuation
  (`&`→`and`, strip/replace `( ) , /`, collapse spaces) applied in the mapper on entity/person/director/MLRO
  names; `ReportValidator` raises a clear `NAME_PATTERN` error for still-invalid names (Arabic / non-Latin)
  across every path instead of a raw SAX message. Tests: `NameNormalizerTest` (4),
  `entityNameWithIllegalPunctuationIsNormalisedAndStaysXsdValid`, `unmappableArabicNameYieldsAClearPatternError…`.
- **docs/15 (LOW)** — updated the person-party section to the lenient `t_person` decision (curated/CSV/screening
  use lenient `t_person`; `t_person_my_client` remains engine-supported for deliberate full-fidelity callers);
  fixed the frontend-implication table (person-party now needs only first/last to be VALID).

**Pre-existing blocker fixed to unblock the build (flag for review):** `IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR`
(security/**, owned by C1/G1) was a computed `getName()+"…"` value used inside a `@RequestAttribute(...)`
annotation in `ScreeningIntegrationController` — not a compile-time constant, so **all** of `compileJava` failed.
Changed it to the identical string literal (behavior-preserving) so the repo compiles. The screening org-claim
check added there by another agent was left untouched.

Files changed (G2):
- `src/test/.../engine/lookup/LookupXsdConsistencyTest.java` — A1b countries⊆country_type assertion
- `src/main/resources/db/migration/tenant/V8__report_client_metadata.sql` — NEW (A3)
- `src/main/.../model/entity/report/Report.java` — `clientMetadata` JSONB (A3)
- `src/main/.../model/dto/report/DpmsrCreateRequest.java` — `clientMetadata` + B3 date deser + B4 person/entity fields + back-compat ctors
- `src/main/.../model/dto/report/DpmsrReportPayload.java` — `clientMetadata` + back-compat ctor
- `src/main/.../model/dto/report/LenientOffsetDateTimeDeserializer.java` — NEW (B3)
- `src/main/.../service/report/DefaultReportService.java` — persist/return clientMetadata + 16 KiB cap (A3)
- `src/main/.../service/report/ReportDetail.java` + `ReportExceptions.java` — clientMetadata field + 422 exception (A3)
- `src/main/.../model/dto/report/ReportResponses.java` — clientMetadata in detail view (A3)
- `src/main/.../exception/GlobalExceptionHandler.java` — ClientMetadataTooLarge → 422 (A3)
- `src/main/.../model/mapper/report/DpmsrRequestMapper.java` — lenient TPerson + address + B4 entity fields + C8 normalize
- `src/main/.../service/integration/ScreeningPartyMapper.java` — B4 carry-through + omission-not-fabrication
- `src/main/.../engine/build/NameNormalizer.java` — NEW (C8)
- `src/main/.../engine/validation/ReportValidator.java` — C8 NAME_PATTERN errors
- `src/main/.../security/IntegrationAuthFilter.java` — const literal (pre-existing build blocker; flagged above)
- `docs/15-dpmsr-field-requirements.md` — lenient person/entity wording
- NEW tests: `LenientOffsetDateTimeDeserializerTest`, `DpmsrCuratedWireFidelityTest`, `NameNormalizerTest`;
  extended `DefaultReportServiceTest`, `DpmsrRequestMapperTest`, `ScreeningPartyMapperTest`.

### NOTE → Wave 4 / G3
- A new tenant migration **V8** landed (`report.client_metadata`). The A2 tenant-schema upgrade runner (G3)
  must migrate existing tenant schemas through V8 too; the detail-view DTO now carries `clientMetadata`.
- The screening-side type-code gap (B4 residual) needs an **AML-side** `ScreeningPartyPayload` change to send
  goAML contact/address/identification type codes — out of scope for goAML; track on the AML repo.

## G3 done (Wave 3 — goAML platform/data-integrity batch; no commits)

Owner: G3. Scope: tenant upgrade path, the review gate, delete-guard, attachment scanning, poller, DB
constraints/audit, dead-code/build. Guardrail held — no filed-XML logic changed. All edits compile
(`compileJava`/`compileTestJava`) and the named new/affected tests run green (full suite deferred to Wave 4).

- **A2 (CRITICAL) — tenant-schema upgrade path.** New `config/tenant/TenantSchemaMigrator`
  (`SmartInitializingSingleton`, runs during context refresh **before** the web server binds, fail-fast) +
  `TenantMigrationProperties` (`goaml.tenant.migrate-on-startup`, default true). On boot it iterates every
  ACTIVE `public.tenant.schema_name` and runs the SAME programmatic tenant Flyway
  (`classpath:db/migration/tenant`, `schemas(<schema>)`) — picking up G2's V8 and G3's V9. Idempotent;
  zero-tenant boot is a no-op. Test (Testcontainers): `TenantSchemaMigratorTest` — older schema (Flyway
  `target=7`) is migrated forward to the V8 column; zero-tenant no-op. **Cross-wave: this is the runner the
  G2 note asked for — it migrates existing tenants through V8 + V9.**
- **A5-goAML (HIGH) — review gate hardened.** (1) `DevDataSeeder` SCREENING `default_role` MLRO→**ANALYST**
  (federated cockpit users land least-privilege; MLRO only by explicit goAML admin action). No DB default to
  override — V6 added `default_role` with no default; the only MLRO default was the seeder. (2)
  `DefaultReportReviewService.approve()` now REJECTS when approver == author
  (`ReportExceptions.SelfApprovalNotAllowedException` → 409). (3) Verified `review_required` submit
  enforcement already correct in `DefaultSubmissionService` (reads `tenant_goaml_config.review_required`;
  requires APPROVED when true). Tests: `DefaultReportReviewServiceTest` (self-approve→409, different
  approver→APPROVED); `ReportReviewE2ETest` updated so a DIFFERENT MLRO approves + asserts author self-approve
  is 409 (and review_required=true still blocks a merely-VALID submit).
- **B6 (HIGH) — user hard-delete guard + TOCTOU lock.** `DefaultAdminService.deleteUser` now locks the user
  row (`AppUserRepository.findByIdForUpdate`, PESSIMISTIC_WRITE) up front, then blocks delete if the user is
  referenced by report.created_by/reviewed_by **OR** attachment.uploaded_by / import_job.created_by /
  notification.recipient_user_id (new `existsBy…` repo methods) → existing 409 `UserReferencedException`
  (steer to disable). Tests: attachment-only / import-only / notification-only each → 409; unreferenced →
  deletes; self-delete guard still holds.
- **B15 (MEDIUM) — attachment content scanning.** New `AttachmentContentInspector` (magic-byte sniff: reject
  ELF/PE/Mach-O/`#!` executables + declared-vs-actual content-type mismatch, runs ALWAYS before persist) +
  pluggable `AttachmentScanner` interface with `NoopAttachmentScanner` default, real hook gated by
  `goaml.attachments.av.enabled` (default false — no AV infra needed today). Wired into
  `DefaultAttachmentService.add` before the S3 put; size/filename/extension checks kept. Tests: renamed
  ELF/PE declared application/pdf → rejected; PNG-as-pdf mismatch → rejected; real PDF/PNG → accepted; plus
  `AttachmentContentInspectorTest` (Mach-O, shebang, matching/unknown pass).
- **B8 (HIGH) — duplicate-poll gate + metric.** New top-level `goaml.scheduler.enabled` (default true; binds
  env `GOAML_SCHEDULER_ENABLED` via relaxed binding) added to `SchedulerProperties`; `scheduledPoll()`
  early-returns when it (or the finer `status-poll.enabled`) is false → the chart's
  `GOAML_SCHEDULER_ENABLED=false` disables polling on web replicas. Added Micrometer counter
  **`goaml.poller.errors`** incremented on each per-tenant poll failure (alongside the existing log-and-skip).
  Tests: master-switch off → no-ops; per-tenant failure increments the counter and doesn't throw.
- **C2/C3/D6 — migrations.** `tenant/V9__constraints_and_audit.sql`: CHECK on report.status (9 states),
  submission.status (4), notification.type (3), import_job.status (3) + source_type (2) — values grounded in
  the Java vocabularies, verified by grep; D6 `submission.submitted_by UUID NULL` + indexes on
  report.created_by / report.reviewed_by / submission.reportkey. `shared/V8__constraints.sql`: CHECK on
  app_user.status, tenant.status, trusted_service.status + source_system, external_identity /
  tenant_external_ref source_system, tenant_goaml_config.auth_mode; **C3** trusted_service.default_role
  constrained to the role.name set (NULL allowed → code maps to ANALYST). Additive + safe on existing
  (pre-prod) data; an out-of-set legacy row would fail the ALTER by design (noted in each file).
  `Submission.submittedBy` mapped; `DefaultSubmissionService.submit` sets it to the acting MLRO (D6 test).
- **C9 (LOW) — submit re-validates stored XML.** `DefaultSubmissionService.submit` re-runs
  `XsdSchemaValidator` on `report_xml` before zipping (`StoredXmlInvalidException` → 409). Cheap
  defense-in-depth so a future bad-XML write can't reach the FIU. Test: tampered/invalid stored XML refused.
- **C11 (LOW) — dead code.** Deleted unreferenced `model/mapper/tenant/TenantMapper` +
  `model/dto/tenant/TenantDto` + their `TenantMapperTest` (only referenced each other / the test).
  `LookupExceptions` relocation skipped (touches controller-package import; low value, marked optional).
- **D8 (LOW) — dev-seeder.** default_role→ANALYST (above) + a startup **WARN** when the seeded demo tenant
  has no `tenant_goaml_config` (so a fresh env knows reports will validate INVALID at rentity_id=0 until an
  admin sets config). Seeder stays gated (`goaml.dev.seed.enabled`).
- **jacoco + testHeap (build.gradle).** `coveredPackages` += `controller/ingestion/**`,
  `controller/notification/**`, and the newly-tested security-core classes (`LoginRateLimiter*`,
  `UserStatusCache*`, `IntegrationAuthFilter*`); `scheduler/**` + `service/notification/**` were already
  listed. `maxHeapSize` now reads `-PtestHeap` (default 2g) so the documented knob actually works.

**New/affected tests (all green locally):** `TenantSchemaMigratorTest` (Testcontainers, 1),
`DefaultReportReviewServiceTest` (2), `ReportReviewE2ETest` (Testcontainers, updated 4),
`DefaultAdminServiceTest` (+3 B6 = 24), `DefaultAttachmentServiceTest` (+4 B15 = 15),
`AttachmentContentInspectorTest` (3), `DefaultSubmissionServiceTest` (+C9/D6 = 15),
`SubmissionStatusPollerTest` (+metric/master-switch = 7), `RetryServiceTest` (6, ctor fix). Provisioning +
SharedSchema tests pass → V9/shared-V8 migrations apply cleanly.

Files changed (G3):
- `src/main/.../config/tenant/TenantSchemaMigrator.java` + `TenantMigrationProperties.java` — NEW (A2)
- `src/main/.../config/scheduler/SchedulerProperties.java` — top-level `enabled` (B8)
- `src/main/.../scheduler/SubmissionStatusPoller.java` — master-switch gate + `goaml.poller.errors` (B8)
- `src/main/.../service/report/DefaultReportReviewService.java` + `ReportExceptions.java` — A5 SoD + 409
- `src/main/.../service/admin/DefaultAdminService.java` — B6 lock + extended guard
- `src/main/.../repository/appuser/AppUserRepository.java` — `findByIdForUpdate` (B6 lock)
- `src/main/.../repository/{attachment,ingestion,notification}/*Repository.java` — `existsBy…` (B6)
- `src/main/.../service/attachment/AttachmentScanner.java` + `NoopAttachmentScanner.java`
  + `AttachmentContentInspector.java` — NEW (B15); `DefaultAttachmentService.java` wires them
- `src/main/.../service/submission/DefaultSubmissionService.java` + `SubmissionExceptions.java` — C9 + D6
- `src/main/.../model/entity/submission/Submission.java` — `submittedBy` (D6)
- `src/main/.../config/dev/DevDataSeeder.java` — default_role ANALYST + no-config WARN (A5/D8)
- `src/main/.../exception/GlobalExceptionHandler.java` — SelfApproval + StoredXmlInvalid → 409
- `src/main/resources/db/migration/tenant/V9__constraints_and_audit.sql` — NEW (C2/D6)
- `src/main/resources/db/migration/shared/V8__constraints.sql` — NEW (C2/C3)
- `src/main/resources/application.yml` — `goaml.tenant.migrate-on-startup`, `goaml.scheduler.enabled`,
  `goaml.attachments.av.enabled`
- `build.gradle` — jacoco allowlist additions + `-PtestHeap` wiring
- DELETED: `model/mapper/tenant/TenantMapper.java`, `model/dto/tenant/TenantDto.java`,
  `test/.../model/mapper/tenant/TenantMapperTest.java` (C11)
- NEW tests: `TenantSchemaMigratorTest`, `DefaultReportReviewServiceTest`, `AttachmentContentInspectorTest`;
  extended `DefaultAdminServiceTest`, `DefaultAttachmentServiceTest`, `DefaultSubmissionServiceTest`,
  `SubmissionStatusPollerTest`, `RetryServiceTest`, `ReportReviewE2ETest`.

### NOTE → Wave 4
- **A2 runner depends on G2's V8** + G3's V9 — both must be on the classpath when the migrator runs (they are).
- **jacoco allowlist grew** (`controller/ingestion`, `controller/notification`, 3 security classes). If any
  is below the 90%/80% bar when the full gate runs, either it has a test gap to fill or pull that one entry.
- **DB CHECK constraints are additive** — they assume clean pre-prod data. If any environment has a legacy
  out-of-set status/role value, the V9/shared-V8 ALTER will fail (intended guard) and that row must be fixed.
- The C9 submit re-validation means `DefaultSubmissionServiceTest` now injects a mocked `XsdSchemaValidator`;
  real-XSD coverage of the submit path stays in the engine/E2E tests.

---

## Wave 4 fallout fixed (post-remediation test integration)

The full gate (`./gradlew test jacocoTestCoverageVerification -PtestHeap=2g`) had **17 failures across 4
classes + 1 IT**, all fallout from the security/domain fixes (not regressions of them). Root-caused to **3
clusters**, **2 root causes**, fixed and re-green:

- **Root cause #1 (clusters 2, 3, and ALL of cluster 4): `IntegrationAuthFilter` never ran in the full
  `@SpringBootTest` context.** Its `shouldNotFilter` / source-derivation matched on
  `request.getServletPath()`, which under Spring Boot 3 / Servlet 6 (DispatcherServlet mapped to `/`) is the
  **empty string** — so `"".startsWith("/api/v1/integration/")` was false and the filter skipped every
  integration request. Result: missing assertion reached the controller (accounting/lookup → 200/202 instead
  of 401), and screening's `@RequestAttribute(VERIFIED_ASSERTION_ATTR)` had nothing stashed → Spring 400 for
  *everything* (even valid pushes). **Code fix** in `security/IntegrationAuthFilter.java`: match on the
  request URI minus context path (`pathWithinApplication`) instead of the servlet path — stable across MockMvc
  and a real container. The C1 intent (filter-based auth, 401 on missing/invalid, single-use stash) is intact
  and *now actually enforced*.
- **Root cause #2 (1 of cluster 4): `ScreeningFilingE2ETest.unmappedCompanyIsNotFound` was a stale test.**
  It posted company `999` but minted an assertion whose `org` claim was hardcoded to `601`, so the (correct,
  intended) B11 org-check rejected it 401 before it could reach the 404 tenant-resolution path. **Test fix**:
  added a parameterized `assertionFor(org)` (mirroring `ScreeningIntegrationE2ETest`) and minted
  `assertionFor("999")` so org matches the unmapped company → reaches 404. B11 enforcement unchanged.
- **Cluster 1 (`ObservabilityIT.prometheusEndpointServesMetrics`): encoded the OLD public-metrics contract.**
  D2 moved `/actuator/prometheus` behind auth on purpose. **Test fix**: split into
  `prometheusEndpointIsUnauthorizedWithoutAuth` (→ 401) and `prometheusEndpointServesMetricsWhenAuthenticated`
  (valid minted JWT → 200 + `jvm_` metrics), using the same `JwtService.issueAccessToken` + seeded ACTIVE
  `app_user` pattern as `LookupApiTest`. Prometheus stays protected.

**Files changed**
- `src/main/java/com/vyttah/goaml/security/IntegrationAuthFilter.java` — CODE: match request-URI-minus-context
  path, not servlet path, so the filter actually runs for `/api/v1/integration/**`.
- `src/test/java/com/vyttah/goaml/security/IntegrationAuthFilterTest.java` — TEST: mock now sets requestURI as
  well as servletPath (mirrors a real container).
- `src/test/java/com/vyttah/goaml/controller/integration/ScreeningFilingE2ETest.java` — TEST: `assertionFor(org)`
  helper; unmapped-company case mints a matching-org assertion so it reaches the 404 path.
- `src/test/java/com/vyttah/goaml/config/observability/ObservabilityIT.java` — TEST: prometheus now asserts
  401 unauthenticated + 200 authenticated (valid JWT); class Javadoc updated.

**Final gate result:** `./gradlew test jacocoTestCoverageVerification -PtestHeap=2g` → **BUILD SUCCESSFUL —
526 tests, 0 failures, 0 errors, 1 skipped** (526 vs the prior 525 because the single prometheus test split
into two). Coverage verification passes; no jacoco allowlist entry needed pulling. No intended fix weakened:
filter-based integration auth, B11 org-claim enforcement, B4 full field carriage, and protected prometheus
all stand — they are now correctly exercised by green tests.
