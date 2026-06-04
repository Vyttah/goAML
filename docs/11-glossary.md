# 11 — Glossary

> Every acronym and term used across these docs and the codebase. Keep this open in a tab.

---

## Business / AML / regulatory

| Term | Meaning |
|------|---------|
| **AML** | Anti-Money-Laundering — laws/processes to detect and prevent laundering illicit funds. |
| **CFT** | Combating the Financing of Terrorism — usually paired with AML. |
| **FIU** | Financial Intelligence Unit — the government agency that receives AML reports. In the UAE it's under the Central Bank. |
| **goAML** | The UNODC-built software platform many FIUs use to collect AML reports. Defines the report XML schema and the B2B submission API. |
| **UNODC** | United Nations Office on Drugs and Crime — builds and maintains goAML. |
| **RE** | Reporting Entity — a business legally required to file AML reports (bank, exchange house, gold dealer…). Each Vyttah client is an RE = a **tenant**. |
| **MLRO** | Money Laundering Reporting Officer — the person at an RE responsible for filing reports. Also an app role (can submit). |
| **RegTech** | Regulatory Technology — software that helps businesses meet regulatory obligations. Vyttah's category. |
| **KYC** | Know Your Customer — verifying customer identity/risk. |
| **CDD / ECDD** | Customer Due Diligence / Enhanced CDD — standard vs deeper customer review (ECDD for high-risk). |
| **PEP** | Politically Exposed Person — someone in/near public office, treated as higher AML risk. |
| **DPMS** | Dealers in Precious Metals & Stones — gold/diamond/jewellery dealers (a key UAE-regulated category). |
| **RBA** | Risk-Based Approach — assessing customer/transaction risk to decide scrutiny level. |
| **BRR** | Business Rejection Rules — the FIU's documented rules a report must satisfy or be rejected. (Open item; drives future validation incl. Emirates-ID/passport rules.) |
| **Emirates ID** | The UAE national identity card (ID type code `EID`). |
| **AED** | UAE Dirham — the UAE's currency. The DPMS cash reporting threshold is **AED 55,000**. |

## The 7 report types

| Code | Meaning | Shape |
|------|---------|-------|
| **STR** | Suspicious Transaction Report | transaction |
| **SAR** | Suspicious Activity Report | activity |
| **AIF** | Additional Information File | activity |
| **AIFT** | Additional Information File — Transaction | transaction |
| **ECDD** | Enhanced Customer Due Diligence | activity |
| **ECDDT** | Enhanced CDD — Transaction | transaction |
| **DPMSR** | Dealers in Precious Metals & Stones Report (UAE-specific) | activity |

## goAML XML / domain

| Term | Meaning |
|------|---------|
| **Schema 5.0.2** | The authoritative goAML XML schema version this platform targets (`goAMLSchema.xsd`, vendored). |
| **XSD** | XML Schema Definition — the formal schema file. The authoritative goAML 5.0.2 XSD is vendored at `src/main/resources/xsd/goaml/5.0.2/` and is the **source of truth** for the domain model. |
| **xjc** | The JAXB schema compiler. Generates the domain model from `goAMLSchema.xsd` at build time (via the `com.github.bjornvester.xjc` Gradle plugin) into `com.vyttah.goaml.domain.generated` — **not committed**. |
| **Generated model** | The xjc output (`domain.generated.*`): `Report`, `ActivityType`, `ReportPartyType`, `TTransItem`, enums (`ReportType`, `CurrencyType`, …), etc. Replaces the retired hand-modeled `domain/*`. |
| **`reportActivity` (choice slot)** | The `Report` property the `.xjb` binding renames branch-1's `activity` to, breaking the `<xs:choice>` catch-all. **`getReportActivity()`** is the activity accessor for *both* shapes; `getActivity()` is vestigial. |
| **JAXB** | Jakarta XML Binding — maps Java objects ↔ XML. Used by the generated `domain/` model + `engine/marshal`. |
| **XSD validation gate** | `engine/validation/XsdSchemaValidator` — validates marshalled report XML against `goAMLSchema.xsd` (standard JDK JAXP, XSD 1.0; the schema has no 1.1 asserts). Collects findings into a `ValidationResult`. |
| **lookup ⊆ XSD invariant** | Any lookup the validator checks must be a subset of its XSD enumeration (`transmode ⊆ conduction_type`, `currencies ⊆ currency_type`), else a rules-clean report can fail the schema. Guarded by `LookupXsdConsistencyTest`. |
| **`conduction_type`** | The XSD enumeration backing `transmode_code` (e.g. `ELCFT`, `SWIFT`, `CDM`). The `ae` `transmode` lookup is a subset of it. |
| **DPMSR builder** | `engine/build/DpmsrReportBuilder` + `DpmsrReportInput` (record **and** fluent) + `GoamlParties`/`GoamlWrappers` + `ValidatedReport` — a schema-driven, invoice-generic convenience for building/validating DPMSR reports. |
| **`report_code`** | The report type (STR…DPMSR). Drives which shape and rules apply. |
| **`submission_code`** | E (electronic) or M (manual). |
| **`rentity_id`** | FIU-assigned ID for a Reporting Entity. Per-tenant (in `tenant_goaml_config`). |
| **`entity_reference`** | The RE's own reference for a report. **Unique per tenant → used as idempotency key.** |
| **`fiu_ref_number`** | The FIU request being answered (required for AIF/AIFT/ECDD/ECDDT). |
| **Transaction shape** | Report body is a list of `Transaction` (money movements). STR/AIFT/ECDDT. |
| **Activity shape** | Report body is one `Activity` (report parties + goods/services). SAR/AIF/ECDD/DPMSR. |
| **bi-party** | A transaction with exactly one *from*-side and one *to*-side. |
| **multi-party** | A transaction with a list of `t_party` subjects (each with a role). |
| **`_my_client` variant** | A structurally-identical, stricter-typed version of a complex type (e.g. `t_person_my_client`) used when the party is the RE's own client. Strictness is enforced by the validator, not the POJO. |
| **`goods_services`** | A line item (e.g. a gold bar) in an activity report — central to DPMSR. |
| **`report_indicator`** | A flag (from FIU lookup lists) explaining why the report was filed (e.g. `DPMSR_CASH_THRESHOLD`). |
| **Lookup / lookup set** | An FIU-defined code list (countries, currencies, transmodes…) modeled as validated Strings, refreshable from `OdataLookups`. |

## Platform / tech

| Term | Meaning |
|------|---------|
| **Tenant** | One client RE. Gets its own Postgres schema `tenant_<uuid>`. |
| **Schema-per-tenant** | Multi-tenancy strategy: each tenant's data in a separate DB schema; Hibernate switches `search_path` per connection. |
| **`TenantContext`** | ThreadLocal holding the current request's tenant schema. |
| **JWT** | JSON Web Token — the signed auth token (HS256). Carries `sub`, `tenant`, `schema`, `roles`. |
| **RBAC** | Role-Based Access Control. Roles: SUPER_ADMIN, TENANT_ADMIN, MLRO, ANALYST. |
| **BCrypt** | Password-hashing algorithm used for `app_user.password_hash`. |
| **Flyway** | Database migration tool. Shared migrations at startup; tenant migrations at provisioning. |
| **JPA / Hibernate** | Java Persistence API / its implementation — the ORM. |
| **Lombok** | Annotation processor generating boilerplate (`@Getter`/`@Setter`/`@RequiredArgsConstructor`) — used on the JPA/web side only, not the generated domain or engine. |
| **MapStruct** | Compile-time entity↔DTO mapper (`componentModel = "spring"`); mappers live in `model/mapper/<feature>/`. |
| **Actuator** | Spring Boot's ops endpoints (`/actuator/health`, `/actuator/prometheus`). |
| **Testcontainers** | Library that spins real Docker containers (Postgres) for integration tests. |
| **XMLUnit** | Library for semantic XML comparison — used in golden-file tests. |
| **Golden file** | A committed expected-output XML; tests assert the engine still produces it. |
| **WireMock** | In-process HTTP mock server — stubs the goAML B2B API in tests (auth/submit/status). Added in Phase 6. |
| **LocalStack** | Local emulator of AWS services — in `docker-compose.yml`; the Phase 6 Secrets Manager integration test runs against it (`:4566`). |
| **Redis** | In-memory store (`docker-compose` `redis`); caches per-tenant goAML B2B session tokens (`goaml:b2b:token:<tenantId>`, TTL) via `TokenManager`. Phase 6. |
| **JaCoCo** | Java code-coverage tool; the build enforces a ≥90% instruction / ≥80% branch gate on the Phase 6 packages. |
| **`TokenManager`** | Authenticates to goAML B2B and caches the per-tenant `SqlAuthCookie` in Redis; re-auths on 401. |
| **`GoamlSecretsClient`** | Fetches a tenant's goAML B2B credentials from AWS Secrets Manager (named to avoid a clash with the AWS SDK's `SecretsManagerClient`). |
| **MCP** | Model Context Protocol — lets AI agents call the platform's tools (Phase 12). |
| **B2B** | The goAML machine-to-machine REST interface for submission. See [10](10-b2b-submission-protocol.md). |
| **`reportkey`** | The FIU's handle returned after a successful `PostReport`; used to poll status. |
| **OData** | The query protocol goAML uses for the status endpoint (`OdataReports`). |
| **`SqlAuthCookie`** | The session token cookie goAML's B2B auth returns. |

## AWS

| Term | Meaning |
|------|---------|
| **EKS** | Elastic Kubernetes Service — where the app runs (Phase 14). |
| **RDS** | Relational Database Service — managed PostgreSQL. |
| **S3** | Object storage — report attachments. |
| **Secrets Manager** | Stores per-tenant goAML credentials + JWT key, encrypted. |
| **KMS** | Key Management Service — encryption keys behind Secrets Manager. |
| **SES** | Simple Email Service — notification emails. |
| **ECR** | Elastic Container Registry — Docker image storage. |
| **IRSA** | IAM Roles for Service Accounts — lets pods access AWS without static keys. |
| **`me-central-1`** | AWS UAE (Middle East) region — for data residency. |

---

← Back to the [docs index](README.md).
