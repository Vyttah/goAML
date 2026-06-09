import type {
  DpmsrAddress,
  DpmsrCreateRequest,
  DpmsrDirector,
  DpmsrEntity,
  DpmsrIdentification,
  DpmsrPerson,
  DpmsrPhone,
} from '../types/dpmsr';
import type {
  AddressesWrapper,
  DirectorJson,
  DpmsrReportPayload,
  EntityJson,
  IdentificationJson,
  PartyJson,
  PersonMyClientJson,
  PhoneJson,
  PhonesWrapper,
  ReportingPersonJson,
} from '../types/dpmsrPayload';

/**
 * Adapts the curated flat form shape ({@link DpmsrCreateRequest}) onto the backend's full-fidelity
 * {@link DpmsrReportPayload} (the xjc wrapper shape). Without this, the schema's wrapper elements
 * (`phones`/`addresses`/`directorId`/inline `identification`) and the renamed fields (`nationality1`,
 * `tphNumber`, `personMyClient`) wouldn't bind — phones/addresses/directors would be silently dropped.
 *
 * The form is validated (Zod) on the curated shape first; this runs last, just before the POST.
 */

function phones(phone?: DpmsrPhone): PhonesWrapper | undefined {
  if (!phone) return undefined;
  const p: PhoneJson = {
    tphContactType: phone.contactType,
    tphCommunicationType: phone.communicationType,
    tphCountryPrefix: phone.countryPrefix,
    tphNumber: phone.number,
  };
  return { phone: [p] };
}

function addresses(address?: DpmsrAddress): AddressesWrapper | undefined {
  return address ? { address: [address] } : undefined;
}

function identifications(ids?: DpmsrIdentification[]): IdentificationJson[] | undefined {
  return ids && ids.length > 0 ? ids : undefined;
}

function reportingPerson(p: DpmsrPerson): ReportingPersonJson {
  return {
    gender: p.gender,
    firstName: p.firstName,
    lastName: p.lastName,
    birthdate: p.birthdate,
    ssn: p.ssn,
    passportNumber: p.passportNumber,
    passportCountry: p.passportCountry,
    nationality1: p.nationality,
    residence: p.residence,
    idNumber: p.idNumber,
    occupation: p.occupation,
    phones: phones(p.phone),
    addresses: addresses(p.address),
  };
}

function personMyClient(p: DpmsrPerson): PersonMyClientJson {
  return {
    gender: p.gender,
    firstName: p.firstName,
    lastName: p.lastName,
    birthdate: p.birthdate,
    ssn: p.ssn,
    passportNumber: p.passportNumber,
    passportCountry: p.passportCountry,
    nationality1: p.nationality,
    residence: p.residence,
    idNumber: p.idNumber,
    taxRegNumber: p.taxRegNumber,
    occupation: p.occupation,
    phones: phones(p.phone),
    addresses: addresses(p.address),
    identification: identifications(p.identifications),
  };
}

function director(d: DpmsrDirector): DirectorJson {
  return {
    gender: d.gender,
    firstName: d.firstName,
    lastName: d.lastName,
    birthdate: d.birthdate,
    ssn: d.ssn,
    passportNumber: d.passportNumber,
    passportCountry: d.passportCountry,
    idNumber: d.idNumber,
    nationality1: d.nationality,
    residence: d.residence,
    role: d.role,
    phones: phones(d.phone),
    addresses: addresses(d.address),
    identification: identifications(d.identifications),
  };
}

function entity(e: DpmsrEntity): EntityJson {
  return {
    name: e.name,
    commercialName: e.commercialName,
    incorporationNumber: e.incorporationNumber,
    incorporationState: e.incorporationState,
    incorporationCountryCode: e.incorporationCountryCode,
    incorporationDate: e.incorporationDate,
    phones: phones(e.phone),
    addresses: addresses(e.address),
    directorId: e.directors && e.directors.length > 0 ? e.directors.map(director) : undefined,
  };
}

function party(p: DpmsrCreateRequest['parties'][number]): PartyJson {
  return {
    reason: p.reason,
    comments: p.comments,
    entity: p.entity ? entity(p.entity) : undefined,
    personMyClient: p.person ? personMyClient(p.person) : undefined,
  };
}

/** Map the curated create request onto the full-fidelity wire payload. Empty wrappers are pruned. */
export function toDpmsrPayload(req: DpmsrCreateRequest): DpmsrReportPayload {
  return prune({
    rentityBranch: req.rentityBranch,
    entityReference: req.entityReference,
    submissionDate: req.submissionDate,
    fiuRefNumber: req.fiuRefNumber,
    reason: req.reason,
    action: req.action,
    indicators: req.indicators,
    reportingPerson: reportingPerson(req.reportingPerson),
    location: req.location,
    parties: (req.parties ?? []).map(party),
    goods: req.goods ?? [],
  });
}

/** Recursively drop undefined/null/empty values so absent = not provided over the wire. */
function prune<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map(prune).filter((v) => !isEmpty(v)) as unknown as T;
  }
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, raw] of Object.entries(value as Record<string, unknown>)) {
      const v = prune(raw);
      if (!isEmpty(v)) out[k] = v;
    }
    return out as T;
  }
  return value;
}

function isEmpty(value: unknown): boolean {
  if (value === undefined || value === null || value === '') return true;
  if (Array.isArray(value)) return value.length === 0;
  if (typeof value === 'object') return Object.keys(value as object).length === 0;
  return false;
}
