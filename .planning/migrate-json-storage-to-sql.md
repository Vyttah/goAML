# Migrate JSON Storage to SQL in goAML — Proposal

**Task:** "migrate json storage to sql in GOAML"
**Status:** DRAFT — for senior dev review/verification. Not yet implemented.
**Author:** Claude (assistant), prepared for Kuldeepsingh / Rajat Gajera review.
**Date:** 2026-09-02

## 1. Problem statement

Currently the `report` table (per-tenant schema, e.g. `tenant_vyttah`) stores the entire DPMSR report
payload as a single JSONB column (`input`). This is convenient, but if the JSON shape changes in the
future (new fields, renamed fields, structural changes), there is no relational safety net — the whole
blob has to be re-parsed/migrated at once, and there is no way to query, index, or report on individual
fields (goods, parties, addresses, etc.) at the SQL level.

Goal: design a normalized relational schema that mirrors the DPMSR JSON structure, so that:
- Individual fields are queryable/indexable in SQL.
- Future JSON shape changes only require mapping-layer changes, not a full blob re-migration.
- XML generation (goAML B2B submission) can be reliably reconstructed from SQL rows instead of a JSON blob.

## 2. IMPORTANT — this reverses a previous, documented design decision

`src/main/resources/db/migration/tenant/V2__reports.sql` explicitly chose JSONB for `report.input` with
a comment stating that **"the XSD-generated model is the structure authority, so a relational mirror
would only drift."**

That is a real risk this proposal reintroduces: the JAXB/XSD-generated classes
(`build/generated/sources/xjc/java/com/vyttah/goaml/domain/generated/`, from
`src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd`) are the authoritative structure — not the SQL
schema. If the XSD changes (goAML publishes a new schema version) and this SQL mirror isn't updated in
lockstep, XML generation from SQL rows could silently drift out of sync with the schema.

**This needs explicit sign-off, not just a technical migration.** Recommend the team decide:
- Who owns keeping the SQL schema in sync when the XSD changes?
- Is `report.input` (JSONB) kept as a fallback/audit trail, or fully retired?

**DECIDED (2026-09-02, per requester): proceed.** Sign-off given to reverse the JSONB-only decision and
add the section tables in §6. `report.input` (JSONB) is kept — not retired — per the migration/backfill
strategy in §7 point 5. Still recommend the team separately nominate an owner for keeping the SQL
section tables in sync if/when the goAML XSD version changes (not yet assigned).

## 3. Current state (verified against the codebase)

### `report` table (`tenant` schema, e.g. `tenant_vyttah`)
Defined in `V2__reports.sql`, altered by `V7`, `V8`, `V9`, `V11`.

| column | type | nullable | notes |
|---|---|---|---|
| `id` | UUID | PK | |
| `entity_reference` | VARCHAR(255) | NOT NULL, UNIQUE | idempotency key |
| `report_code` | VARCHAR(16) | NOT NULL | currently always `DPMSR` |
| `rentity_id` | INTEGER | NOT NULL | |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'DRAFT' | CHECK: DRAFT, VALID, INVALID, PENDING_REVIEW, APPROVED, SUBMITTING, SUBMITTED, ACCEPTED, REJECTED, FAILED |
| **`input`** | **JSONB** | **NOT NULL** | full DPMSR request JSON (Jackson-serialized `DpmsrCreateRequest`/`DpmsrReportPayload`) — **this is what we're normalizing** |
| `report_xml` | TEXT | NULL | marshalled goAML XML snapshot |
| `validation_errors` | JSONB | NULL | `[{severity,path,code,message},...]` |
| `client_metadata` | JSONB | NULL | opaque, never reaches XML — **out of scope**, already its own column |
| `reviewed_by` / `reviewed_at` / `review_remark` | UUID / TIMESTAMPTZ / TEXT | NULL | |
| `created_by` | UUID | NULL | |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

No `report_goods` / `report_party` or other normalized tables exist today — `report.input` is the only
place DPMSR content lives.

### JSON → XML flow (must keep working)
1. `DpmsrRequestMapper` (`model/mapper/report/DpmsrRequestMapper.java`) maps `DpmsrCreateRequest` →
   `DpmsrReportInput` (hand-written record).
2. `DpmsrReportBuilder` (`engine/build/`) assembles a JAXB `Report` object from that.
3. `ReportMarshaller` (`engine/marshal/ReportMarshaller.java`) marshals the JAXB object to XML.
4. `DefaultReportService` (`service/report/DefaultReportService.java`) persists `input` as
   `objectMapper.writeValueAsString(...)` and `report_xml` as the marshalled string, via
   `ReportRepository` (plain Spring Data JPA over the `Report` entity,
   `model/entity/report/Report.java`).

Only `currencyCode` (`CurrencyType`) and party `role` (`EntityPersonRoleType`) are real Java enums in the
generated JAXB model. Every other "coded" field (`itemType`, `statusCode`, `addressType`, `countryCode`,
`incorporationLegalForm`, `tphContactType`, `tphCommunicationType`, etc.) is a plain `String`, validated
only by XSD pattern/enumeration — **not** a Java/DB enum. The proposed schema mirrors this (see §5).

## 4. Sample JSON being normalized

<details>
<summary>Full DPMSR JSON payload (click to expand)</summary>

```json
{
  "goods": [
    {
      "size": null,
      "address": null,
      "sizeUom": null,
      "comments": null,
      "itemMake": null,
      "itemType": "APA",
      "statusCode": "ACTVO",
      "description": "fgsdf",
      "currencyCode": "AED",
      "disposedValue": null,
      "estimatedValue": 65444311,
      "statusComments": null,
      "registrationDate": null,
      "registrationNumber": null,
      "identificationNumber": null,
      "presentlyRegisteredTo": null,
      "previouslyRegisteredTo": null
    }
  ],
  "action": "Filing the DPMSR as per MOE mandate",
  "reason": "Reporting to FIU per mandate payment of AED 65,444,311.00 as payment for the purchase of fgsdf",
  "parties": [
    {
      "role": null,
      "entity": {
        "url": null,
        "name": "VIMAL Trading LLC",
        "urls": null,
        "email": [],
        "emails": null,
        "phones": {
          "phone": [
            {
              "comments": null,
              "tphNumber": "1234567890",
              "tphExtension": null,
              "tphContactType": "BU",
              "tphCountryPrefix": "971",
              "tphCommunicationType": "L"
            }
          ]
        },
        "business": "ACCESSORIES",
        "comments": null,
        "addresses": {
          "address": [
            {
              "zip": null,
              "city": "Dubai",
              "town": null,
              "state": "dubai",
              "address": "aedzxfv",
              "comments": null,
              "addressType": "BU",
              "countryCode": "AE",
              "geoLocation": null,
              "houseNumber": null,
              "apartmentNumber": null,
              "additionalAddressLine1": null,
              "additionalAddressLine2": null
            }
          ]
        },
        "sanctions": null,
        "taxNumber": null,
        "directorId": [],
        "entityStatus": null,
        "taxRegNumber": null,
        "businessClosed": null,
        "commercialName": null,
        "networkDevices": null,
        "reRelationship": null,
        "relatedPersons": null,
        "relatedEntities": null,
        "entityStatusDate": null,
        "incorporationDate": "2015-03-10T00:00:00Z",
        "dateBusinessClosed": null,
        "incorporationState": "United Arab Emirates",
        "incorporationNumber": "DED-2024-73799787",
        "additionalInformation": null,
        "entityIdentifications": null,
        "incorporationLegalForm": "AAFSI",
        "incorporationCountryCode": "AE"
      },
      "person": null,
      "reason": "roleadnfkvsl",
      "account": null,
      "country": null,
      "comments": "snvkdlfv",
      "isSuspected": null,
      "significance": null,
      "entityMyClient": null,
      "personMyClient": null,
      "accountMyClient": null
    },
    {
      "role": null,
      "entity": {
        "url": null,
        "name": "XYZ Holdings LLC",
        "urls": null,
        "email": [],
        "emails": null,
        "phones": null,
        "business": null,
        "comments": null,
        "addresses": null,
        "sanctions": null,
        "taxNumber": null,
        "directorId": [],
        "entityStatus": null,
        "taxRegNumber": null,
        "businessClosed": null,
        "commercialName": null,
        "networkDevices": null,
        "reRelationship": null,
        "relatedPersons": null,
        "relatedEntities": null,
        "entityStatusDate": null,
        "incorporationDate": null,
        "dateBusinessClosed": null,
        "incorporationState": null,
        "incorporationNumber": null,
        "additionalInformation": null,
        "entityIdentifications": null,
        "incorporationLegalForm": null,
        "incorporationCountryCode": "AE"
      },
      "person": null,
      "reason": "SHAREHOLDER",
      "account": null,
      "country": null,
      "comments": null,
      "isSuspected": null,
      "significance": null,
      "entityMyClient": null,
      "personMyClient": null,
      "accountMyClient": null
    }
  ],
  "location": {
    "zip": null,
    "city": "dubai",
    "town": null,
    "state": "dubai",
    "address": "Dubai",
    "comments": null,
    "addressType": "BU",
    "countryCode": "AE",
    "geoLocation": null,
    "houseNumber": null,
    "apartmentNumber": null,
    "additionalAddressLine1": null,
    "additionalAddressLine2": null
  },
  "indicators": ["DPMSJ"],
  "fiuRefNumber": null,
  "rentityBranch": null,
  "clientMetadata": {
    "rate": "1",
    "executedBy": "vyttah",
    "indemnified": "Yes",
    "licenseType": "Business License",
    "jurisdiction": "FREE ZONE",
    "amlCustomerId": "706",
    "amlCustomerKind": "LEGAL",
    "countriesOfOperation": "United Arab Emirates",
    "countriesOfSourceOfFunds": "United Arab Emirates"
  },
  "submissionDate": "2026-08-24T00:00:00Z",
  "entityReference": "TXN-1787549777628",
  "reportingPerson": {
    "ssn": null,
    "alias": null,
    "email": [],
    "title": null,
    "gender": "M",
    "phones": null,
    "prefix": null,
    "comments": null,
    "deceased": null,
    "idNumber": "123",
    "lastName": "Prajapati",
    "addresses": null,
    "birthdate": null,
    "firstName": "Kuldeepsingh",
    "residence": null,
    "taxNumber": null,
    "birthPlace": null,
    "middleName": null,
    "occupation": "dfg",
    "mothersName": null,
    "dateDeceased": null,
    "employerName": null,
    "nationality1": "IN",
    "nationality2": null,
    "nationality3": null,
    "taxRegNumber": null,
    "identification": [],
    "passportNumber": null,
    "sourceOfWealth": null,
    "employerPhoneId": null,
    "passportCountry": null,
    "employerAddressId": null
  }
}
```
</details>

## 5. Proposed schema

Design principles:
- **DPMSR-specific tables are kept separate from `report`**, because `report` is meant to stay generic
  across all 17 future goAML report codes (per `.planning/PROJECT.md`). A `report_dpmsr_detail` table
  holds the DPMSR-only scalars (activity-shaped: goods + parties, no `<transaction>` block).
- **`address` and `phone` are shared, reusable tables** — the JSON reuses the identical `TAddress`/
  `TPhone` shape in `location`, `goods[].address`, entity/person addresses, and employer
  address/phone. One physical shape, referenced by FK, avoids repeating ~20 columns four times.
- **Party is polymorphic** (`entity` / `person` / `account`, and their `*MyClient` variants per the XSD
  choice) — `report_party` is the parent row with a `party_type` discriminator, and exactly one of
  `party_entity` / `party_person` / `party_account` hangs off it 1:1.
- **Arrays get an `ordinal` column** (`goods`, `parties`, `indicators`, `email[]`, `directorId[]`) so
  array order is reconstructible for XML regeneration — goAML XML is order-sensitive in places.
- **Coded fields stay `VARCHAR`, not SQL/Java enums** — mirrors the JAXB model, where only
  `currencyCode` and `role` are real enums; everything else is XSD-pattern-validated only. A CHECK-
  constrained enum here would fight the XSD as source of truth.
- **Keep `report.input` (JSONB) during migration** as a fallback/audit trail rather than dropping it
  immediately — retire it only once the new tables are proven to round-trip correctly through
  `ReportMarshaller`.

```sql
-- =========================================================
-- Shared value tables (reused across goods/entity/person/employer/location)
-- =========================================================

CREATE TABLE address (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address_type                VARCHAR(10),
    address                     VARCHAR(255),
    house_number                VARCHAR(50),
    apartment_number             VARCHAR(50),
    additional_address_line1    VARCHAR(255),
    additional_address_line2    VARCHAR(255),
    town                        VARCHAR(100),
    city                        VARCHAR(100),
    zip                         VARCHAR(20),
    country_code                CHAR(2),
    state                       VARCHAR(100),
    geo_latitude                NUMERIC(10,6),
    geo_longitude               NUMERIC(10,6),
    geo_plus_code               VARCHAR(50),
    geo_is_approx_location      BOOLEAN,
    geo_error_distance_margin   NUMERIC(10,2),
    geo_margin_uom              VARCHAR(20),
    comments                    TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE phone (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tph_contact_type       VARCHAR(10),
    tph_communication_type VARCHAR(10),
    tph_country_prefix     VARCHAR(10),
    tph_number             VARCHAR(30),
    tph_extension          VARCHAR(10),
    comments               TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================================
-- DPMSR report-level detail (1:1 with report; keeps `report` generic)
-- =========================================================

CREATE TABLE report_dpmsr_detail (
    report_id          UUID PRIMARY KEY REFERENCES report(id) ON DELETE CASCADE,
    action              TEXT,
    reason              TEXT,
    fiu_ref_number      VARCHAR(100),
    rentity_branch      VARCHAR(100),
    submission_date     TIMESTAMPTZ,
    location_address_id UUID REFERENCES address(id)
);

CREATE TABLE report_indicator (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id      UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    indicator_code VARCHAR(20) NOT NULL,
    ordinal        INT NOT NULL
);
CREATE INDEX idx_report_indicator_report ON report_indicator(report_id);

-- =========================================================
-- Goods (report activity items)
-- =========================================================

CREATE TABLE report_goods (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id                 UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    ordinal                   INT NOT NULL,
    item_type                 VARCHAR(10),
    item_make                 VARCHAR(255),
    description                TEXT,
    previously_registered_to  VARCHAR(255),
    presently_registered_to   VARCHAR(255),
    estimated_value           NUMERIC(18,2),
    status_code                VARCHAR(10),
    status_comments            TEXT,
    disposed_value             NUMERIC(18,2),
    currency_code               CHAR(3),
    size                       NUMERIC(18,4),
    size_uom                   VARCHAR(20),
    registration_date           TIMESTAMPTZ,
    registration_number         VARCHAR(100),
    identification_number       VARCHAR(100),
    comments                    TEXT,
    address_id                 UUID REFERENCES address(id)
);
CREATE INDEX idx_report_goods_report ON report_goods(report_id);

-- =========================================================
-- Parties (polymorphic: entity / person / account, incl. *MyClient variants)
-- =========================================================

CREATE TABLE report_party (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id        UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    ordinal          INT NOT NULL,
    party_type       VARCHAR(20) NOT NULL
        CHECK (party_type IN ('ENTITY','ENTITY_MY_CLIENT','PERSON','PERSON_MY_CLIENT',
                               'ACCOUNT','ACCOUNT_MY_CLIENT')),
    role             VARCHAR(20),
    reason           TEXT NOT NULL,   -- required per XSD
    country          CHAR(2),
    comments         TEXT,
    is_suspected     BOOLEAN,
    significance     VARCHAR(50)
);
CREATE INDEX idx_report_party_report ON report_party(report_id);

-- ---- entity detail (1:1 with report_party) ----
CREATE TABLE party_entity (
    party_id                   UUID PRIMARY KEY REFERENCES report_party(id) ON DELETE CASCADE,
    name                       VARCHAR(255),
    commercial_name            VARCHAR(255),
    incorporation_legal_form   VARCHAR(20),
    incorporation_number        VARCHAR(100),
    business                   VARCHAR(255),
    entity_status               VARCHAR(50),
    entity_status_date           TIMESTAMPTZ,
    incorporation_state          VARCHAR(100),
    incorporation_country_code    CHAR(2),
    incorporation_date            TIMESTAMPTZ,
    business_closed              BOOLEAN,
    date_business_closed          TIMESTAMPTZ,
    tax_number                   VARCHAR(50),
    tax_reg_number                VARCHAR(50),
    re_relationship               VARCHAR(255),
    sanctions                    TEXT,
    additional_information        TEXT,
    comments                     TEXT
);

CREATE TABLE party_entity_address (
    entity_id  UUID NOT NULL REFERENCES party_entity(party_id) ON DELETE CASCADE,
    address_id UUID NOT NULL REFERENCES address(id) ON DELETE CASCADE,
    ordinal    INT NOT NULL,
    PRIMARY KEY (entity_id, address_id)
);
CREATE TABLE party_entity_phone (
    entity_id UUID NOT NULL REFERENCES party_entity(party_id) ON DELETE CASCADE,
    phone_id  UUID NOT NULL REFERENCES phone(id) ON DELETE CASCADE,
    ordinal   INT NOT NULL,
    PRIMARY KEY (entity_id, phone_id)
);
CREATE TABLE party_entity_email (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id UUID NOT NULL REFERENCES party_entity(party_id) ON DELETE CASCADE,
    email     VARCHAR(255) NOT NULL,
    ordinal   INT NOT NULL
);
CREATE TABLE party_entity_url (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id UUID NOT NULL REFERENCES party_entity(party_id) ON DELETE CASCADE,
    url       VARCHAR(500) NOT NULL,
    ordinal   INT NOT NULL
);
CREATE TABLE party_entity_director (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id   UUID NOT NULL REFERENCES party_entity(party_id) ON DELETE CASCADE,
    director_id VARCHAR(100) NOT NULL,
    ordinal     INT NOT NULL
);

-- ---- person detail (1:1 with report_party) ----
CREATE TABLE party_person (
    party_id            UUID PRIMARY KEY REFERENCES report_party(id) ON DELETE CASCADE,
    gender               VARCHAR(5),
    title                VARCHAR(20),
    prefix               VARCHAR(20),
    first_name            VARCHAR(100),
    middle_name           VARCHAR(100),
    last_name             VARCHAR(100),
    birthdate            TIMESTAMPTZ,
    birth_place           VARCHAR(255),
    mothers_name          VARCHAR(255),
    alias                VARCHAR(255),
    ssn                  VARCHAR(50),
    passport_number       VARCHAR(50),
    passport_country      CHAR(2),
    id_number            VARCHAR(100),
    nationality1          CHAR(2),
    nationality2          CHAR(2),
    nationality3          CHAR(2),
    residence            CHAR(2),
    occupation           VARCHAR(255),
    employer_name         VARCHAR(255),
    employer_address_id   UUID REFERENCES address(id),
    employer_phone_id     UUID REFERENCES phone(id),
    deceased             BOOLEAN,
    date_deceased         TIMESTAMPTZ,
    tax_number            VARCHAR(50),
    tax_reg_number         VARCHAR(50),
    source_of_wealth       VARCHAR(255),
    comments             TEXT
);

CREATE TABLE party_person_address (
    person_id  UUID NOT NULL REFERENCES party_person(party_id) ON DELETE CASCADE,
    address_id UUID NOT NULL REFERENCES address(id) ON DELETE CASCADE,
    ordinal    INT NOT NULL,
    PRIMARY KEY (person_id, address_id)
);
CREATE TABLE party_person_phone (
    person_id UUID NOT NULL REFERENCES party_person(party_id) ON DELETE CASCADE,
    phone_id  UUID NOT NULL REFERENCES phone(id) ON DELETE CASCADE,
    ordinal   INT NOT NULL,
    PRIMARY KEY (person_id, phone_id)
);
CREATE TABLE party_person_email (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES party_person(party_id) ON DELETE CASCADE,
    email     VARCHAR(255) NOT NULL,
    ordinal   INT NOT NULL
);
CREATE TABLE party_person_identification (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES party_person(party_id) ON DELETE CASCADE,
    id_type   VARCHAR(50),
    id_value  VARCHAR(100),
    ordinal   INT NOT NULL
);

-- ---- account detail (1:1 with report_party) — STUB, no `account` sample seen yet ----
CREATE TABLE party_account (
    party_id         UUID PRIMARY KEY REFERENCES report_party(id) ON DELETE CASCADE,
    institution_name VARCHAR(255),
    branch           VARCHAR(255),
    account_number   VARCHAR(100),
    -- extend per TAccount/TAccountMyClient once a real sample/XSD walk-through is available
    comments         TEXT
);

-- =========================================================
-- Reporting person (1:1 with report — same shape as party_person)
-- =========================================================

CREATE TABLE report_reporting_person (
    report_id            UUID PRIMARY KEY REFERENCES report(id) ON DELETE CASCADE,
    gender               VARCHAR(5),
    title                VARCHAR(20),
    prefix               VARCHAR(20),
    first_name            VARCHAR(100),
    middle_name           VARCHAR(100),
    last_name             VARCHAR(100),
    birthdate            TIMESTAMPTZ,
    birth_place           VARCHAR(255),
    mothers_name          VARCHAR(255),
    alias                VARCHAR(255),
    ssn                  VARCHAR(50),
    passport_number       VARCHAR(50),
    passport_country      CHAR(2),
    id_number            VARCHAR(100),
    nationality1          CHAR(2),
    nationality2          CHAR(2),
    nationality3          CHAR(2),
    residence            CHAR(2),
    occupation           VARCHAR(255),
    employer_name         VARCHAR(255),
    employer_address_id   UUID REFERENCES address(id),
    employer_phone_id     UUID REFERENCES phone(id),
    deceased             BOOLEAN,
    date_deceased         TIMESTAMPTZ,
    tax_number            VARCHAR(50),
    tax_reg_number         VARCHAR(50),
    source_of_wealth       VARCHAR(255),
    comments             TEXT
);

CREATE TABLE report_reporting_person_identification (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES report_reporting_person(report_id) ON DELETE CASCADE,
    id_type   VARCHAR(50),
    id_value  VARCHAR(100),
    ordinal   INT NOT NULL
);
```

## 6. Approved direction (per senior dev feedback, 2026-09-02): 5 section tables

The senior dev reviewed and directed a specific 5-table structure instead of full normalization (§5) or
the generic section-table sketch (§6 variants below). **`report.input` (JSONB) continues to be written
exactly as today** — this design only adds tables alongside it; XML generation is untouched.

The 5 tables:

1. **`goods_and_services`** — one row per `goods[]` item.
2. **`report_details`** — the report header/scalar section (action, reason, location, indicators,
   fiuRefNumber, rentityBranch, submissionDate, reportingPerson).
3. **`involved_party_natural`** — one row per `parties[]` entry where the party is a **natural person**
   (`person`/`personMyClient`).
4. **`involved_party_legal`** — one row per `parties[]` entry where the party is a **legal entity**
   (`entity`/`entityMyClient`).
5. **`additional_details`** — optional/overflow fields that don't need their own column: repeatable
   sub-lists (extra phones/addresses beyond the primary one, emails, director IDs, sanctions, related
   persons/entities, network devices, entity identifications) and report-level `clientMetadata`. Stored
   as JSONB, tagged by category and linked back to whichever row it belongs to.

```sql
-- =========================================================
-- 2. report_details — header/scalar section (1:1 with report)
-- =========================================================
CREATE TABLE report_details (
    report_id                      UUID PRIMARY KEY REFERENCES report(id) ON DELETE CASCADE,
    action                          VARCHAR(8000),
    reason                          VARCHAR(8000),
    fiu_ref_number                  VARCHAR(255),
    rentity_branch                  VARCHAR(255),
    submission_date                 TIMESTAMPTZ,               -- XSD sql_date = xs:dateTime; TIMESTAMPTZ per §6b/point 8
    indicators                      VARCHAR(5)[],               -- report_indicator_type: fixed 5-char code list
    -- location (single t_address)
    location_address                VARCHAR(100),             -- required per XSD when submitted, but NOT NULL here would break storing in-progress DRAFTs — see §6c
    location_house_number            VARCHAR(25),
    location_apartment_number        VARCHAR(25),
    location_additional_line1        VARCHAR(100),
    location_additional_line2        VARCHAR(100),
    location_town                    VARCHAR(255),
    location_city                    VARCHAR(255),
    location_state                   VARCHAR(255),
    location_zip                     VARCHAR(10),
    location_country_code             VARCHAR(2)                -- country_type includes a 1-char "-" sentinel; not fixed CHAR(2)
        CHECK (location_country_code = '-' OR length(location_country_code) = 2),
    location_address_type             VARCHAR(5)                -- contact_type: fixed enum, CONFIRMED full list (8 values)
        CHECK (location_address_type IS NULL OR location_address_type IN ('-','BU','OFFIC','OPRTL','PRIVT','PRSNL','REG','RES')),
    location_comments                 VARCHAR(8000),
    -- reportingPerson (single t_person_registration_in_report — required object)
    reporting_person_first_name        VARCHAR(100),          -- required per XSD when submitted; NOT NULL removed, see §6c
    reporting_person_middle_name       VARCHAR(100),
    reporting_person_last_name          VARCHAR(100),          -- same as above
    reporting_person_gender             CHAR(1)                  -- gender_type: enum '-','F','M' only
        CHECK (reporting_person_gender IN ('-','F','M')),
    reporting_person_title              VARCHAR(30),
    reporting_person_prefix             VARCHAR(10),             -- t_person_registration_in_report caps prefix at 10 (t_person allows 100 — see §6a note)
    reporting_person_birthdate           TIMESTAMPTZ,             -- sql_date = dateTime
    reporting_person_birth_place         VARCHAR(255),
    reporting_person_mothers_name        VARCHAR(100),
    reporting_person_alias               VARCHAR(100),
    reporting_person_ssn                 VARCHAR(25),
    reporting_person_id_number           VARCHAR(255),
    reporting_person_passport_number      VARCHAR(255),
    reporting_person_passport_country     VARCHAR(2)
        CHECK (reporting_person_passport_country = '-' OR length(reporting_person_passport_country) = 2),
    reporting_person_nationality1         VARCHAR(2)
        CHECK (reporting_person_nationality1 = '-' OR length(reporting_person_nationality1) = 2),
    reporting_person_nationality2         VARCHAR(2)
        CHECK (reporting_person_nationality2 = '-' OR length(reporting_person_nationality2) = 2),
    reporting_person_nationality3         VARCHAR(2)
        CHECK (reporting_person_nationality3 = '-' OR length(reporting_person_nationality3) = 2),
    reporting_person_residence            VARCHAR(2)
        CHECK (reporting_person_residence = '-' OR length(reporting_person_residence) = 2),
    reporting_person_occupation           VARCHAR(255),
    reporting_person_employer_name        VARCHAR(255),
    reporting_person_tax_number           VARCHAR(100),
    reporting_person_tax_reg_number        VARCHAR(100),
    reporting_person_source_of_wealth      VARCHAR(255),         -- RESOLVED: t_person_registration_in_report DOES define source_of_wealth (maxLength 255) — the JAXB model has the field, but DpmsrCreateRequest.Person/DpmsrRequestMapper.reportingPerson() never populate it today (app-layer gap, not a schema issue) — see §6a Q2
    reporting_person_deceased             BOOLEAN,               -- XSD: fixed="1" — only ever present-as-true in valid XML, never written as false (see §6a, item 13)
    reporting_person_date_deceased        TIMESTAMPTZ,
    reporting_person_comments             VARCHAR(8000),
    CHECK ((reporting_person_passport_number IS NULL) = (reporting_person_passport_country IS NULL))
);

-- =========================================================
-- 1. goods_and_services — one row per goods[] item
-- =========================================================
CREATE TABLE goods_and_services (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id                 UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    ordinal                   INT NOT NULL,
    item_type                 VARCHAR(5),               -- trans_item_type: fixed enum, CONFIRMED 63 values — see §6a Q4 (not inlined as CHECK; recommend a lookup table, open decision point)
    item_make                 VARCHAR(255),
    description                VARCHAR(8000),
    previously_registered_to  VARCHAR(500),
    presently_registered_to   VARCHAR(500),
    estimated_value           NUMERIC(19,4),            -- xs:decimal, fractionDigits=4, |value| <= 7.9e14
    status_code                VARCHAR(5)                -- trans_item_status: fixed enum, CONFIRMED full list (20 values)
        CHECK (status_code IS NULL OR status_code IN
            ('-','ACTUC','ACTVO','CNL','CONT','DONTD','DSTRY','EXCHD','FRZN','INHTD','LET','LSD',
             'MORTG','PNDNG','PRCHS','SOLD','TOM','UAPPL','UNCLM','WTH')),
    status_comments            VARCHAR(500),             -- capped at 500, NOT the 8000-char comments_type
    disposed_value             NUMERIC(19,4),
    currency_code               CHAR(3),                 -- currency_type: fixed ISO-4217 enum, always 3 chars (OK as CHAR(3))
    size                       NUMERIC(15,0),            -- xs:decimal, fractionDigits=0, integer-valued
    size_uom                   VARCHAR(250),
    registration_date           TIMESTAMPTZ,             -- sql_date = dateTime
    registration_number         VARCHAR(500),
    identification_number       VARCHAR(255),
    comments                    VARCHAR(8000),
    -- goods[].address (single t_address)
    address_line                VARCHAR(100),
    address_city                 VARCHAR(255),
    address_state                 VARCHAR(255),
    address_country_code          VARCHAR(2)
        CHECK (address_country_code IS NULL OR address_country_code = '-' OR length(address_country_code) = 2),
    address_zip                   VARCHAR(10),
    address_type                  VARCHAR(5)               -- contact_type: fixed enum, CONFIRMED full list (8 values)
        CHECK (address_type IS NULL OR address_type IN ('-','BU','OFFIC','OPRTL','PRIVT','PRSNL','REG','RES'))
);
CREATE INDEX idx_goods_and_services_report ON goods_and_services(report_id);

-- =========================================================
-- 3. involved_party_natural — parties[] where person/personMyClient present
-- =========================================================
CREATE TABLE involved_party_natural (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id             UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    party_ordinal         INT NOT NULL,
    is_my_client          BOOLEAN NOT NULL,   -- true when source was personMyClient
    role                  VARCHAR(500),        -- report_party_role_type: XSD imposes NO enumeration/length at all — do not reuse the transaction-report party_role_type enum here
    reason                VARCHAR(8000),        -- required per XSD when submitted; NOT NULL removed, see §6c
    country               VARCHAR(2)
        CHECK (country IS NULL OR country = '-' OR length(country) = 2),
    comments              VARCHAR(8000),
    is_suspected          BOOLEAN,
    significance          SMALLINT CHECK (significance IS NULL OR significance BETWEEN 0 AND 10),  -- xs:int 0..10, not free text
    -- t_person core fields (party-level type — NOT the same maxLengths as reportingPerson's registration type; see §6a)
    first_name             VARCHAR(100),        -- required per XSD when submitted; NOT NULL removed, see §6c
    middle_name            VARCHAR(100),
    last_name               VARCHAR(100),        -- same as above
    gender                 CHAR(1) CHECK (gender IN ('-','F','M')),
    title                  VARCHAR(30),
    prefix                 VARCHAR(100),        -- t_person allows 100 (vs. 10 on t_person_registration_in_report)
    birthdate              TIMESTAMPTZ,
    birth_place             VARCHAR(255),
    mothers_name             VARCHAR(100),
    alias                   VARCHAR(100),
    ssn                     VARCHAR(25),
    id_number               VARCHAR(255),
    passport_number          VARCHAR(255),
    passport_country          VARCHAR(2)
        CHECK (passport_country IS NULL OR passport_country = '-' OR length(passport_country) = 2),
    nationality1              VARCHAR(2)
        CHECK (nationality1 IS NULL OR nationality1 = '-' OR length(nationality1) = 2),
    nationality2              VARCHAR(2)
        CHECK (nationality2 IS NULL OR nationality2 = '-' OR length(nationality2) = 2),
    nationality3              VARCHAR(2)
        CHECK (nationality3 IS NULL OR nationality3 = '-' OR length(nationality3) = 2),
    residence                VARCHAR(2)
        CHECK (residence IS NULL OR residence = '-' OR length(residence) = 2),
    occupation               VARCHAR(255),
    employer_name             VARCHAR(255),
    deceased                 BOOLEAN,          -- fixed="1" — present-as-true only, see §6a item 13
    date_deceased             TIMESTAMPTZ,
    tax_number                VARCHAR(100),
    tax_reg_number              VARCHAR(100),   -- t_person: 100 (contrast t_entity: 1 — see §6a item 9)
    source_of_wealth            VARCHAR(255),
    -- primary (first) phone/address, flattened for quick queries; the rest -> additional_details
    primary_phone_number         VARCHAR(50),
    primary_address_line          VARCHAR(100),
    primary_address_city           VARCHAR(255),
    primary_address_country_code    VARCHAR(2)
        CHECK (primary_address_country_code IS NULL OR primary_address_country_code = '-' OR length(primary_address_country_code) = 2),
    CHECK ((passport_number IS NULL) = (passport_country IS NULL))
);
CREATE INDEX idx_involved_party_natural_report ON involved_party_natural(report_id);

-- =========================================================
-- 4. involved_party_legal — parties[] where entity/entityMyClient present
-- =========================================================
CREATE TABLE involved_party_legal (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id                 UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    party_ordinal              INT NOT NULL,
    is_my_client               BOOLEAN NOT NULL,  -- true when source was entityMyClient
    role                      VARCHAR(500),        -- report_party_role_type: no enum/length in XSD
    reason                    VARCHAR(8000),        -- required per XSD when submitted; NOT NULL removed, see §6c
    country                   VARCHAR(2)
        CHECK (country IS NULL OR country = '-' OR length(country) = 2),
    comments                  VARCHAR(8000),
    is_suspected               BOOLEAN,
    significance                SMALLINT CHECK (significance IS NULL OR significance BETWEEN 0 AND 10),
    -- t_entity core fields
    name                       VARCHAR(255),        -- required per XSD when submitted; NOT NULL removed, see §6c (also has the ASCII pattern restriction, §6a item 15 — not enforced here either)
    commercial_name             VARCHAR(255),
    incorporation_legal_form     VARCHAR(5),             -- legal_form_type: fixed enum, CONFIRMED 97 values — see §6a Q4 (not inlined as CHECK; recommend a lookup table, open decision point)
    incorporation_number          VARCHAR(50),          -- required per XSD when submitted; NOT NULL removed, see §6c
    business                     VARCHAR(255),
    entity_status                 VARCHAR(255),          -- entity_status_type CONFIRMED: unrestricted xs:string, no enum, no maxLength in XSD — resolved, no CHECK needed
    entity_status_date              TIMESTAMPTZ,
    incorporation_state              VARCHAR(255),
    incorporation_country_code        VARCHAR(2)
        CHECK (incorporation_country_code IS NULL OR incorporation_country_code = '-' OR length(incorporation_country_code) = 2),
    incorporation_date                 TIMESTAMPTZ,
    business_closed                   BOOLEAN,          -- fixed="1" — present-as-true only, see §6a item 13
    date_business_closed                TIMESTAMPTZ,
    tax_number                          VARCHAR(100),
    tax_reg_number                        VARCHAR(1),    -- t_entity: CONFIRMED, distinct element from tax_number, real minLength=1/maxLength=1 in XSD (likely a Y/N-style flag, not a truncated ID) — resolved, see §6a Q1
    -- primary (first) phone/address, flattened for quick queries; the rest -> additional_details
    primary_phone_number                   VARCHAR(50),
    primary_address_line                    VARCHAR(100),
    primary_address_city                     VARCHAR(255),
    primary_address_country_code              VARCHAR(2)
        CHECK (primary_address_country_code IS NULL OR primary_address_country_code = '-' OR length(primary_address_country_code) = 2)
);
CREATE INDEX idx_involved_party_legal_report ON involved_party_legal(report_id);

-- =========================================================
-- 5. additional_details — optional/overflow fields, JSONB, tagged by category
-- =========================================================
CREATE TABLE additional_details (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id              UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    goods_id                UUID REFERENCES goods_and_services(id) ON DELETE CASCADE,
    natural_party_id         UUID REFERENCES involved_party_natural(id) ON DELETE CASCADE,
    legal_party_id            UUID REFERENCES involved_party_legal(id) ON DELETE CASCADE,
    category                 VARCHAR(50) NOT NULL,
        -- e.g. PHONES, ADDRESSES, EMAILS, DIRECTOR_IDS, SANCTIONS, RELATED_ENTITIES,
        --      RELATED_PERSONS, NETWORK_DEVICES, ENTITY_IDENTIFICATIONS, EMPLOYER_ADDRESS,
        --      EMPLOYER_PHONE, IDENTIFICATION_LIST, CLIENT_METADATA
    data                     JSONB NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (goods_id IS NOT NULL)::INT + (natural_party_id IS NOT NULL)::INT
        + (legal_party_id IS NOT NULL)::INT <= 1     -- at most one owner besides report-level rows
    )
);
CREATE INDEX idx_additional_details_report ON additional_details(report_id);
CREATE INDEX idx_additional_details_category ON additional_details(category);
```

**How this covers the sample JSON:**
- `report_details` ← `action`, `reason`, `fiuRefNumber`, `rentityBranch`, `submissionDate`, `indicators`,
  `location`, `reportingPerson` (core fields).
- `goods_and_services` ← each `goods[]` entry, including the flattened `address` sub-object.
- `involved_party_legal` ← both `parties[]` entries in the sample (VIMAL Trading LLC, XYZ Holdings LLC —
  both have `entity`, not `person`), with `phones`/`addresses`/`email`/`directorId` for VIMAL Trading LLC
  going to `additional_details` (category `PHONES`, `ADDRESSES`) since it has more than one addressable
  sub-item.
- `involved_party_natural` ← unused by this particular sample (no `person`-type party present), but
  ready for reports that have one.
- `additional_details` ← `clientMetadata` (report-level, all `*_id` columns null), plus the entity phone
  and address rows for the first party.

**Note on `is_my_client`:** the XSD offers `person`/`personMyClient` and `entity`/`entityMyClient` as
separate choice branches (a party that *is* the reporting entity's own client vs. a third party). Both
land in the same table with a boolean flag rather than 4 separate tables, to stay within the 5-table
budget the senior specified — flag this specifically for confirmation since it wasn't explicit in the
verbal direction.

## 6a. XSD type verification (2026-09-02) — required before implementation

Per instruction: every column above was checked field-by-field against the raw XSD restrictions in
`src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd` (not the generated JAXB Java types, which can
widen/erase constraints) — base type, `maxLength`, `pattern`, `enumeration`, `minInclusive`/
`maxInclusive`, and required/optional. **The DDL in §6 above already reflects these corrections.** This
section is the audit trail showing what was wrong in the first draft and why, so the reviewer doesn't
have to re-derive it.

**Structural note:** there is no single `DPMSR` complexType in the XSD. DPMSR is a `report_code="DPMSR"`
value of the generic `report` element, using the `activity` choice branch (`activityType`, line ~2670) —
`report_parties/report_party` (`report_party_type`, line ~2725) and `goods_services/item`
(`t_trans_item`, line ~2750). This is a **different, parallel type** from the transaction-shaped `t_party`
/`party_role_type` used by STR/transaction reports — those must not be reused as a source of
enum/length constraints for DPMSR parties (see item 4 below).

| # | Field(s) | Issue found in first draft | Fix applied |
|---|---|---|---|
| 1 | All date-ish fields (`submissionDate`, `birthdate`, `registrationDate`, `incorporationDate`, `entityStatusDate`, `dateBusinessClosed`, `dateDeceased`) | XSD's `sql_date` type is actually restricted from `xs:dateTime`, **not** `xs:date`, despite the name | Kept as `TIMESTAMP`/`TIMESTAMPTZ` everywhere (already correct in §6, called out here for the record) |
| 2 | All country-code fields (`countryCode`, `nationality1/2/3`, `residence`, `passportCountry`, `incorporationCountryCode`) | `country_type` includes a 1-character `"-"` (Unknown) sentinel value alongside 2-char ISO codes — a fixed `CHAR(2)` would reject `"-"` | Changed `CHAR(2)` → `VARCHAR(2)` with a `CHECK (... = '-' OR length(...) = 2)` on each |
| 3 | `addressType`/`tphContactType` (`contact_type`), `itemType` (`trans_item_type`), `statusCode` (`trans_item_status`), `incorporationLegalForm` (`legal_form_type`), indicator codes (`report_indicator_type`) | Modeled as loosely-capped free `VARCHAR`; these are actually **fixed code-list enumerations**, all ≤5 chars | Tightened to `VARCHAR(5)` (documented as a fixed-enum field in each comment); a follow-up decision is whether to add a real `CHECK IN (...)` list or a lookup table (see open point 2) |
| 4 | `report_party_type.role` | Assumed it shared the transaction-report `party_role_type` enum/length | It doesn't — `report_party_role_type` (the DPMSR-party type) has **no enumeration and no length restriction** at all in the XSD. Widened to `VARCHAR(500)` (a practical cap the schema itself doesn't impose) rather than constraining it |
| 5 | `significance` | Modeled as `VARCHAR(50)` (assumed free text/code) | It's `xs:int`, `minInclusive=0`, `maxInclusive=10` — changed to `SMALLINT` with a `CHECK BETWEEN 0 AND 10` |
| 6 | `gender` | Modeled as `VARCHAR(5)` | `gender_type` is a 3-value enum: `-`, `F`, `M` only (length 1) — changed to `CHAR(1)` with `CHECK IN ('-','F','M')` |
| 7 | `estimatedValue` / `disposedValue` (goods) | Modeled as `NUMERIC(18,2)` | XSD: `xs:decimal`, `fractionDigits=4`, `|value| ≤ 7.9 × 10^14` — changed to `NUMERIC(19,4)` (15 integer digits + 4 fraction) |
| 8 | `size` (goods) | Modeled as `NUMERIC(18,4)` | XSD: `xs:decimal`, `fractionDigits=0` (integer-valued), same magnitude bound — changed to `NUMERIC(15,0)` |
| 9 | `taxRegNumber` on entity vs. person | Modeled with one shared length (`VARCHAR(50)`) across `involved_party_legal` and `involved_party_natural` | On `t_entity` the XSD literally caps `tax_reg_number` at **`maxLength=1`** — sharply different from `t_person`/`t_person_registration_in_report`, where it's `maxLength=100`. **Flagged, not just fixed** — a 1-char cap looks like it could be an XSD authoring quirk; verify against the live FIU schema/portal behavior before trusting it, since silently truncating real tax registration numbers to 1 char would be a data-loss bug |
| 10 | `t_address` sub-fields (`address`, `city`, `state`, `zip`, `town`, `houseNumber`, `apartmentNumber`, `additionalAddressLine1/2`) | Generic `VARCHAR(255)`/`VARCHAR(100)`/`VARCHAR(20)` guesses across `report_details.location_*`, `goods_and_services.address_*`, and the `primary_address_*` columns | Corrected per the real per-field maxLengths: `address` ≤100 (required), `city`/`state` ≤255 (required), `zip` ≤10, `houseNumber`/`apartmentNumber` ≤25, `additionalAddressLine1/2` ≤100, `town` ≤255 |
| 11 | `t_phone.tphNumber` (→ `primary_phone_number`) | Modeled as `VARCHAR(30)` | XSD allows up to 50 chars (required, minLength 1) — changed to `VARCHAR(50)` |
| 12 | `reportingPerson.sourceOfWealth` | Carried straight from the JSON sample into `report_details.reporting_person_source_of_wealth` | **Unresolved — flagged, not fixed.** The XSD's `t_person_registration_in_report` (the type `reportingPerson` should map to) does **not** define a `sourceOfWealth` element at all — only the general-purpose `t_person` does. The sample JSON has this field (as `null`), so either (a) the JAXB/DTO layer is more permissive than the raw XSD and the field is silently dropped when building XML, or (b) `reportingPerson` actually maps to a different/wider type than assumed here. **Needs a direct check against `DpmsrReportInput`/`TPersonRegistrationInReport` in the actual Java code before this column is trusted** — left in the DDL but called out as unverified |
| 13 | `businessClosed` (entity) and `deceased` (person) | Treated as ordinary nullable booleans | XSD marks both `fixed="1"` — meaning in valid XML the element is either **present with value true, or entirely absent**; there is no valid `false`. `BOOLEAN` is fine for SQL storage, but this is an **implementation note for the future XML-builder mapping code**, not a schema-only fix: when generating XML from these tables, a `false`/`FALSE` value must be mapped to "omit the element", never to a literal `false` |
| 14 | `entity_status`/`entityStatus` | Not deeply verified this pass — the agent's search surfaced `entity_status_type` as a coded type but didn't pull its exact enumeration | Left as `VARCHAR(50)` placeholder — **needs a follow-up XSD lookup** before implementation (same treatment as `party_account`, already flagged as a stub) |
| 15 | `name`/`commercialName` (entity), `firstName`/`middleName`/`lastName` (person) | Length caps were already close (255/100), but missed a constraint | XSD pattern restricts these to `[a-zA-Z0-9 .'-]*` — **ASCII only, no accented characters, no non-Latin scripts**. Not encoded as a SQL `CHECK` in the DDL above (deliberately, since UAE business names can be legitimately multi-script and the practical FIU portal behavior for this pattern should be confirmed first) — flagged for a decision, not silently enforced |
| 16 | `reportingPerson.prefix` | Single `VARCHAR(20)` shared assumption | `t_person_registration_in_report.prefix` is capped at 10; the party-level `t_person.prefix` allows 100 — the two tables now use different lengths accordingly (`report_details.reporting_person_prefix` = `VARCHAR(10)`, `involved_party_natural.prefix` = `VARCHAR(100)`) |
| 17 | `passportNumber` / `passportCountry` | Modeled as independently nullable | XSD groups them as a linked optional pair — if one is present, the other is required. Added `CHECK ((passport_number IS NULL) = (passport_country IS NULL))` on both `report_details` and `involved_party_natural` |
| 18 | `statusComments` (goods) | Modeled as unbounded `TEXT` (assumed same `comments_type`, 8000 chars, as other comment fields) | XSD caps it specifically at `maxLength=500`, not 8000 — changed to `VARCHAR(500)` |

**Update (2026-09-02) — deep-dig follow-up: all 4 previously-flagged items resolved.** Each was
re-verified by reading the exact XSD element declarations directly (not summaries) and cross-checking
the generated JAXB classes and the hand-written Java mapper code. Results below; the DDL in §6 has
already been updated accordingly.

**Q1 — `t_entity.tax_reg_number` maxLength=1 — RESOLVED, not a misread.**
Confirmed exact in the XSD, in both `t_entity_my_client` and `t_entity`:
```xml
<xs:element name="tax_reg_number" minOccurs="0">
  <xs:simpleType>
    <xs:restriction base="xs:string">
      <xs:minLength value="1" />
      <xs:maxLength value="1" />
    </xs:restriction>
  </xs:simpleType>
</xs:element>
```
This is a real, separate element from the sibling `tax_number` (which is the normal free-length tax ID,
`maxLength=100`). The 1-char restriction is specific to `t_entity`/`t_entity_my_client` — the equivalent
element on `t_person`/`t_person_registration_in_report` is `maxLength=100`, a normal field. It's almost
certainly a single-character flag (e.g. "tax registered Y/N"), not a truncated registration number.
**Kept as `VARCHAR(1)` in `involved_party_legal.tax_reg_number`** — this is a genuine, deliberate FIU
constraint, not a bug to route around.

**Q2 — `reportingPerson.sourceOfWealth` — RESOLVED, the XSD field is real; the gap is in the app code.**
`t_person_registration_in_report` (the type `reporting_person` actually uses, confirmed at the XSD's
`report` element) **does** declare `source_of_wealth` (`maxLength=255`, optional) — right after
`tax_reg_number`. The generated `TPersonRegistrationInReport.java` has the field with a working
getter/setter. **But** `DpmsrCreateRequest.Person` (the shared DTO for both parties and
`reportingPerson`) has no `sourceOfWealth` component at all, and `DpmsrRequestMapper.reportingPerson(...)`
never sets it — unlike `countryOfBirth`, which the mapper's own doc comment explicitly calls out as
intentionally unmapped, `sourceOfWealth` isn't a documented exclusion, just a field the DTO never
gained. **Kept the SQL column** (`report_details.reporting_person_source_of_wealth`) since it's a
legitimate, reachable XSD field — but this surfaces a real app-layer gap worth fixing independently of
this schema work: today a `reportingPerson.sourceOfWealth` value can be captured in `report.input` JSON
but is silently never written into the submitted XML.

**Q3 — `entity_status`/`entityStatus` enumeration — RESOLVED, it's unrestricted free text.**
```xml
<xs:simpleType name="entity_status_type">
  <xs:restriction base="xs:string" />
</xs:simpleType>
```
No enumeration, no `maxLength`, no pattern — genuinely open `xs:string`. Both `entity_status` and
`entity_status_date` sit in an optional (`minOccurs="0"`) sequence on `t_entity`/`t_entity_my_client`.
**Changed `entity_status` from the `VARCHAR(50)` placeholder to `VARCHAR(255)`** (no schema-imposed cap,
so 255 is a practical choice, not a derived constraint) — no `CHECK` constraint is appropriate here.

**Q4 — full code lists for the coded/enum fields — RESOLVED with exact counts, decision still open.**
All seven are genuine `xs:enumeration`-restricted types (not `pattern`), so a `CHECK IN (...)` is
structurally valid for any of them:

| Type | Backs | Count | Notes |
|---|---|---|---|
| `contact_type` | `addressType`/`tphContactType` | **8** | `-, BU, OFFIC, OPRTL, PRIVT, PRSNL, REG, RES` — full list, small |
| `communication_type` | `tphCommunicationType` | **6** | `-, F, L, M, P, S` — full list, small |
| `trans_item_status` | goods `statusCode` | **20** | full list — small |
| `trans_item_type` | goods `itemType` | **63** | medium-sized, FIU-maintained |
| `legal_form_type` | `incorporationLegalForm` | **97** | medium-sized, FIU-maintained |
| `report_indicator_type` | indicator codes | **423** | large, FIU-maintained, likely to grow |
| `country_type` | all country-code fields | **253** | large; confirmed the `"-"` (Unknown) sentinel is a real member |

**DECIDED (2026-09-02, per requester):** stay at exactly 5 tables — no lookup/reference tables for
`trans_item_type`, `legal_form_type`, `report_indicator_type`, or `country_type`. Inline `CHECK IN (...)`
stays only on the three small, stable lists already in §6 (`contact_type` → `location_address_type`/
`address_type`; `trans_item_status` → `status_code`). The four large FIU-maintained lists remain plain
`VARCHAR` with a code comment noting the confirmed count — validated only by the existing XSD validation
gate before submission, not by a SQL constraint. This closes the decision that was previously open point
6.

**Q5 — `party_account` real fields — RESOLVED, and it changes the table budget answer.**
Read `t_account` (XSD line ~504) and `t_account_my_client` (line ~329) directly — both are legitimate
choice branches inside `report_party_type` alongside `entity`/`entityMyClient`/`person`/`personMyClient`,
same pattern. Full confirmed field list for `t_account` (base type; `t_account_my_client` is a stricter
5.2 variant that promotes several of these from optional to required — see below):

| Field | XSD type/restriction |
|---|---|
| `institution_name` | string 1–255 |
| `institution_code` / `swift` (choice) | string 1–50 / string 1–11 |
| `institution_country` | `country_type` |
| `non_bank_institution` | boolean |
| `collection_account` | boolean, `fixed="1"` (present-as-true only, same as `businessClosed`/`deceased`) |
| `branch` | string 1–255 |
| `account_category` | unrestricted `xs:string` (no enum, no maxLength) |
| `account` | string 1–255, **required** |
| `currency_code` | `currency_type` |
| `account_funds[]` | complex, unbounded — `currency_code`, `currency_balance` (decimal), `currency_balance_date` (date) |
| `account_name` | string 1–255, pattern `[a-zA-Z0-9 .'-]*` (same ASCII restriction as entity/person names), **required** |
| `iban` | string, max 34 |
| `client_number` | string 1–30 |
| `account_type` / `personal_account_type` (choice) | `account_type` enum, ~26 values, max 5 chars (deprecated variant also exists) |
| `t_entity` (owning institution as an entity) | full `t_entity` block, optional |
| `related_entities[]` | complex, unbounded |
| `signatory[]` / `related_persons[]` (choice) | complex, unbounded — each carries a nested `t_person` |
| `related_accounts[]` | complex, unbounded — each nests another `t_account` |
| `opened` / `closed` | `sql_date` (i.e. `dateTime`, per the same `sql_date` quirk as §6a item 1) |
| `balance` + `date_balance` (paired) | `sql_decimal` / `sql_date` |
| `status_code` + `status_date` (paired) | `account_status_type` enum (not fully enumerated in this pass) / `sql_date` |
| `beneficiary` | string, max 50 |
| `beneficiary_comment` | string, max 255 |
| `network_devices[]` / `sanctions[]` / `additional_information[]` | complex, unbounded (5.2+) |
| `comments` | `comments_type` |

`t_account_my_client` promotes `institution_name`, `non_bank_institution`, `branch`, `currency_code`,
the `account_type` choice, `opened`, the `balance`/`status_code` groups, and `beneficiary` from optional
to required, and uses `t_entity_my_client`/`t_person_my_client` for its nested entity/person blocks
instead of the plain versions.

**Live-data check — CORRECTED (2026-09-02).** The first pass wrongly reported "0 rows anywhere" —
that check connected to a *different*, empty Docker Postgres instance (the `goaml-postgres` container on
port 5544, per `docs/17-suite-connections-and-admin-guide.md`), not the real dev database. The requester
pointed out via a pgAdmin screenshot that a **natively-installed local PostgreSQL 18 service on port
5432** holds the real data, in a database named **`VyttahgoAML`** (distinct from the similarly-named
`VyttahAML` database, which belongs to the separate AML suite, not goAML). Re-connected there directly
(`psql -h localhost -p 5432 -U postgres -d VyttahgoAML`, read-only queries only):

- `tenant_vyttah.report`: **20 real rows** (17 `VALID`, 3 `INVALID`, all `report_code = DPMSR`).
  `tenant_demo.report`: 0 rows (demo tenant genuinely has none).
- **Across all 20 rows' `parties[]`, `account` and `accountMyClient` are never populated** (checked with
  `jsonb_typeof(...) = 'object'`, not just key-presence — the JSON always has the keys, but as `null`).
  `entity`, `entityMyClient`, and `personMyClient` **are** used in real data; plain `person` is not. This
  is a materially stronger confirmation of the same conclusion — account-type parties are genuinely
  unused in practice here, not just "no sample happened to include one."
- **`t_entity.tax_reg_number` = 1 char is now confirmed by real data, not just the XSD.** Every populated
  `taxRegNumber` value across the 20 rows is the single character `"N"` — a real Y/N flag, exactly as
  predicted in §6a Q1. The `VARCHAR(1)` column is correct.
- **New finding: the entity/person ASCII-only name pattern is genuinely enforced, not theoretical.** One
  `entityMyClient.name` value is `"LAUSANNE JEWELLERS (L.L.C.)"` — it contains parentheses, which the XSD
  pattern `[a-zA-Z0-9 .'-]*` disallows — and that report's status is **`INVALID`**. This confirms leaving
  the ASCII pattern *unenforced* at the SQL level (§6a item 15, open point 10) was the right call, not
  just a cautious default: `report.input` (and therefore the new section tables, once dual-write and
  backfill are in place) must be able to store `INVALID`/`DRAFT` rows that violate XSD patterns — a SQL
  `CHECK` on the name pattern would make backfill fail on this exact row.
- Sanity-checked real values against every `CHECK IN (...)` added in §6: real `addressType` (`BU`),
  `statusCode` (`PRCHS`, `UAPPL`, `ACTVO`, `ACTUC`, `SOLD`, `PNDNG`, `TOM`, `-`), and `gender` (`M`, or
  SQL `NULL`) all fall inside the confirmed enum lists — no violations found. Real `itemType`
  (`APA`, `DIMND`, `FINA`, `ACTNT`, `BROKC`, `-`) and `incorporationLegalForm` (`ACCOM`, `BUILD`,
  `ECPNG`, `AAFSI`, `CIVEN`, `MJEWR`, `-`) values are consistent with the confirmed 5-char code shape
  (not individually cross-checked against the full 63/97-value lists, since those stay unconstrained
  `VARCHAR` per the "stick to 5 tables" decision). Max observed string lengths (`action` 35,
  `reason` 101, `location.address` 12, `reportingPerson.firstName` 12) are all comfortably inside the
  `VARCHAR` caps in §6 — no truncation risk found in real data.

**This changes the answer, not just fills in the stub.** The account structure is a full nested
sub-graph (an entity, unbounded persons, unbounded related accounts, unbounded funds/devices/sanctions) —
structurally much heavier than `entity`/`person`, and critically: **the senior's original 5-table
directive (`goods_and_services`, `report_details`, `involved_party_natural`, `involved_party_legal`,
`additional_details`) never included a 6th table for accounts at all.** Adding a flattened
`involved_party_account` table (which is what the earlier stub assumed) would break the "stick to 5
tables" decision just confirmed for the coded-list question — this needed to be surfaced explicitly
rather than quietly added as a 6th table.

**Resolution, staying inside the 5-table budget:** an `account`/`accountMyClient` party doesn't get its
own row-per-field table. It's stored as a single JSONB blob in the existing `additional_details` table
(no schema change needed — `additional_details` already supports report-level-only rows with every
owner FK null, exactly like `clientMetadata` does today), tagged `category = 'ACCOUNT_PARTY'`, with
`report_id` set and `goods_id`/`natural_party_id`/`legal_party_id` all `NULL`. The common party-level
fields that DO get first-class columns everywhere else (`role`, `reason`, `country`, `comments`,
`is_suspected`, `significance`, `is_my_client`) go inside that same JSON blob for account parties, for
consistency, rather than splitting them out — since there's no dedicated row for an account party to
attach flattened columns to. `party_ordinal` (position in the original `parties[]` array) is included
in the JSON payload itself so array order is still reconstructible for XML regeneration.

**Caveat to flag explicitly:** this means account-type parties are queryable only via JSONB path
operators (`data->>'accountNumber'`, etc.), not plain SQL columns like natural/legal parties are — an
intentional trade-off to stay within the 5-table budget, not an oversight. If account-type parties turn
out to be common enough in practice to need first-class SQL columns, that's a 6th-table (or restructure)
conversation for later, with real production examples to design against instead of an XSD-only guess.

## 6b. Reverification pass (2026-09-02) — readiness check

Re-read the entire §6 DDL end-to-end (not field-by-field this time — checking internal consistency,
constraint validity, and table-creation order) before declaring this ready to implement.

**Passed:**
- `CREATE TABLE` order is valid — `report_details`, `goods_and_services`, `involved_party_natural`,
  `involved_party_legal` all only reference `report(id)` (already exists); `additional_details` is
  created last and references all four, so no forward-reference errors.
- ~~`NOT NULL` placement is correct given each object's own optionality~~ — **this bullet was wrong and
  was corrected in §6c after actually executing the DDL against real data.** At this point in the review
  the reasoning was "XSD-required fields should be `NOT NULL`," which sounds right but isn't: a report can
  be saved as an in-progress `DRAFT`/`INVALID` row before those fields are filled in, and `report.input`
  has never enforced that. See §6c for the real failure this caused and the fix.
- All `CHECK` constraints (`country_type` sentinel, passport pairing, `significance` range, `gender`
  enum, the three small coded-field enums) are syntactically valid Postgres and match the §6a findings.

**One new issue found this pass — not yet fixed, flagging for the same sign-off round:**
- **`TIMESTAMP` vs `TIMESTAMPTZ` inconsistency.** The §6 tables use plain `TIMESTAMP` (no timezone) for
  every date/dateTime column, on the reasoning that the XSD's `sql_date` type is `xs:dateTime`. But the
  existing `report` table (and the original §5 draft) uses `TIMESTAMPTZ` throughout, and the sample JSON
  values are explicit UTC instants (`"2026-08-24T00:00:00Z"`) — which is exactly what `TIMESTAMPTZ` is
  for. Plain `TIMESTAMP` would silently discard the timezone/offset on write, which is a real correctness
  risk (not just a style nit) if any report is ever created or read from a non-UTC session timezone.
  **Recommend changing all `TIMESTAMP` columns in §6 to `TIMESTAMPTZ`** for consistency with `report` and
  to avoid timezone-loss bugs — flagged as open point 8 below, not yet applied to the DDL pending
  confirmation this doesn't conflict with how `ReportMarshaller` currently serializes `OffsetDateTime`
  back to XML (should be a non-issue, since Jackson/JAXB read `OffsetDateTime` either way, but worth a
  quick check before assuming).

## 6c. Executed verification against real data (2026-09-02) — final pass before implementation

Every check up to this point was either reading (XSD, code) or read-only querying. Before signing off
on "ready to implement," the §6 DDL was **actually executed** against the real `VyttahgoAML` database,
and a full backfill was **actually run** against all 20 real `tenant_vyttah.report` rows — in a
throwaway schema (`migration_verify_tmp`), never touching `tenant_vyttah`/`tenant_demo` themselves, and
dropped at the end regardless of outcome. This is a materially stronger check than reading the DDL and
reasoning about it, because Postgres itself enforces every constraint against every real row, not just
the ones considered during review.

**First run: failed, for a real reason.** Insert into `report_details` failed with:
```
ERROR: null value in column "reporting_person_first_name" of relation "report_details" violates not-null constraint
```
One real report (`b12bae83-…`, `entity_reference = TXN-1787039825069`, `status = INVALID`) has
`reportingPerson` as **JSON `null` entirely** — an incomplete draft, not a data-entry edge case. This
exposed a real design mistake: every `NOT NULL` that had been added on the reasoning "required per XSD"
(`location.address/city/state`, `reportingPerson.firstName/lastName`, party `reason`/`firstName`/
`lastName`/`name`/`incorporationNumber`) was wrong, because **"required for a valid XSD submission" and
"required to store a row at all" are different constraints**, and `report.input` has only ever enforced
the second one. A report can be — and in this real dataset, is — saved mid-draft with these fields
empty. A SQL `NOT NULL` modeled on XSD-requiredness would make the write-path (and the backfill script,
point 5) fail on real, legitimate rows.

**Fix applied:** removed `NOT NULL` from all of the above columns in the §6 DDL (each now carries a
comment noting it's "required per XSD when submitted" but not enforced at the SQL level, with a pointer
to this section). This mirrors the same reasoning already applied to the ASCII-name-pattern decision
(open point 10) — XSD "required"/"pattern" constraints belong to the *submission validation gate*, not
to whether a row can be stored at all.

**Second run: passed cleanly, zero errors.** After the fix, the same throwaway-schema DDL + backfill ran
end-to-end against all 20 real rows with no constraint violations, no cast failures, no truncation:

| Table | Rows inserted |
|---|---|
| `report_details` | 20 / 20 |
| `goods_and_services` | 20 / 20 |
| `involved_party_legal` | 16 |
| `involved_party_natural` | 5 |

(21 parties total across 20 reports — consistent with the earlier finding that `person`/`personMyClient`
is used far less than `entity`/`entityMyClient` in this tenant's real data.) The scratch schema was
dropped immediately after, confirmed via a clean `DROP SCHEMA ... CASCADE` with no errors — nothing was
left behind in the database.

**What this does and doesn't prove:** this confirms the DDL is syntactically valid Postgres, every
`CHECK`/`CHAR`/`VARCHAR` length constraint holds against all 20 real rows as they exist today, and a
reasonable backfill query can populate all four row-producing tables without error. It does **not**
prove the `DpmsrPersistenceMapper` (point 7, not yet written) will produce identical results to this ad
hoc SQL, or that re-marshalling XML from the new tables matches `ReportMarshaller`'s output from
`input` — that's the verification step already called out in point 5.3, and still needs to happen once
the real mapper exists.

## 7. Open points for senior dev sign-off

1. **This reverses a documented design decision** in `V2__reports.sql` (JSONB chosen deliberately to
   avoid drift vs. the XSD-generated model). Reviving that risk needs explicit sign-off — who owns
   keeping this schema in sync if/when the goAML XSD version changes?
2. ~~Coded fields stay `VARCHAR`, not enums~~ — **SUPERSEDED by point 6's more granular answer**: small
   stable lists (`contact_type`, `trans_item_status`) got real `CHECK IN (...)` constraints in §6; large
   FIU-maintained lists (`itemType`, `incorporationLegalForm`, indicator codes, country codes) stay plain
   `VARCHAR` per the "stick to 5 tables" decision.
3. ~~`party_account` is a stub~~ — **RESOLVED (§6a Q5), now confirmed against real data too.** Real
   `t_account`/`t_account_my_client` fields pulled directly from the XSD, and cross-checked against the
   real dev database (`VyttahgoAML`, `tenant_vyttah.report`, 20 rows) — `account`/`accountMyClient` is
   never populated in any of the 20 real DPMSR reports (checked properly, by JSON value type, not just
   key-presence). Given the structure's real size (nested entity + unbounded persons/accounts/funds), that
   it's genuinely unused in real data, and that account parties were never part of the senior's 5-table
   directive, the resolution is to store account-type parties as a single JSONB row in
   `additional_details` (`category = 'ACCOUNT_PARTY'`, report-level, no owner FK) rather than adding a 6th
   table — no DDL change needed, this uses the existing overflow mechanism. Confirm this trade-off
   (JSONB-queryable only, not plain-column-queryable like the other two party tables) is acceptable.
4. **Scope is DPMSR-only.** `.planning/PROJECT.md` lists 17 planned goAML report codes; `report_goods`
   is activity-shaped and specific to DPMSR (goods + parties, no `<transaction>` block). Other report
   types (transaction-shaped) will need their own detail tables later — confirm this "generic `report`
   + type-specific detail tables" pattern is the intended direction before it's used as a template.
5. **Migration/backfill strategy — DECIDED (2026-09-02, per requester): both, not either/or.**
   A one-time backfill script *alone* only fixes existing rows — every report created after go-live
   would still be JSON-only until someone remembers to backfill it too, so the two tables drift apart
   again immediately. A backfill only pays off if it's paired with a write-path change. The recommended
   sequence:
   1. **Ship the write-path change first.** `DpmsrPersistenceMapper` (§7 point 7) starts writing to both
      `report.input` (JSON, unchanged) and the 5 new section tables, in the same transaction, for every
      **new** report create/update from the day this deploys. This is the part that actually stops the
      drift — everything from here on is covered automatically.
   2. **Then run a one-time backfill** (a Flyway migration or a standalone ops script, not part of normal
      app traffic) that reads every *existing* `report.input` row and populates the section tables from
      it, using the same mapper logic as step 1 so there's exactly one parsing implementation, not two to
      keep in sync. Run it once, off-peak, with row-count/error logging so partial failures are visible.
   3. **Verify before trusting the new tables anywhere:** spot-check (or a full scripted diff) that
      re-deriving JSON from the section tables — or re-running `ReportMarshaller` against both paths —
      produces the same XML for a sample of backfilled reports.
   4. **`report.input` is kept, not dropped**, even after backfill completes — it stays the fallback/audit
      trail per the §2 decision. Revisit dropping it only after the section tables have been live and
      trusted in production for a while.
6. **From the §6a XSD verification pass — all 4 items now fully resolved:**
   - ~~`t_entity.tax_reg_number` maxLength=1~~ — **RESOLVED.** Confirmed genuine and distinct from
     `tax_number`; kept as `VARCHAR(1)`.
   - ~~`reportingPerson.sourceOfWealth`~~ — **RESOLVED.** The XSD field is real and reachable; kept the
     column. Surfaced a separate, real app-layer gap: `DpmsrCreateRequest.Person`/
     `DpmsrRequestMapper.reportingPerson()` never populate it today, so it can never reach the submitted
     XML as-is — **recommend a follow-up ticket to fix the mapper**, independent of this schema work.
   - ~~`entity_status_type` enumeration~~ — **RESOLVED.** Confirmed unrestricted free text; widened the
     column to `VARCHAR(255)`, no `CHECK` needed.
   - ~~Coded-list CHECK vs. lookup tables~~ — **DECIDED.** Stick to 5 tables — no lookup/reference tables
     for the 4 large lists (`trans_item_type`, `legal_form_type`, `report_indicator_type`,
     `country_type`); they stay plain `VARCHAR`. The 3 small/stable lists already have inline
     `CHECK IN (...)` in §6.
7. **Implementation follow-up (once approved):** a Flyway migration for the DDL above, JPA entities for
   each table, and a `DpmsrPersistenceMapper` (parallel to the existing `DpmsrRequestMapper`) to
   read/write these tables instead of/alongside `input`, keeping `ReportMarshaller`/`DpmsrReportBuilder`
   XML generation working unchanged.
8. ~~`TIMESTAMP` vs `TIMESTAMPTZ`~~ — **DECIDED.** Switched to `TIMESTAMPTZ`, applied throughout §6.
9. ~~`is_my_client` modeling~~ — **CONFIRMED acceptable** as-is (boolean flag on one shared table per
   party kind, rather than 4 separate tables).
10. ~~ASCII-only name pattern~~ — **CONFIRMED, and now backed by real data (§6a Q5 live-data check)**:
    leave unenforced at the SQL level. A real `INVALID` report in `tenant_vyttah.report` has an entity
    name containing parentheses (violates the XSD pattern) — proving both that the pattern is genuinely
    enforced by the app's validation gate (that's plausibly part of why the report is `INVALID`), and
    that a SQL-level `CHECK` here would be actively wrong: `INVALID`/`DRAFT` rows with pattern violations
    must still be storable, or the backfill script (point 5) would fail on real existing rows.

### Readiness verdict (updated 2026-09-02, after executing the DDL against real data)

**Ready to implement.** Beyond the field-level XSD verification (§6a) and desk-check consistency pass
(§6b), the DDL in §6 has now actually been **run** — not just read — against the real `VyttahgoAML`
database, and a full backfill was executed against all 20 real `tenant_vyttah.report` rows in a
throwaway schema (§6c). That run caught one genuine bug (several `NOT NULL` constraints that would have
rejected real, legitimately-stored draft/invalid reports) — fixed, and confirmed clean on re-run: 20/20
`report_details`, 20/20 `goods_and_services`, 16 `involved_party_legal`, 5 `involved_party_natural`, zero
errors. The scratch schema was dropped afterward; nothing was left in the database.

- ✅ Sign-off to reverse the JSONB-only decision (point 1, §2).
- ✅ Coded-field CHECK vs. plain `VARCHAR`, resolved at the granular level (points 2 & 6, §6a Q4).
- ✅ `party_account` — real XSD fields identified, and its "unused in practice" conclusion confirmed
  against the actual dev database (point 3, §6a Q5).
- ✅ Migration/backfill strategy: write-path change first, then a one-time backfill reusing the same
  mapper logic, `report.input` kept not dropped (point 5) — and now proven to actually work against real
  rows (§6c), not just designed on paper.
- ✅ `TIMESTAMPTZ`, `is_my_client` modeling, ASCII-name handling (points 8-10) — the ASCII-name decision
  is backed by a real `INVALID` report that actually violates the pattern.
- ✅ `NOT NULL` placement (§6c) — corrected after the real run: XSD-"required" fields are no longer
  `NOT NULL` in SQL, since draft/invalid reports must remain storable.
- ⏳ **Point 4 — scope/pattern for future report types** is the one item still genuinely open: whether
  the "generic `report` + type-specific detail tables" pattern used here for DPMSR is the intended
  template for the other 16 planned report codes. This doesn't block *starting* DPMSR implementation
  (nothing about it changes the DPMSR DDL), but the senior should weigh in before the second report type
  is built, so the pattern isn't accidentally locked in by precedent alone.

**Correction on record:** an earlier pass in this document wrongly reported 0 rows in both tenant
schemas — that check hit a different, empty Docker Postgres instance, not the real dev database. The
requester caught this via a pgAdmin screenshot; corrected findings (against `VyttahgoAML` on
`localhost:5432`, 20 real rows in `tenant_vyttah.report`) are in the "Live-data check — CORRECTED" block
(§6a Q5), and the DDL has since been executed against that same real database directly (§6c) — the
strongest verification level available short of writing the actual application code.

**Bottom line:** the DDL in §6 is field-level verified against the XSD (§6a), internally consistent on
review (§6b), and **has actually been executed successfully against every real row that exists today**
(§6c). This can now be turned into a Flyway migration, JPA entities, and the `DpmsrPersistenceMapper`
(point 7) with high confidence — that's the next step whenever you're ready to move from design to code.

## 8. Implemented (2026-09-02)

The design in §6/§6a/§6b/§6c was implemented, then the requester asked for one further change: the
5 table names were prefixed with `dpmsr_` so they group together and are immediately recognizable in a
table browser (pgAdmin lists tables alphabetically, and the un-prefixed names were interleaved with
unrelated existing tables like `attachment`/`notification`/`submission`).

**§6/§6a/§6b/§6c above are left as originally written and are NOT updated with the new names** — they're
the accurate historical record of what was designed, verified against the XSD, and executed against real
data at the time, under the original names. Rewriting them to say `dpmsr_report_details` etc. would
misrepresent what was actually tested (e.g. §6c's "first run failed" trail is a factual account of a run
against `report_details`, not `dpmsr_report_details`). **This section is the current, authoritative state.**

**Final table names** (single migration, `V12__dpmsr_section_tables.sql`):

| Design-doc name (§6) | Final table name |
|---|---|
| `report_details` | `dpmsr_report_details` |
| `goods_and_services` | `dpmsr_goods_and_services` |
| `involved_party_natural` | `dpmsr_involved_party_natural` |
| `involved_party_legal` | `dpmsr_involved_party_legal` |
| `additional_details` | `dpmsr_additional_details` |

Indexes were renamed to match (`idx_dpmsr_goods_and_services_report`,
`idx_dpmsr_involved_party_natural_report`, `idx_dpmsr_involved_party_legal_report`,
`idx_dpmsr_additional_details_report`, `idx_dpmsr_additional_details_category`).

**One migration, not two — consolidated after the rename.** The rename was first shipped as a second
migration, `V13__dpmsr_table_prefix.sql` (`ALTER TABLE ... RENAME TO ...`), because V12 was already
applied to the real `tenant_vyttah`/`tenant_demo` schemas at that point, and Flyway checksums every
applied migration — editing V12 in place would have failed validation on the next app startup. The
requester then asked for a single migration instead. Since V12/V13 had only ever been applied in this
local dev environment (never shared/deployed anywhere else), squashing them was safe and is the correct
fix here — this was done properly, not just by editing the files:
1. Rewrote `V12__dpmsr_section_tables.sql` in place with the final `dpmsr_`-prefixed names directly
   (as if it had been written that way from the start) and deleted `V13__dpmsr_table_prefix.sql`.
2. On the real database: confirmed all 5 tables were still empty (no backfill had run yet), dropped
   them, and deleted the `version IN ('12','13')` rows from `flyway_schema_history` in both
   `tenant_vyttah` and `tenant_demo` — undoing the two-migration history rather than leaving it
   alongside a now-mismatched migration file.
3. Restarted the app: Flyway applied the new, single V12 fresh to both schemas — confirmed via the
   startup log (`Migrating schema "tenant_vyttah" to version "12 - dpmsr section tables"`, one line, not
   two) and via `flyway_schema_history` showing exactly one row for version 12, no version 13 anywhere.
4. Re-ran the full live verification (create a report through the real API, confirm all 5 tables +
   `additional_details` populate correctly, confirm cascade delete, clean up) — passed identically to
   the earlier checks.

**Files implemented:**
- `src/main/resources/db/migration/tenant/V12__dpmsr_section_tables.sql` — the 5 tables, final
  `dpmsr_`-prefixed names, one migration.
- `src/main/java/com/vyttah/goaml/model/entity/report/{ReportDetails,GoodsAndServices,InvolvedPartyNatural,InvolvedPartyLegal,AdditionalDetail}.java`
  — JPA entities. Java class names were **not** renamed (only `@Table(name = "dpmsr_...")`) — the request
  was to prefix the DB tables, not rename the Java model.
- `src/main/java/com/vyttah/goaml/repository/report/{ReportDetailsRepository,GoodsAndServicesRepository,InvolvedPartyNaturalRepository,InvolvedPartyLegalRepository,AdditionalDetailRepository}.java`
  — Spring Data repositories.
- `src/main/java/com/vyttah/goaml/model/mapper/report/{DpmsrSectionMapper,DpmsrSectionResult}.java` — maps
  the engine's `DpmsrReportInput` (the same JAXB object `DpmsrReportBuilder` turns into XML) onto the 5
  tables, so the section tables can never drift from what was actually filed — deliberately **not** built
  from the request DTOs (see the class Javadoc for why).
- `src/main/java/com/vyttah/goaml/service/report/DefaultReportService.java` — `doCreate()` now dual-writes:
  `report.input` (unchanged) + the 5 section tables, in the same `@Transactional` boundary. Also removed a
  pre-existing duplicate `import` line and a now-dead `resolveRentityId` method while touching this file.
- `src/test/java/com/vyttah/goaml/service/report/DefaultReportServiceTest.java` — updated for the
  constructor's new dependencies.

**Verification performed (repeated after each change — the rename, and again after consolidating to one
migration):**
1. `./gradlew compileJava` — clean, every time.
2. `DefaultReportServiceTest` — 17/17 passing every time (exercises create→validate→persist through the
   real engine).
3. Full suite — 529 tests, 470 passing, 48 failing / 11 skipped, identically every time. Every failure is
   the same pre-existing `ContainerFetchException` (Testcontainers can't fetch `postgres:16-alpine` in
   this sandbox) — confirmed unrelated to this work by stashing all changes and running the same test
   against clean `main`, where it failed identically.
4. **Live-executed against the real app + real `VyttahgoAML` database three separate times** (not a
   throwaway schema): once for the original 5-table names, once after the V12+V13 rename, and once more
   after consolidating back to a single V12. Each time: started the app pointed at the real database,
   confirmed Flyway applied cleanly to both real tenant schemas (`tenant_demo`, `tenant_vyttah`) with 0
   failures, logged in as a real demo-tenant user, created a report through the actual
   `POST /api/v1/reports` endpoint, confirmed all 5 tables populated correctly and matched what was
   submitted, confirmed `ON DELETE CASCADE` by deleting the test report and verifying all dependent rows
   vanished, deleted the test report, stopped the app and the `redis`/`localstack` containers started for
   this. Confirmed `tenant_vyttah` still has its original 20 rows and `tenant_demo` still has 0 after
   every run — real data untouched throughout all three passes.
5. After consolidation specifically: confirmed via `flyway_schema_history` that both real tenant schemas
   show exactly one row for version 12 and no row for version 13 — the database's migration history now
   matches what a fresh single-migration deploy would produce, not just the end-state table structure.

## 9. Backfill executed (2026-09-02)

The one-time historical backfill (§7 point 5, step 2) is done — all 20 real `tenant_vyttah.report` rows
now have section-table data, using the same mapper/save path as the create dual-write.

**Implementation:** rather than a throwaway script, this was built as reusable app code, gated off by
default:
- `DpmsrSectionBackfillService` — the transactional save logic, refactored out of
  `DefaultReportService` so there is exactly **one** implementation of "map + save the 5 tables," called
  by both the create dual-write and the backfill (no second parsing/mapping path to drift out of sync,
  per the original migration strategy decision in §7 point 5). Also reconstructs a `DpmsrReportInput`
  from an existing report's stored `input` JSON — trying `DpmsrReportPayload` first (what
  `POST /api/v1/reports`, the endpoint actually used for all 20 real rows, persists) and falling back to
  the curated `DpmsrCreateRequest` (`POST /api/v1/reports/dpmsr`) if that fails to deserialize.
- `DpmsrSectionBackfillRunner` — an `ApplicationRunner`, gated by
  `goaml.dpmsr-backfill.enabled=true` (off by default — never runs during normal startup, tests, or an
  accidental prod boot). Iterates every ACTIVE tenant, skips reports already backfilled
  (`dpmsr_report_details` row exists — idempotent, safe to re-run), and logs a per-tenant summary. A
  per-report failure is logged and skipped, not fatal, so one bad historical row can't abort the batch.

**First run: 19/20 succeeded, 1 failed — a second real bug the earlier dry-run (§6c) never exercised.**
`ERROR: value too long for type character varying(100)` on `dpmsr_involved_party_legal`. Root cause: the
`primary_address_line` column (holding the free-text `t_address.address` field) is `VARCHAR(100)` —
matching the XSD's own stated `maxLength`, same reasoning as every other column in §6. But a real
`INVALID` report in `tenant_vyttah` has an address that is **102 characters**, 2 over that cap. This is
the exact same class of problem as the `NOT NULL` bug found in §6c — an `INVALID`/`DRAFT` report is not
bound by any XSD constraint, length included, not just requiredness — but it wasn't caught by the earlier
throwaway-schema dry-run because that ad hoc verification SQL never included the `primary_address_line`/
`primary_phone_number` columns in its `INSERT` at all. Real execution through the real mapper against
every real row, end to end, is what caught it.

**Fix — first shipped as `V13`, then consolidated back into `V12`.** Widened the free-text `address` line
specifically (not its siblings `houseNumber`/`apartmentNumber`/`additionalLine1/2`/`town`/`city`/`state`,
none of which overflowed across the 20 rows) from `VARCHAR(100)` to `TEXT` in all 4 places it appears:
`dpmsr_report_details.location_address`, `dpmsr_goods_and_services.address_line`,
`dpmsr_involved_party_natural.primary_address_line`, `dpmsr_involved_party_legal.primary_address_line`.
No entity changes needed (the JPA entities never declared an explicit `@Column` length on these fields).

This was first shipped as a second migration, `V13__dpmsr_widen_address_line.sql`
(`ALTER COLUMN ... TYPE TEXT`), since at that point 19 rows were already genuinely backfilled and
dropping the tables to re-squash into V12 would have destroyed real (if fully reproducible) work. The
requester then asked, a second time, for a single migration. Since every `dpmsr_*` row is 100% derived
from `report.input` — nothing hand-entered, nothing that can't be regenerated — squashing was still safe
here, just via one extra step: merged the `TEXT` columns directly into `V12__dpmsr_section_tables.sql`,
deleted `V13`, dropped the 5 tables and their `flyway_schema_history` rows (versions 12 and 13) in both
real tenant schemas, then let the app recreate the schema fresh from the single consolidated V12 and
**re-ran the backfill** (idempotent, reusing the same tested path) to regenerate identical data. Confirmed
via `flyway_schema_history`: exactly one row for version 12, none for version 13, in both schemas — and
this time the backfill completed **20/20 on the very first pass, zero failures**, since the fix was
already in the schema from the start.

**Final state, verified directly:**

| Table | Rows |
|---|---|
| `dpmsr_report_details` | 20 |
| `dpmsr_goods_and_services` | 20 |
| `dpmsr_involved_party_legal` | 16 |
| `dpmsr_involved_party_natural` | 5 |
| `dpmsr_additional_details` | 21 |

Every one of the 20 real `report` rows has exactly one `dpmsr_report_details` row (checked via a `LEFT
JOIN ... WHERE d.report_id IS NULL`, 0 missing). The previously-failing report's 102-character address is
now stored in full. These counts exactly match the earlier §6c throwaway dry-run's predictions
(20/20/16/5) — confirming the real `DpmsrSectionMapper` behaves identically to that hand-written SQL
approximation for every row except the two real edge cases (`NOT NULL`, this length cap) that only an
exhaustive, real, end-to-end run could surface.

Full test suite re-run clean afterward (470 passing, same 48 pre-existing unrelated Testcontainers
failures). `tenant_vyttah.report`/`tenant_demo.report` row counts unchanged throughout (20 / 0) — the
backfill only added section-table rows, never touched `report` itself.

**Status: this migration is now fully implemented, live-verified against real data three times over
(original design, the rename, the backfill), and complete.** `report.input` remains the source of truth
for XML generation, untouched throughout; the 5 `dpmsr_*` tables now mirror every existing report plus
every new one going forward.
