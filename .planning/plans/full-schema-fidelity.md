# Plan — Full-schema fidelity for the DPMSR contract (1:1 with the official goAML XSD)

> **Trigger:** a third real DPMSR (`assets/USG0000000297 299.xml`, from another vendor's software) exposed
> ~13+ schema fields our JSON contract silently drops. User decision: the contract should be **full schema
> 1:1** — a caller can supply (and the output XML must contain) **every** element the goAML schema defines,
> with only our genuinely server-applied values injected on our side.

## Root cause (why we "missed" fields despite having the official XSD)

We did **not** lose schema fidelity. `xjc` generated the full goAML domain from the official XSD — every
`T*` type carries every element. Fidelity is intact at:
- **Domain:** `Report`, `TPerson`, `TEntity`, `TTransItem`, `TAddress`, … (all elements present).
- **Engine input:** `engine/build/DpmsrReportInput` already carries the **generated leaf types**
  (`TPersonRegistrationInReport`, `ReportPartyType`, `TTransItem`, `TAddress`) — full fidelity.

The loss is in **one layer only**: the curated hand-written JSON DTO `model/dto/report/DpmsrCreateRequest`
+ `model/mapper/report/DpmsrRequestMapper`. It exposes the subset of fields our earliest minimal samples
used. So the gaps are an **exposure gap in the curated DTO, not a schema miss** — closing them is additive
at the contract layer; no `xjc` regen, no domain change.

## De-risking already done (facts, don't re-derive)

- DPMSR activity path is **JAXBElement-free** (`JAXBElement` only in `ObjectFactory` + `IpAddressType`).
- Generated date fields bind to **`OffsetDateTime`** (custom adapter via `src/main/jaxb/goaml-bindings.xjb`),
  not `XMLGregorianCalendar` → Jackson-friendly.
- Server-forced header fields live in `engine/build/ReportHeaderApplier`: `rentity_id`, `submission_code`
  (`E`), `report_code` (`DPMSR`), `currency_code_local` (`AED`), plus schema_version. These stay
  server-controlled on **input**; they still appear in **output**.
- The gap inventory (per owner type) is recorded in `docs/` follow-up; headline gaps:
  - **goods/`TTransItem`** (10/17 exposed): `disposedValue`, `statusComments`, `registrationNumber`
    (string invoice ref — we only had a *date*), `identificationNumber`, `previouslyRegisteredTo`, `address`,
    `comments`.
  - **entity/`TEntity`**: `addresses`, `incorporationDate`, `incorporationLegalForm`, `business`,
    `taxNumber`, `emails`, `url`, `entityStatus`, `comments`, `entityIdentifications`, multi-phone.
  - **person/director (`TPerson` family)**: `ssn`, `passportNumber/Country` (our `Person` record had none),
    `addresses` (multi), `identifications`, `employerName/AddressId/PhoneId`, `middleName`, `alias`,
    `mothersName`, `birthPlace`, `emails`, `sourceOfWealth`, `nationality2/3`, multi-phone, + ⚪ KYC blocks.
  - **leaves**: `TAddress` (5/20 — `town`, `zip`, `houseNumber`, `apartmentNumber`, addl lines, `comments`);
    `TPhone` (`extension`, `comments`); `TPersonIdentification` (`issuedBy`, `comments`).
- **Silent-drop bug:** the `reportingPerson` mapper ignores `taxRegNumber`, `countryOfBirth`,
  `identifications` that the `Person` DTO *does* carry — fixed implicitly when the DTO layer is replaced.

## Chosen approach — bind the JSON contract to the generated domain types

Eliminate the parallel hand-DTO. The report create/validate/preview contract accepts a payload whose nested
objects **are the generated types** — i.e. essentially expose `DpmsrReportInput`'s shape as the JSON contract
(`reportingPerson: TPersonRegistrationInReport`, `parties: [ReportPartyType]`, `goods: [TTransItem]`,
`location: TAddress`, header scalars). Result: JSON shape == schema shape, **1:1, self-maintaining** (any
future `xjc` regen updates the contract automatically).

- **Server injects/overrides** `rentity_id` (from `tenant_goaml_config`), `submission_code=E`,
  `report_code=DPMSR`, `currency_code_local=AED`, `schema_version` — caller-supplied values for these are
  ignored. Everything else is caller-supplied and round-trips to the output XML.
- **Output is everything**: marshalling the built `Report` already emits every set element (incl. the
  injected server fields) — satisfies "in response it should be everything."

**Rejected alternative:** hand-expand `DpmsrCreateRequest` to mirror the full schema — hundreds of nested
fields, perpetual hand-maintenance, drifts from the XSD. Defeats the purpose.

### Known wrinkles to handle (in Step 0 spike)
- Generated types have **no Jackson annotations** → register a small Jackson setup: serialize via
  getters/setters; **xjc enums** (`CurrencyType`, `EntityPersonRoleType`, `ReportType`, …) expose
  `value()`/`fromValue()` → a tiny Jackson module (or mixins) to bind enums by their schema string.
- **Wrapper inner classes** (`TPerson.Phones { List<TPhone> phone }`) serialize as nested objects
  `{"phones":{"phone":[…]}}` — mirrors the XML; acceptable and intentional.
- **Bean-validation loss:** we drop `@NotBlank/@NotNull` on the DTO. Replace with engine-level required-field
  checks; the **XSD gate (`XsdSchemaValidator`) remains authoritative** for completeness.
- **Persistence:** still JSONB; store the full payload verbatim (re-validate/re-edit unaffected).

## Build — gated steps (each = atomic commit, full gate, planning sync, `--no-ff` to `main`)

**PII prerequisite (do first, blocks committing any sample):** the staged real-PII files
`assets/USG0000000297 299.xml`, `assets/TR.2079.*.xml` must not be committed as-is — the repo was history-
purged precisely so it could be pushed. Either `git rm --cached` + add `assets/*.xml` to `.gitignore`
(keep as local-only reference), or anonymize. **Anonymized copies** go into `src/test/resources/samples/`
for the golden tests (extend the existing `/tmp/anon.py` map with the new tokens).

**Step 0 — Spike + golden harness.** Prove Jackson round-trips the DPMSR generated types (enum + wrapper +
date) both ways. Add a domain round-trip test: unmarshal each anonymized real sample XML → `Report` →
re-marshal → assert semantic field-equality (proves the *domain* is lossless — it is). Jackson enum module.

**Step 1 — Full-fidelity contract type.** Introduce the new request shape bound to generated types (reuse
`DpmsrReportInput` or a thin `@JsonIgnoreProperties` wrapper exposing the same slots). Server-field injection
helper. Unit tests: JSON → contract → `DpmsrReportInput` with all sample fields present.

**Step 2 — Wire `ReportService`.** `create/validate/previewXml` accept the new contract; persist full JSON;
keep the duplicate-`entity_reference` guard. Adapt `DefaultReportService` (drop `DpmsrRequestMapper` or
reduce it to enum/normalization helpers).

**Step 3 — Migrate call sites.** MCP `ReportTools`; **goAML XML importer** (gets *simpler* — unmarshal real
XML straight to the contract, no lossy re-mapping); CSV importer; `AccountingDpmsrMapper`;
`ScreeningPartyMapper`. Each keeps its existing tests green (adjust constructors — positional records change).

**Step 4 — Frontend builder.** Expand the SPA builder to the richer model **pragmatically** — the API is
full-fidelity; the SPA is a convenience, so surface the common 🔴/🟡 fields and keep the form working; do not
attempt to render every ⚪ KYC field. Vitest specs updated. (May be split into its own follow-up.)

**Step 5 — Golden round-trip proof (the deliverable).** Per real sample (`USG…`, `TR…`×2, existing DPMSR):
real XML → contract JSON → build → marshal → assert field-equivalence (minus server-injected header). This
**mechanically proves "no missing fields"** and locks it against regression.

**Step 6 — Coverage doc + planning sync + merge.** Write `docs/` coverage matrix (exposed vs ⚪-excluded
rationale if any are deliberately omitted — under this decision, none are), sync `STATE.md`/`ROADMAP.md`,
`--no-ff` merge.

## Risks / decisions still open
- **Frontend scope:** full-schema UI is large; recommend surfacing common fields now, progressive disclosure
  later. (Step 4 may be deferred to its own phase.)
- **Contract ergonomics:** binding to generated types gives XML-shaped JSON (wrapper objects). If a flatter
  public JSON is later wanted, add a thin façade — but that re-introduces a curated layer, so only on demand.
- **`TR.*` (transaction reports)** are a *different* report type (transaction-shaped, not DPMSR/activity).
  This plan is DPMSR-scoped; the same full-fidelity approach generalizes to them but is out of scope here.
