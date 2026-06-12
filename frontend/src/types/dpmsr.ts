/**
 * Mirror of `model.dto.report.DpmsrCreateRequest` and its nested records. This is the JSON the builder
 * POSTs to `POST /api/v1/reports`. DPMSR-fixed header values (submission_code E, report_code DPMSR,
 * currency AED) and rentity_id are applied server-side and are NOT carried here.
 *
 * Optional fields are typed `?` (the backend treats absent = not provided). Timestamps are ISO-8601
 * strings (OffsetDateTime); money is sent as a number (Jackson → BigDecimal).
 */

export interface DpmsrPhone {
  contactType?: string;
  communicationType?: string;
  countryPrefix?: string;
  number?: string;
}

export interface DpmsrAddress {
  addressType?: string;
  address?: string;
  city?: string;
  countryCode?: string;
  state?: string;
}

export interface DpmsrIdentification {
  type?: string;
  number?: string;
  issueDate?: string;
  expiryDate?: string;
  issueCountry?: string;
}

export interface DpmsrPerson {
  gender?: string;
  firstName: string;
  lastName: string;
  birthdate?: string;
  nationality?: string;
  residence?: string;
  idNumber?: string;
  ssn?: string;
  passportNumber?: string;
  passportCountry?: string;
  taxRegNumber?: string;
  occupation?: string;
  phone?: DpmsrPhone;
  address?: DpmsrAddress;
  identifications?: DpmsrIdentification[];
}

/**
 * The reporting person (MLRO) — same shape as {@link DpmsrPerson} but with optional names: the whole
 * section may be omitted (the server then fills the tenant's configured goAML person). The Zod mirror
 * enforces names-together coherence when the section is filled at all.
 */
export type DpmsrReportingPerson = Omit<DpmsrPerson, 'firstName' | 'lastName'> & {
  firstName?: string;
  lastName?: string;
};

export interface DpmsrDirector {
  gender?: string;
  firstName: string;
  lastName: string;
  birthdate?: string;
  passportNumber?: string;
  passportCountry?: string;
  idNumber?: string;
  ssn?: string;
  nationality?: string;
  residence?: string;
  role?: string;
  phone?: DpmsrPhone;
  address?: DpmsrAddress;
  identifications?: DpmsrIdentification[];
}

export interface DpmsrEntity {
  name: string;
  commercialName?: string;
  incorporationNumber?: string;
  incorporationState?: string;
  incorporationCountryCode?: string;
  incorporationDate?: string;
  phone?: DpmsrPhone;
  address?: DpmsrAddress;
  directors?: DpmsrDirector[];
}

/** Exactly one of `entity` / `person` is set. */
export interface DpmsrParty {
  reason?: string;
  comments?: string;
  entity?: DpmsrEntity;
  person?: DpmsrPerson;
}

export interface DpmsrGoods {
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

export interface DpmsrCreateRequest {
  rentityBranch?: string;
  entityReference: string;
  submissionDate: string;
  fiuRefNumber?: string;
  reason?: string;
  action?: string;
  indicators?: string[];
  /** Optional: when omitted, the server fills the tenant's configured goAML person (MLRO). */
  reportingPerson?: DpmsrReportingPerson;
  location?: DpmsrAddress;
  parties: DpmsrParty[];
  goods: DpmsrGoods[];
}
