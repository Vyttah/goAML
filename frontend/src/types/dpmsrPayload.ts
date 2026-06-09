/**
 * Mirror of the backend's full-fidelity `DpmsrReportPayload` (which binds the xjc-generated goAML types).
 * This is the JSON the builder POSTs to `POST /api/v1/reports`. Unlike the curated `DpmsrCreateRequest`,
 * it uses the schema's wrapper shape (`phones: { phone: [...] }`, `addresses: { address: [...] }`,
 * `directorId: [...]`, inline `identification: [...]`) and the schema property names (`nationality1`,
 * `tphNumber`, `personMyClient`). The `lib/dpmsrPayload.ts` adapter maps the flat form values onto this.
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

/** t_person_registration_in_report (the reporting person / MLRO). No identifications block. */
export interface ReportingPersonJson {
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
  occupation?: string;
  phones?: PhonesWrapper;
  addresses?: AddressesWrapper;
}

/** t_person_my_client (a person party). Uses the inline `identification` list. */
export interface PersonMyClientJson {
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

/** report_party_type — exactly one of entity / personMyClient is set. */
export interface PartyJson {
  reason?: string;
  comments?: string;
  entity?: EntityJson;
  personMyClient?: PersonMyClientJson;
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
  reportingPerson: ReportingPersonJson;
  location?: AddressJson;
  parties: PartyJson[];
  goods: GoodsJson[];
}
