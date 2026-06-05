# 01 — Business Context

> Read this first. It explains the *why* before any code. No prior AML knowledge assumed.

---

## 1. What problem are we solving?

Money laundering is the process of making illegally-obtained money look legitimate. To fight it,
governments require certain businesses (banks, money exchanges, gold/jewellery dealers, real-estate
agents, etc.) to **report suspicious or high-value activity** to a government agency. Failing to
report carries heavy fines and criminal liability.

That government agency is called a **Financial Intelligence Unit (FIU)**. Every country has one. In
the **United Arab Emirates (UAE)** it sits under the Central Bank and receives reports through a
system called **goAML**.

**goAML** is a software platform built by the **United Nations Office on Drugs and Crime (UNODC)**.
Dozens of countries' FIUs run goAML to collect AML reports. It defines:
- a strict **XML format** (this platform targets the authoritative **5.0.2** XSD) that every report must
  conform to, and
- a **"goAML Web" B2B REST interface** for submitting those XML reports programmatically.

So when a business needs to file a report in the UAE, it must produce a goAML-schema-compliant XML document
and hand it to the UAE FIU's goAML system.

---

## 2. Who is Vyttah and what are we building?

**Vyttah** is a RegTech (regulatory-technology) company. Many businesses don't want to build their
own goAML XML generator and submission pipeline — it's fiddly, the rules change, and getting the XML
wrong means rejected reports and compliance risk. So Vyttah builds and operates a **platform that
does it for them**.

Vyttah acts **on behalf of many client businesses at once**. In AML/goAML terminology each such
client business is a **Reporting Entity (RE)**. Examples: a gold dealer in Dubai, a money-exchange
house, a bank branch. Each RE has its own identity with the FIU (an `rentity_id`) and its own goAML
login credentials.

This is why the platform is **multi-tenant**: one running application serves many REs, and each RE's
data must be strictly isolated from every other RE's data. (How we achieve that isolation is in
[06 — Multi-Tenancy & Security](06-multitenancy-and-security.md).)

> **Mental model:** Vyttah is like an accountant who files taxes for hundreds of separate companies.
> Each company's books are kept separate; Vyttah logs into each company's tax account with that
> company's own credentials; but it's one accounting firm with one set of tools.

---

## 3. What does the platform actually do? (the lifecycle)

For each report, the platform:

1. **Builds** a goAML-schema-compliant XML document from structured input data.
2. **Validates** it — first that it's structurally correct (schema), then that it satisfies the FIU's
   *business rules* (e.g. a suspicious-transaction report must have a reason; a UAE precious-metals
   report only matters above a cash threshold).
3. **Packages** the XML (plus any supporting attachments — PDFs, scans) into a ZIP.
4. **Submits** the ZIP to the FIU over goAML's B2B REST interface, using **that RE's own credentials**.
5. **Tracks** the result — submission is asynchronous, so the platform polls the FIU for the outcome
   (accepted / rejected / errors) and notifies the user.

Data can enter the platform three ways: through the **React web UI**, through a **generic inbound REST
API** (so other systems can push report data), and through **file import** (uploading an existing
goAML XML or a CSV).

---

## 4. The two "shapes" of a report

This is the single most important domain concept in the whole codebase. Every goAML report is one of
**two shapes**:

### Transaction-based reports
These describe one or more concrete **money movements** (transactions) — money flowing *from* a party
*to* a party. Each transaction has an amount, a date, a transmission mode (cash, wire, cheque...), and
the two sides.

### Activity-based reports
These describe **suspicious activity or an event** that isn't a simple transaction — e.g. a customer's
overall behaviour, or (crucially for the UAE) a **dealer in precious metals & stones** recording a cash
sale of gold. Activity reports carry **report parties** (the people/entities involved) and
**goods & services** (e.g. the gold bar: type, value, weight).

The code maps these to two builders and the validator routes by report type. Internally:
- Transaction shape → a list of `Transaction` objects.
- Activity shape → a single `Activity` object (report parties + goods/services).

---

## 5. The 7 UAE report types (all supported in v1)

| Code | Stands for | Shape | When it's used |
|------|------------|-------|----------------|
| **STR** | Suspicious Transaction Report | transaction | A specific transaction looks suspicious (e.g. structuring). |
| **SAR** | Suspicious Activity Report | activity | Suspicious behaviour not tied to one transaction. |
| **AIF** | Additional Information File | activity | Responding to an FIU request for more info (activity form). |
| **AIFT** | Additional Information File — Transaction | transaction | Responding to an FIU request, transaction form. |
| **ECDD** | Enhanced Customer Due Diligence | activity | Deeper review of a high-risk customer (e.g. a PEP). |
| **ECDDT** | Enhanced CDD — Transaction | transaction | ECDD tied to specific transactions. |
| **DPMSR** | **D**ealers in **P**recious **M**etals & **S**tones **R**eport | activity | **UAE-specific.** A gold/jewellery/diamond dealer reporting a qualifying (large, cash) sale. |

Notes:
- **AIF / AIFT / ECDD / ECDDT** are *responses to the FIU* and therefore require an **FIU reference
  number** (`fiu_ref_number`) — the ID of the request you're answering.
- **STR / SAR** require a free-text **reason** and **action** (why it's suspicious, what you did).
- **DPMSR** is the UAE's flagship use case for Vyttah's first customers (precious-metals & stones dealers).
  It is **generic across DPMS dealers and any qualifying invoice** — gold, diamonds, jewellery, any
  `item_type` — not gold-specific. It only needs to be filed when a cash transaction reaches the
  **AED 55,000** threshold. (PEP = Politically Exposed Person — someone in/near public office, treated as
  higher-risk.)

---

## 6. Key UAE-specific facts the system encodes

- **Currency:** AED (the report's `currency_code_local` must be AED for the UAE).
- **DPMS cash threshold:** **AED 55,000** — below it, a precious-metals cash sale generally doesn't
  need a DPMSR.
- **Attachment limits** (from the UAE submission guide): **5 MB per file**, **20 MB per report**.
- **Emirates ID** is the UAE national ID; passports are used for non-residents. *(Note: the code does
  not yet enforce Emirates-ID/passport format rules — see the gap note in [05 — The Engine](05-engine.md).)*

---

## 7. Why "jurisdiction abstraction"?

The UAE is the **first** target, not the only one. Other countries also run goAML, with different
currencies, report types, thresholds, and lookup code-lists. So the platform is built around a
**jurisdiction** concept: UAE-specific settings live in config (`jurisdictions/ae.yml` + `lookups/ae/`),
not hard-coded. Adding a new country later means adding a new config + lookup set, ideally with no code
change. Today only `ae` (UAE) ships.

---

## 8. Where the spec comes from

The design is derived from three vendor PDFs (UNODC + UAE):
1. **goAML XML Schema Guide v.5** — the XML format.
2. **goAML B2B Developers Guide** — the submission REST API.
3. **UAE DPMS Report Submission Guide** — UAE precious-metals specifics.

✅ **The authoritative XSD is in the repo.** The real goAML **5.0.2** schema (`goAMLSchema.xsd`) plus two
real DPMSR samples are vendored under `src/main/resources/xsd/` and `src/test/resources/samples/`. The
domain model is **generated from that XSD** (xjc) and reports are validated against it at runtime — the
PDFs are now just background. *(The UAE production schema version, 4.x vs 5.x, should still be confirmed
before go-live — see [09 — Build Order & Roadmap](09-build-order-and-roadmap.md).)*

---

**Next:** [02 — System Architecture](02-system-architecture.md) — how this is actually structured as software.
