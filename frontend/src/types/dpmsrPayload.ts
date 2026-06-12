/**
 * Mirror of the backend's full-fidelity `DpmsrReportPayload` (which binds the xjc-generated goAML types).
 * This is the JSON the builder POSTs to `POST /api/v1/reports`. Unlike the curated `DpmsrCreateRequest`,
 * it uses the schema's wrapper shape (`phones: { phone: [...] }`, `addresses: { address: [...] }`,
 * `directorId: [...]`, inline `identification: [...]`) and the schema property names (`nationality1`,
 * `tphNumber`). The `lib/dpmsrPayload.ts` adapter maps the flat form values onto this.
 *
 * DPMSR-fixed header values (submission_code E, report_code DPMSR, currency AED) and rentity_id are applied
 * server-side and are NOT carried here.
 */

export interface PhoneJson {
  tphContactType?: string;
  tphCommunicationType?: string;
  tphCountryPrefix?: string;
  tphNumber?: string;
}

export interface AddressJson {
  addressType?: string;
  address?: string;
  city?: string;
  countryCode?: string;
  state?: string;
}

export interface IdentificationJson {
  type?: string;
  number?: string;
  issueDate?: string;
  expiryDate?: string;
  issueCountry?: string;
}

export interface PhonesWrapper {
  phone: PhoneJson[];
}

export interface AddressesWrapper {
  address: AddressJson[];
}

/**
 * t_person_registration_in_report (the reporting person / MLRO). No identifications block. Names are
 * optional on the wire because the whole section may be omitted (the server fills the tenant's
 * configured goAML person); the Zod mirror enforces names-together when the section is supplied.
 */
export interface ReportingPersonJson {
  gender?: string;
  firstName?: string;
  lastName?: string;
  birthdate?: string;
  ssn?: string;
  passportNumber?: string;
  passportCountry?: string;
  nationality1?: string;
  residence?: string;
  idNumber?: string;
  occupation?: string;
  phones?: PhonesWrapper;
  addresses?: AddressesWrapper;
}

/**
 * t_person (a plain person party). Uses the inline `identification` list. UAE DPMSR activity-report
 * parties are plain person/entity — the `*_my_client` variants are not used there (matches the
 * backend curated path, the AML cockpit, and the real FIU samples).
 */
export interface PersonJson {
  gender?: string;
  firstName: string;
  lastName: string;
  birthdate?: string;
  ssn?: string;
  passportNumber?: string;
  passportCountry?: string;
  nationality1?: string;
  residence?: string;
  idNumber?: string;
  taxRegNumber?: string;
  occupation?: string;
  phones?: PhonesWrapper;
  addresses?: AddressesWrapper;
  identification?: IdentificationJson[];
}

/** t_entity_person (a director); extends the person fields with a role + inline identification. */
export interface DirectorJson {
  gender?: string;
  firstName: string;
  lastName: string;
  birthdate?: string;
  ssn?: string;
  passportNumber?: string;
  passportCountry?: string;
  idNumber?: string;
  nationality1?: string;
  residence?: string;
  role?: string;
  phones?: PhonesWrapper;
  addresses?: AddressesWrapper;
  identification?: IdentificationJson[];
}

export interface EntityJson {
  name: string;
  commercialName?: string;
  incorporationNumber?: string;
  incorporationState?: string;
  incorporationCountryCode?: string;
  incorporationDate?: string;
  phones?: PhonesWrapper;
  addresses?: AddressesWrapper;
  directorId?: DirectorJson[];
}

/** report_party_type — exactly one of entity / person is set. */
export interface PartyJson {
  reason?: string;
  comments?: string;
  entity?: EntityJson;
  person?: PersonJson;
}

export interface GoodsJson {
  itemType: string;
  itemMake?: string;
  description?: string;
  presentlyRegisteredTo?: string;
  statusCode?: string;
  estimatedValue: number;
  currencyCode?: string;
  size?: number;
  sizeUom?: string;
  registrationDate?: string;
  disposedValue?: number;
  statusComments?: string;
  registrationNumber?: string;
  identificationNumber?: string;
}

export interface DpmsrReportPayload {
  rentityBranch?: string;
  entityReference: string;
  submissionDate: string;
  fiuRefNumber?: string;
  reason?: string;
  action?: string;
  indicators?: string[];
  /** Optional: when omitted, the server fills the tenant's configured goAML person (MLRO). */
  reportingPerson?: ReportingPersonJson;
  location?: AddressJson;
  parties: PartyJson[];
  goods: GoodsJson[];
}
