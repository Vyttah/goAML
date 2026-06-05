# Step 5 — Reconcile lookups with the XSD & complete golden coverage

> **Status: ✅ DONE (2026-06-04) — transmode reconciled, all 7 goldens XSD-valid, consistency guard added. See "Outcome".**
> Part of [../plans/xsd-first-foundation.md](../plans/xsd-first-foundation.md) (step X.5).
> Predecessors: Steps 1–4 ✅ (XSD gate, codegen, round-trip, engine re-pointed onto the generated model).
> Closes the **Step-4 carry-over**: the transaction-shaped goldens (STR/AIFT/ECDDT) were deferred because the
> `transmode` lookup is disjoint from the XSD enum.

---

## 1. What this step is, and why

Step 4 made `EngineGoldenTest` **XSD-validate** every marshalled golden. That surfaced a real data bug: the
hand-authored **`lookups/ae/transmode.json`** (`CASH, WIRE, CHEQUE, BANKD, CARD, CRYPTO`) is **disjoint** from
the goAML XSD enum that `transmode_code` actually uses — `conduction_type`
(`ATM, ATMWT, BO, CBMNT, CCD, CDM, CRNEX, DAWLT, DBCD, DRPCB, DSB, DTB, ELCFT, INTRN, LCRD, MB, OCT, ONBNK,
PPCD, SWIFT`). The lookup conflated **funds types** (cash/cheque/card — those belong to `funds_code`) with
**conduction/transmission modes**. So a transaction that passes the business-rule validator (which checks the
lookup) could still be **XSD-invalid** — which is why the STR/AIFT/ECDDT goldens were held back.

Step 5 fixes that mismatch at the source, restores the 3 transaction goldens, and adds a permanent guard so
**lookup ↔ XSD drift can't silently happen again** — the root cause, not just this instance.

> **Root issue:** goAML has two parallel sources of allowed values — the **XSD enums** (structural, authoritative)
> and the **per-jurisdiction lookup JSONs** (business-rule validation, meant to be a jurisdiction-specific
> *subset*). A lookup must always be a **subset of its XSD enum**, or validator-clean reports fail the schema.
> Today only `transmode` violates this; this step makes the invariant explicit and tested.

## 2. Scope (and what's deliberately deferred)

**In scope:** make all **currently-modeled report types (the 7 in `SampleReports`: DPMSR, SAR, AIF, ECDD, STR,
AIFT, ECDDT)** XSD-golden-covered, and lock the lookup↔XSD invariant.

**Deferred (logged, not silent):** the **other 10 schema report types** (CIR, CNMRA, HRC, HRCA, IRR, ITR,
PNMRA, PSTR, REAR, SIR). Per the locked project scope (DPMSR first; PNMRA/CNMRA sanctions near-term; **all 17
"next phase"**), building goldens for report shapes we don't yet support would mean inventing sample data and
validator rules for undesigned flows. They arrive with their functional phase (PNMRA/CNMRA with the Phase 1.5
sanctions integration; the rest later). This step does **not** silently claim 17-type coverage.

## 3. What I'll build / change

**3.1 Reconcile `lookups/ae/transmode.json` to real `conduction_type` codes.** Replace the funds-flavoured
entries with a sensible UAE subset of `conduction_type`, e.g. `ELCFT` (electronic funds transfer), `SWIFT`,
`CDM` (cash deposit machine), `ATM`, `ONBNK` (online banking), `OCT` (over-the-counter). Labels are best-effort;
the authoritative UAE conduction codelist is a field-acquisition item (see
[field-acquisition-checklist.md](../field-acquisition-checklist.md)) — **flagged, not blocking**: the goal here
is consistency (lookup ⊆ XSD enum), and the chosen codes are all valid `conduction_type` members.

**3.2 (consistency tidy) `lookups/ae/funds.json`.** It also mixes modes with funds (`WIRE/CHEQUE/CARD/CRYPTO`
aren't `funds_type` codes). The validator doesn't currently check it, so it has no XSD impact — but I'll align
it to a `funds_type` subset (`CASH, BANKD, CHQ, …`) for correctness while I'm here. (currencies.json and
countries.json are already valid subsets of `currency_type`/`country_type` — verified — so they're untouched.)

**3.3 Update the transaction sample's `transmode_code`** in `SampleReports.bipartyCashTransfer(...)` from
`"CASH"` to a code valid in **both** the reconciled lookup and the XSD enum (e.g. `"ELCFT"`). (Funds direction
stays `FundsType.CASH`/`FundsType.BANKD` — those are correct.)

**3.4 Restore STR/AIFT/ECDDT in `EngineGoldenTest`.** Re-add the 3 transaction types to the `@EnumSource` so the
test marshals + XSD-validates + golden-compares all **7** modeled types; regenerate the 3 transaction goldens
(via `-Dgoaml.golden.regenerate=true`, which still XSD-validates before writing). Iterate on any remaining
XSD-mandatory transaction fields the same way Step 4 did for the activity types (the sender `TPersonMyClient`
already got `birthdate`/`phones`/`tax_reg_number` in Step 4; the transaction header may surface more).

**3.5 NEW `LookupXsdConsistencyTest` (the durable guard).** For each lookup set the validator checks
(`transmode → conduction_type`, `currencies → currency_type`), load the JSON and assert **every code is a member
of the corresponding XSD enumeration**. This fails fast if anyone adds a lookup value the schema won't accept —
preventing a repeat of the transmode drift. Pure file/parsing test; no Docker.

## 4. Files touched

| File | Change |
|---|---|
| `src/main/resources/lookups/ae/transmode.json` | **rewrite** — `conduction_type` subset |
| `src/main/resources/lookups/ae/funds.json` | **tidy** — `funds_type` subset (consistency; validator-unused) |
| `src/test/.../engine/SampleReports.java` | transaction `transmode_code` → valid code (e.g. `ELCFT`) |
| `src/test/.../engine/EngineGoldenTest.java` | `@EnumSource` back to all 7 types |
| `src/test/resources/golden/{STR,AIFT,ECDDT}.xml` | **regenerated + XSD-validated** (restored) |
| `src/test/.../engine/lookup/LookupXsdConsistencyTest.java` | **new** — lookup ⊆ XSD-enum invariant |
| *(unchanged)* engine, generated domain, activity goldens | — |

## 5. How it's verified

```bash
./gradlew test --tests '*EngineGoldenTest' --tests '*LookupXsdConsistencyTest' \
               --tests '*ReportValidatorTest' --tests '*GoamlXsdValidationTest'
```
**Expected:** all **7** report types marshal, XSD-validate, and match their goldens; the consistency test
passes (every validator lookup ⊆ its XSD enum); the validator's transmode-lookup tests still pass against the
reconciled codes. No Docker needed. Then a full `./gradlew test` (Docker up) stays green.

## 6. Risks / caveats (honest)

1. **Exact UAE conduction codelist unknown.** I'm picking valid `conduction_type` members with best-effort
   labels; the FIU's official UAE codelist may use a different subset. This is a **data** refinement (swap the
   JSON when the real export arrives), not a structural risk — flagged in the field-acquisition checklist.
2. **`transmode_code` semantics ≠ `funds_code`.** The fix re-bases the lookup onto conduction modes; the
   `ReportValidatorTest.transactionWithUnknownTransModeIsRejected` test uses `"NOT_A_MODE"` (still invalid, still
   passes) and `everyCanonicalSampleValidatesClean` will use the new valid code — both stay green.
3. **More transaction XSD-mandatory fields may surface** when STR/AIFT/ECDDT are XSD-validated as goldens
   (as happened for activity types in Step 4). Handled by the same iterate-until-valid loop; low risk.
4. **Scope expectation:** this step does **not** add the remaining 10 report types (§2). If you want any of them
   now (e.g. PNMRA/CNMRA), say so and I'll fold them in — otherwise they ride with their functional phase.

## 7. Done criteria

- `lookups/ae/transmode.json` ⊆ `conduction_type`; all validator-checked lookups ⊆ their XSD enums, asserted by
  `LookupXsdConsistencyTest`.
- All **7** modeled report types pass `EngineGoldenTest` (marshal + XSD-validate + golden match); the 3
  transaction goldens are restored.
- `ReportValidatorTest` still green; full suite green with Docker up.
- The 10 unbuilt report types are explicitly deferred (logged), not silently skipped.

## 8. What this step does NOT do

No new report-type builders for the unmodeled 10, no DPMSR convenience builder API (Step 6), no docs refresh
(Step 7), no engine behaviour change beyond the lookup data + sample transmode value.

---

## Outcome (2026-06-04) — ✅ done, full suite green

**Reconciled the lookups + restored all 7 goldens + added the durable guard.**
- `lookups/ae/transmode.json` rewritten to a `conduction_type` subset (`ELCFT, SWIFT, CDM, ATM, ONBNK, OCT`);
  `lookups/ae/funds.json` tidied to a `funds_type` subset (`CASH, BANKD, CHQ, ELCFT, CRPTC, POS, JEWL`).
- `SampleReports` transaction `transmode_code` → `ELCFT` (valid in both lookup and XSD).
- `EngineGoldenTest` back to all **7** types; the 3 transaction goldens (STR/AIFT/ECDDT) regenerated and
  **XSD-valid**. The DPMSR-first 4 activity goldens unchanged.
- **NEW `LookupXsdConsistencyTest`** parses the XSD and asserts each validator-checked lookup ⊆ its XSD enum
  (`transmode ⊆ conduction_type`, `currencies ⊆ currency_type`) — fails fast on future drift.

**Transaction XSD-mandatory fields discovered (same iterate-to-valid loop as Step 4), now set in the sample:**
`transmode_comment` (mandatory single-char, right after `transmode_code`); the sender `TPersonMyClient` needs
`identifications` (mandatory) whose `TPersonIdentification` needs `issue_date` before `issue_country`. (The
`birthdate`/`phones`/`tax_reg_number` were already added in Step 4.)

**Note:** `conduction_type` is generated as a plain `String` (not an enum), so the consistency test parses the
XSD directly — more robust (checks the authoritative schema, not a generated derivative).

**Deferred as planned (logged):** the other 10 report types (CIR, CNMRA, HRC, HRCA, IRR, ITR, PNMRA, PSTR,
REAR, SIR) ride with their functional phase. UAE conduction codelist is a field-acquisition refinement.

**Verify:** `./gradlew test --tests '*EngineGoldenTest' --tests '*LookupXsdConsistencyTest'
--tests '*ReportValidatorTest'` → green; full `./gradlew test` (Docker up) → BUILD SUCCESSFUL.
