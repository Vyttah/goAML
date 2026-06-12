# Official-documentation review + fix wave — 2026-06-12

**Method:** 3 research agents against official sources (uaefiu.gov.ae, moet.gov.ae, uaelegislation.gov.ae,
UNODC goAML 5.0.2 schema doc, peer-FIU goAML guides) + 3 expert code reviewers over both repos (focused on
the never-independently-reviewed remediation code at goAML `b299999` / AML FE `5b599d7` / AML BE `e292f77`),
then a 3-agent fix wave. Follows `e2e-audit-2026-06-11.md` + `e2e-audit-remediation-progress.md` — read those
first; this doc only covers the *new* findings.

---

## 1. Official-source verification (research verdicts)

### Confirmed (official, with sources)
| Assumption | Verdict | Source |
|---|---|---|
| `DPMSR` is the right report code for DPMS threshold reports | ✅ exclusive to DPMS; suspicion goes via STR/SAR | FIU "Different types of reports on goAML" V1.2 (29-Apr-2024) |
| AED 55,000 threshold, single-or-linked | ✅ unchanged, now Cabinet Resolution 134/2025 Art 3(3) | uaelegislation.gov.ae; MOE Circular 08/AML/2021 |
| DPMSR filing deadline | ✅ **within 2 weeks** of the transaction (FIU Reports FAQs V1.2 Q8; the circular itself has no deadline) | uaefiu.gov.ae Reports FAQs |
| Record retention | ✅ **5 years** from transaction completion / relationship end (CR 134/2025 Art 25) | uaelegislation.gov.ae |
| MLRO mandatory + submitting persona | ✅ compliance officer mandatory (CR 134/2025 Arts 21(3)+22); MLRO is the goAML registering/submitting persona | FIU registration guide + FAQs |
| Activity-shaped DPMSR, lenient `t_person` parties | ✅ UAE's own DPMSR guide builds activity parties as plain Person/Entity — **no My-Client selector exists in the DPMSR flow**; UNODC doc: my_client is FIU policy, structurally identical | UAE DPMSR guide v1.3; UNODC 5.0.2 doc §4.2.1 |
| ≥1 report indicator mandatory | ✅ XSD (5.0.2 requires 1..many) **and** server-side (UAE/Malta/Denmark all reject without one) | vendored XSD line ~312; UAE web guide |
| Attachments | ✅ **5 MB per file, 20 MB per report**, short English filenames | FIU FAQs v2.1 Q33 |
| Registration | ✅ SACM pre-registration → goAML registration; supervisory approval by **Ministry of Economy & Tourism** (moet.gov.ae) for DPMS | FIU pre-registration guide |

### Law replaced (docs must cite the new instruments)
- **Federal Decree-Law 10/2025** (eff. 14-Oct-2025) **repealed** Decree-Law 20/2018.
- **Cabinet Resolution 134/2025** (eff. 14-Dec-2025) **repealed** Cabinet Decision 10/2019.
- Obligations/threshold materially unchanged for DPMS; penalty band reportedly AED 10k–5M (secondary).
- MOE Circulars 5/2021 + 08/AML/2021 not found repealed — still applied in practice; confirm with MOET.

### Business-rule gaps found (now encoded — see fix 15)
DPMSR trigger is **not cash-only** (MOE Circular 08/AML/2021 + FIU goAML FAQs Q51):
- Individuals: **cash** ≥ 55k reportable; card/cheque/bank-transfer NOT reportable.
- Legal persons: ≥ 55k **cash or wire**; local UAE B2B bank wires NOT reportable **unless via an exchange
  house**; **all international wires reportable** (inward + outward); cheques not reportable.
- Installments on one invoice: one report, at funds receipt. Bearer instruments/trade-ins count (MOE
  supplemental guidance, consultation draft).

### Cannot be verified publicly (go-live actions)
1. **The UAE B2B API** — no public spec exists (endpoints/auth/ack schema). The "B2B" in FIU FAQs means
   business-to-business *transactions*. Our `b2b/` transport must be validated against the FIU-issued spec
   (goaml@uaefiu.gov.ae) before Phase E. Peer-FIU evidence (South Africa): B2B component = folder drop +
   `.txt` error files; failed XML never reaches the FIU.
2. **UAE XSD version** — never stated publicly; 5.0.2 corroborated only via peer FIUs. Verify in-portal.
3. **Business Rejection Rules (BRR) list** — login-gated on the goAML web homepage. Known from guides:
   entity_reference compulsory; per-party Reason, ID Number (EID **and** ID Number for residents, no
   spaces/hyphens), Location of Incident, goods Item Type + Invoice Amount (in **AED**) are rejection-backed.
4. **Status pipeline** (peer-FIU, Sri Lanka manual): Uploaded → Failed Validation(/Invalid Structure) →
   Transferred from Web → Approved-scheduled → **Processed** (= submitted) / **Rejected** (free-text reason
   via Message Board; Revert → version+1, 10-day grace in UAE). Useful field conventions (UAE web guide):
   unknown DOB → 1900-01-01, unknown ID expiry → 2100-12-31, phones without 00/+/hyphens, description ≤4000.

---

## 2. Code findings + fixes (all fixed in this wave unless noted)

### goAML backend (agent cut off by session limit; work verified on disk + gate re-run)
| # | Sev | Finding | Fix |
|---|---|---|---|
| 1 | HIGH | V9 `notification_type_chk` omitted `REPORT_PENDING_REVIEW` (written by notifyDraftAwaitingReview) → silent MLRO-notification failures | new `tenant/V10` recreates CHECK with full vocabulary |
| 2 | HIGH | Accounting integration push had no org-claim tenant binding (cross-tenant write with any valid ACCOUNTING assertion) | `requireOrgMatches` on all 3 handlers, org claim mandatory, mirrors screening B11 |
| 3 | MED | Replay-store cleanup dead code (`deleteExpired` without `@Transactional`, exception swallowed) | `@Transactional` on repo method; cleanup failures logged |
| 4 | HIGH | `GoamlDateTimeAdapter` normalized to UTC → +04:00 local-midnight dates filed as the **previous day** | marshal at the caller's original offset (wall-clock preserved); fidelity test with +04:00 |
| 5 | HIGH | CSV import fabricated `country_of_birth` from nationality | own column / null, never fabricated; row test |
| 6 | HIGH | Double-submit race → duplicate FIU filings | atomic CAS `claimForSubmission` (status→`SUBMITTING`, V11 CHECK update); 0 rows → 409; restore-on-failure |
| 7 | MED-HIGH | NameNormalizer silently rewrote legal names | every change surfaced as `NAME_NORMALIZED` WARNING in create/validate/preview responses; emptied name → `NAME_UNREPRESENTABLE` ERROR; original preserved in `report.input` |
| 8 | MED | `contains("accept")` status mapping ("Not Accepted" → ACCEPTED); terminal status overwritable | whitelist mapping, unknown → keep SUBMITTED + warn; terminal states guarded |
| 9 | MED | Raw `entity_reference` as ZIP entry name (slashes nest dirs, `..` escapes) | sanitized entry filename |
| 10 | MED | Poller error metric blind to per-report FIU failures → alert never fires in an outage | counter incremented in inner catch |
| 11 | MED | Countries test was subset-only (regression to 10 codes would pass) | set equality vs XSD `country_type` (253 = 253) |
| 12 | MED | XML import: un-hardened JAXB unmarshal (XXE window) + foreign `rentity_id` accepted | hardened SAXSource (DTDs/external entities off); rentity_id must match tenant config |
| 13 | LOW | reportingPerson dropped taxRegNumber/alias (real XSD slots) | mapped; wire-fidelity test extended to previously-unasserted fields |
| 14 | LOW | Party with both person+entity silently dropped the person | validation error: exactly one |
| 15 | BUSINESS | ReportabilityDetector was cash-only | extended with `ReportabilityFacts` (counterparty type, payment type, wire domestic/international, exchange-house) per the official rule table; additive, unknown facts → prior behavior; reasons cite the rule; docs re-anchored to DL 10/2025 + CR 134/2025 |
| 16 | MED | One tenant's failed migration aborted ALL-tenant boot | per-tenant catch + `goaml.tenant.migration.failures` metric + `fail-fast` property (default false) |
| 17 | LOW | `UserStatusCache.evict()` never wired | wired on admin disable/delete |

Also verified correct (no change): default-secret guard, RS256 assertion validation (no alg confusion),
replay PK atomicity, IntegrationAuthFilter URI matching, attachment content inspector wiring, submit-time
re-validation, poller TenantContext hygiene, clientMetadata never reaching XML, XSD gate on every path.

### AML cockpit (`Frontend_Customer`, all fixed)
| # | Sev | Finding | Fix |
|---|---|---|---|
| C1 | CRIT | Natural-subject address **still** silently omitted (A4 half-fix): no `addressCountry` input/prefill, and warning+consent gated on the missing field | KYC-derived editable Address Country select for both kinds; warning + consent fire on ANY partial address |
| C2 | HIGH | Draft restore clobbered by KYC prefill effect; selectedRelations not in draft | prefill skipped for one pass on restore; relations saved+restored |
| C3 | HIGH | Partial-ID KYC → unsubmittable with unactionable error (no editable inputs) | editable identity & ID section (nationality, birth country, ID quartet) |
| C4 | MED | UTC date defaults (00:00–03:59 local → yesterday, today unpickable); stale drafts silent | local-time `localToday()`; `savedAt` + >24h restore banner |
| C5 | MED | Single-token relation names silently dropped after consent built | surfaced in consent as "cannot split — will be omitted" (never fabricated) |
| C6 | MED | Subject-level unresolved countries pruned silently | consent list extended to subject fields with raw KYC values |
| C7 | LOW | Threshold hint summed mixed currencies | AED-only sum + exclusion note |
| C8 | — | No assembly-level tests | `assembleReport.ts` extracted; 12 new golden tests |

### goAML SPA (all fixed; gates green: typecheck/lint/100 tests)
| # | Sev | Finding | Fix |
|---|---|---|---|
| S1 | HIGH | Draft restore crashed on nested dates (ISO strings into AntD DatePickers) | deep-walk rehydration to dayjs |
| S2 | HIGH | `.toISOString()` shifted picked dates to UTC | serialize picked calendar date pinned to `T00:00:00Z` |
| S3 | MED-HIGH | SPA filed persons as `personMyClient` (5.2 path, 1-char tax-reg trap, diverged from cockpit/backend/samples) | converged on lenient `person` (officially grounded — see §1); detail view reads both keys for old reports |
| S4 | MED | zod drift: indicators optional (server mandates ≥1); reportingPerson wrongly required | indicators required min 1; MLRO section optional (server default from tenant config) |

---

## 3. Outstanding (user decisions / external)
Unchanged from the remediation tracker: A6 (delete PII bundle, add remote, push), A8 (two-paths
convergence), D5 (orphan INVALID cleanup), AML residuals, FIU/SACM registration + B2B spec + BRR list +
in-portal XSD version confirmation (§1 "cannot be verified publicly"), AWS account. New from this review:
confirm with MOET whether Circulars 5/2021 + 08/AML/2021 are re-issued under the 2025 framework.
