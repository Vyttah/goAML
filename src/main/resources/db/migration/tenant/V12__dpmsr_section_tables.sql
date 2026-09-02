-- ============================================================================
-- DPMSR relational mirror (migrate-json-storage-to-sql). Five section tables that
-- mirror report.input alongside it — input stays the source of truth for XML
-- generation (ReportMarshaller/DpmsrReportBuilder are unchanged); these tables
-- exist so individual DPMSR fields are queryable/indexable in SQL. Prefixed
-- dpmsr_ so they group together in a table browser instead of being
-- alphabetically interleaved with unrelated tables (attachment, notification,
-- report, submission, ...).
--
-- Column types/lengths/constraints are verified field-by-field against the raw
-- goAML XSD (src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd) and executed
-- against the real tenant_vyttah.report data during design
-- (see .planning/migrate-json-storage-to-sql.md for the full trail).
--
-- NOT NULL is deliberately NOT used for XSD-"required" fields: a report can be
-- stored as an in-progress DRAFT/INVALID row before those fields are filled in
-- (report.input has never enforced XSD-requiredness), and a real such row exists
-- in tenant_vyttah.report today. XSD-requiredness is enforced by the existing
-- validation gate (ReportValidator/XsdSchemaValidator), not by this schema.
-- ============================================================================

-- ---- 2. dpmsr_report_details: header/scalar section (1:1 with report) -------
CREATE TABLE dpmsr_report_details (
    report_id                         UUID PRIMARY KEY REFERENCES report(id) ON DELETE CASCADE,
    action                             VARCHAR(8000),
    reason                             VARCHAR(8000),
    fiu_ref_number                     VARCHAR(255),
    rentity_branch                     VARCHAR(255),
    submission_date                    TIMESTAMPTZ,
    indicators                         VARCHAR(5)[],
    -- location (single t_address). address is TEXT, not VARCHAR(100): the XSD's stated maxLength=100
    -- is a submission-validity constraint, not a storage constraint — a real INVALID/DRAFT report in
    -- tenant_vyttah has a 102-char address, and this table must be able to store it regardless.
    location_address                   TEXT,
    location_house_number              VARCHAR(25),
    location_apartment_number          VARCHAR(25),
    location_additional_line1          VARCHAR(100),
    location_additional_line2          VARCHAR(100),
    location_town                      VARCHAR(255),
    location_city                      VARCHAR(255),
    location_state                     VARCHAR(255),
    location_zip                       VARCHAR(10),
    location_country_code              VARCHAR(2)
        CHECK (location_country_code IS NULL OR location_country_code = '-' OR length(location_country_code) = 2),
    location_address_type              VARCHAR(5)
        CHECK (location_address_type IS NULL OR location_address_type IN
            ('-','BU','OFFIC','OPRTL','PRIVT','PRSNL','REG','RES')),
    location_comments                  VARCHAR(8000),
    -- reportingPerson (single t_person_registration_in_report)
    reporting_person_first_name        VARCHAR(100),
    reporting_person_middle_name       VARCHAR(100),
    reporting_person_last_name         VARCHAR(100),
    reporting_person_gender            CHAR(1)
        CHECK (reporting_person_gender IS NULL OR reporting_person_gender IN ('-','F','M')),
    reporting_person_title             VARCHAR(30),
    reporting_person_prefix            VARCHAR(10),
    reporting_person_birthdate         TIMESTAMPTZ,
    reporting_person_birth_place       VARCHAR(255),
    reporting_person_mothers_name      VARCHAR(100),
    reporting_person_alias             VARCHAR(100),
    reporting_person_ssn               VARCHAR(25),
    reporting_person_id_number         VARCHAR(255),
    reporting_person_passport_number   VARCHAR(255),
    reporting_person_passport_country  VARCHAR(2)
        CHECK (reporting_person_passport_country IS NULL OR reporting_person_passport_country = '-'
            OR length(reporting_person_passport_country) = 2),
    reporting_person_nationality1      VARCHAR(2)
        CHECK (reporting_person_nationality1 IS NULL OR reporting_person_nationality1 = '-'
            OR length(reporting_person_nationality1) = 2),
    reporting_person_nationality2      VARCHAR(2)
        CHECK (reporting_person_nationality2 IS NULL OR reporting_person_nationality2 = '-'
            OR length(reporting_person_nationality2) = 2),
    reporting_person_nationality3      VARCHAR(2)
        CHECK (reporting_person_nationality3 IS NULL OR reporting_person_nationality3 = '-'
            OR length(reporting_person_nationality3) = 2),
    reporting_person_residence         VARCHAR(2)
        CHECK (reporting_person_residence IS NULL OR reporting_person_residence = '-'
            OR length(reporting_person_residence) = 2),
    reporting_person_occupation        VARCHAR(255),
    reporting_person_employer_name     VARCHAR(255),
    reporting_person_tax_number        VARCHAR(100),
    reporting_person_tax_reg_number    VARCHAR(100),
    reporting_person_source_of_wealth  VARCHAR(255),
    reporting_person_deceased          BOOLEAN,
    reporting_person_date_deceased     TIMESTAMPTZ,
    reporting_person_comments          VARCHAR(8000),
    CHECK ((reporting_person_passport_number IS NULL) = (reporting_person_passport_country IS NULL))
);

-- ---- 1. dpmsr_goods_and_services: one row per goods[] item -------------------
CREATE TABLE dpmsr_goods_and_services (
    id                        UUID PRIMARY KEY,
    report_id                 UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    ordinal                   INT NOT NULL,
    item_type                 VARCHAR(5),
    item_make                 VARCHAR(255),
    description               VARCHAR(8000),
    previously_registered_to  VARCHAR(500),
    presently_registered_to   VARCHAR(500),
    estimated_value           NUMERIC(19,4),
    status_code               VARCHAR(5)
        CHECK (status_code IS NULL OR status_code IN
            ('-','ACTUC','ACTVO','CNL','CONT','DONTD','DSTRY','EXCHD','FRZN','INHTD','LET','LSD',
             'MORTG','PNDNG','PRCHS','SOLD','TOM','UAPPL','UNCLM','WTH')),
    status_comments           VARCHAR(500),
    disposed_value            NUMERIC(19,4),
    currency_code             CHAR(3),
    size                      NUMERIC(15,0),
    size_uom                  VARCHAR(250),
    registration_date         TIMESTAMPTZ,
    registration_number       VARCHAR(500),
    identification_number     VARCHAR(255),
    comments                  VARCHAR(8000),
    -- goods[].address (single t_address); address is TEXT — see the report_details.location_address note
    address_line              TEXT,
    address_city              VARCHAR(255),
    address_state             VARCHAR(255),
    address_country_code      VARCHAR(2)
        CHECK (address_country_code IS NULL OR address_country_code = '-' OR length(address_country_code) = 2),
    address_zip                VARCHAR(10),
    address_type               VARCHAR(5)
        CHECK (address_type IS NULL OR address_type IN ('-','BU','OFFIC','OPRTL','PRIVT','PRSNL','REG','RES'))
);
CREATE INDEX idx_dpmsr_goods_and_services_report ON dpmsr_goods_and_services(report_id);

-- ---- 3. dpmsr_involved_party_natural: parties[] where person/personMyClient present
CREATE TABLE dpmsr_involved_party_natural (
    id                     UUID PRIMARY KEY,
    report_id              UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    party_ordinal          INT NOT NULL,
    is_my_client           BOOLEAN NOT NULL,
    role                   VARCHAR(500),
    reason                 VARCHAR(8000),
    country                VARCHAR(2)
        CHECK (country IS NULL OR country = '-' OR length(country) = 2),
    comments               VARCHAR(8000),
    is_suspected           BOOLEAN,
    significance           SMALLINT CHECK (significance IS NULL OR significance BETWEEN 0 AND 10),
    first_name             VARCHAR(100),
    middle_name            VARCHAR(100),
    last_name              VARCHAR(100),
    gender                 CHAR(1) CHECK (gender IS NULL OR gender IN ('-','F','M')),
    title                  VARCHAR(30),
    prefix                 VARCHAR(100),
    birthdate              TIMESTAMPTZ,
    birth_place            VARCHAR(255),
    mothers_name           VARCHAR(100),
    alias                  VARCHAR(100),
    ssn                    VARCHAR(25),
    id_number              VARCHAR(255),
    passport_number        VARCHAR(255),
    passport_country       VARCHAR(2)
        CHECK (passport_country IS NULL OR passport_country = '-' OR length(passport_country) = 2),
    nationality1           VARCHAR(2)
        CHECK (nationality1 IS NULL OR nationality1 = '-' OR length(nationality1) = 2),
    nationality2           VARCHAR(2)
        CHECK (nationality2 IS NULL OR nationality2 = '-' OR length(nationality2) = 2),
    nationality3           VARCHAR(2)
        CHECK (nationality3 IS NULL OR nationality3 = '-' OR length(nationality3) = 2),
    residence              VARCHAR(2)
        CHECK (residence IS NULL OR residence = '-' OR length(residence) = 2),
    occupation             VARCHAR(255),
    employer_name          VARCHAR(255),
    deceased               BOOLEAN,
    date_deceased          TIMESTAMPTZ,
    tax_number             VARCHAR(100),
    tax_reg_number         VARCHAR(100),
    source_of_wealth       VARCHAR(255),
    primary_phone_number         VARCHAR(50),
    primary_address_line         TEXT,
    primary_address_city         VARCHAR(255),
    primary_address_country_code VARCHAR(2)
        CHECK (primary_address_country_code IS NULL OR primary_address_country_code = '-'
            OR length(primary_address_country_code) = 2),
    CHECK ((passport_number IS NULL) = (passport_country IS NULL))
);
CREATE INDEX idx_dpmsr_involved_party_natural_report ON dpmsr_involved_party_natural(report_id);

-- ---- 4. dpmsr_involved_party_legal: parties[] where entity/entityMyClient present
CREATE TABLE dpmsr_involved_party_legal (
    id                        UUID PRIMARY KEY,
    report_id                 UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    party_ordinal             INT NOT NULL,
    is_my_client              BOOLEAN NOT NULL,
    role                      VARCHAR(500),
    reason                    VARCHAR(8000),
    country                   VARCHAR(2)
        CHECK (country IS NULL OR country = '-' OR length(country) = 2),
    comments                  VARCHAR(8000),
    is_suspected              BOOLEAN,
    significance              SMALLINT CHECK (significance IS NULL OR significance BETWEEN 0 AND 10),
    name                      VARCHAR(255),
    commercial_name           VARCHAR(255),
    incorporation_legal_form  VARCHAR(5),
    incorporation_number      VARCHAR(50),
    business                  VARCHAR(255),
    entity_status             VARCHAR(255),
    entity_status_date        TIMESTAMPTZ,
    incorporation_state       VARCHAR(255),
    incorporation_country_code VARCHAR(2)
        CHECK (incorporation_country_code IS NULL OR incorporation_country_code = '-'
            OR length(incorporation_country_code) = 2),
    incorporation_date        TIMESTAMPTZ,
    business_closed           BOOLEAN,
    date_business_closed      TIMESTAMPTZ,
    tax_number                VARCHAR(100),
    tax_reg_number            VARCHAR(1),
    primary_phone_number         VARCHAR(50),
    primary_address_line         TEXT,
    primary_address_city         VARCHAR(255),
    primary_address_country_code VARCHAR(2)
        CHECK (primary_address_country_code IS NULL OR primary_address_country_code = '-'
            OR length(primary_address_country_code) = 2)
);
CREATE INDEX idx_dpmsr_involved_party_legal_report ON dpmsr_involved_party_legal(report_id);

-- ---- 5. dpmsr_additional_details: optional/overflow fields, JSONB, tagged by category
CREATE TABLE dpmsr_additional_details (
    id                UUID PRIMARY KEY,
    report_id         UUID NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    goods_id          UUID REFERENCES dpmsr_goods_and_services(id) ON DELETE CASCADE,
    natural_party_id  UUID REFERENCES dpmsr_involved_party_natural(id) ON DELETE CASCADE,
    legal_party_id    UUID REFERENCES dpmsr_involved_party_legal(id) ON DELETE CASCADE,
    category          VARCHAR(50) NOT NULL,
        -- PHONES, ADDRESSES, EMAILS, DIRECTOR_IDS, SANCTIONS, RELATED_ENTITIES, RELATED_PERSONS,
        -- NETWORK_DEVICES, ENTITY_IDENTIFICATIONS, URLS, ADDITIONAL_INFORMATION, ACCOUNT_PARTY
    data              JSONB NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (goods_id IS NOT NULL)::INT + (natural_party_id IS NOT NULL)::INT
        + (legal_party_id IS NOT NULL)::INT <= 1
    )
);
CREATE INDEX idx_dpmsr_additional_details_report ON dpmsr_additional_details(report_id);
CREATE INDEX idx_dpmsr_additional_details_category ON dpmsr_additional_details(category);
