# Phase 13.6 — DPMSR report builder

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Sixth step of Phase 13
> and the **largest FE step**. The guided form that turns operator input into a validated DPMSR report.

---

## 1. Goal & why
Let an analyst/MLRO build a DPMSR filing through a structured form instead of hand-writing JSON. 13.6 mirrors
`DpmsrCreateRequest` in full (per plan D1), validates client-side with Zod, drives country/currency
dropdowns from the backend lookups, and on submit renders the server's authoritative `ValidationResult`
inline.

## 2. What was built (`frontend/`)

| File | Role |
|---|---|
| `types/dpmsr.ts` | Full TS mirror of `DpmsrCreateRequest` + nested records (Phone/Address/Identification/Person/Director/Entity/Party/Goods). |
| `lib/dpmsrSchema.ts` | **Zod mirror** of the backend `@NotBlank`/`@NotNull` constraints + the party "person XOR entity" rule. `validateDpmsr()` → flat `path → message` issues. |
| `lib/dpmsrForm.ts` | `buildDpmsrPayload()` — raw AntD values → payload: Dayjs → ISO, party reduced to its `_type` branch, empties pruned (0 preserved). |
| `api/lookups.ts` + `features/lookups/useLookups.ts` | Lookup endpoints + `useLookupCodes(set)` (cached per jurisdiction+set), `useJurisdictions`/`useLookupSets` (for 13.9). |
| `components/lookups/CodeSelect.tsx` | Searchable Select backed by a lookup set; Form.Item-compatible. |
| `components/forms/{CommonFields,PersonFields,EntityFields,GoodsFields}.tsx` | Reusable nested sub-forms (phone/address; person + identifications list; entity + directors list; goods item) addressed by a `name` path prefix so they compose at top-level and inside `Form.List`. |
| `components/ValidationMessages.tsx` | Renders server findings (ERROR/WARNING tags, path + message); reused by 13.7. |
| `api/reports.ts` + `features/reports/useCreateReport.ts` | `createReport()` + mutation that invalidates the report list. |
| `features/reports/DpmsrBuilderPage.tsx` | The assembled form (header + reportingPerson + location + parties[] person/entity + goods[]) with dynamic add/remove, submit → normalize → Zod → POST → inline result panel (status + messages + "View report"). |
| `routes/AppRoutes.tsx` | `/reports/new` gated to `ANALYST`/`MLRO` (matches the create RBAC). |

## 3. Key understanding / decisions
- **Lookups are placeholder seeds, codes-only** — UAE has only `countries`/`currencies`/`transmode`/`funds`
  seeded, returned as bare codes (no labels). So only **country** and **currency** fields are dropdowns
  (`CodeSelect`); gender/role/ID-type/status/indicators are **free text/tags** — anything else would
  over-constrain beyond what the backend actually validates. (When authoritative UAE lookups land, more
  fields can become dropdowns with zero UI change beyond the `set` name.)
- **AntD nested `name` paths** — sub-forms take a `name` prefix array; at top level it's an absolute path
  (`['reportingPerson']`), inside a `Form.List` it starts with the list field index
  (`[field.name, 'person']`). Nested lists (identifications-in-person, directors-in-entity) compose the same
  way. Inputs get AntD-generated ids (`parties_0_person_firstName`) — used to target fields in tests.
- **Party = person XOR entity** — a UI-only `_type` radio per party picks the branch; `buildDpmsrPayload`
  keeps only that branch and drops `_type`; the Zod `.refine` enforces exactly-one (mirrors the engine).
- **Two-layer validation, server authoritative** — AntD `rules` give instant required-field UX; the Zod
  mirror gates the built payload; the **server `ValidationResult`** (XSD + business rules) is the real
  verdict, rendered inline via `ValidationMessages` after create. Client Zod is UX, not the gate (plan D6).
- **Create always persists** — the backend creates the report (status VALID/INVALID) and returns the
  messages; the panel offers "View report" (→ detail, 13.7) so an INVALID draft can be fixed/re-validated.
- **Sensible defaults** — submissionDate defaults to today; one party (person) + one goods item
  (currency AED) are pre-seeded so the form opens usable.

## 4. Tests (Vitest + RTL + MSW)
- `dpmsrSchema.test.ts` (5): minimal valid; missing reference; party neither/both person+entity; goods
  required + numeric estimatedValue.
- `dpmsrForm.test.ts` (4): Dayjs → ISO; party branch kept + `_type` dropped; empties pruned; zero value kept.
- `DpmsrBuilderPage.test.tsx` (3): fill required (ids) → submit → **captured POST body has the correct
  nested shape**; server validation messages render inline; "View report" → detail route.
- Suite now **40 passing** (11 files).

## 5. Verification
`npm run typecheck`, `npm run lint` (0 warnings), `npm test` (**40 passing**), `npm run build` (emits
`dist/`) — all green on Node 18.16. (Harmless jsdom `getComputedStyle` table noise persists in stderr.)
`git status` scoped to `frontend/`. (Bundle ~1.36 MB — code-splitting deferred; noted for Phase 14.)

---

## Outcome
✅ A working DPMSR builder: full nested form, lookup-driven dropdowns, Zod mirror, create → inline server
validation. Next: **13.7** — report detail + lifecycle (XML preview, validation panel, **MLRO submit** with
confirmation, FIU status timeline, attachments upload/list/remove).
