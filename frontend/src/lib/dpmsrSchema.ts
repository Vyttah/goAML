import { z } from 'zod';

/**
 * Client-side Zod mirror of the backend's `DpmsrCreateRequest` constraints (`@NotBlank`/`@NotNull` +
 * the "a party is a person XOR an entity" rule). This is UX validation only — the server's
 * `ValidationResult` (XSD + business rules) remains authoritative and is rendered inline after create.
 */
const nonEmpty = z.string().trim().min(1, 'Required');
const optStr = z.string().trim().min(1).optional();

const phoneSchema = z
  .object({
    contactType: optStr,
    communicationType: optStr,
    countryPrefix: optStr,
    number: optStr,
  })
  .optional();

const addressSchema = z
  .object({
    addressType: optStr,
    address: optStr,
    city: optStr,
    countryCode: optStr,
    state: optStr,
  })
  .optional();

const identificationSchema = z.object({
  type: optStr,
  number: optStr,
  issueDate: optStr,
  expiryDate: optStr,
  issueCountry: optStr,
});

const personSchema = z.object({
  gender: optStr,
  firstName: nonEmpty,
  lastName: nonEmpty,
  birthdate: optStr,
  nationality: optStr,
  residence: optStr,
  idNumber: optStr,
  ssn: optStr,
  passportNumber: optStr,
  passportCountry: optStr,
  taxRegNumber: optStr,
  occupation: optStr,
  phone: phoneSchema,
  address: addressSchema,
  identifications: z.array(identificationSchema).optional(),
});

const directorSchema = z.object({
  gender: optStr,
  firstName: nonEmpty,
  lastName: nonEmpty,
  birthdate: optStr,
  passportNumber: optStr,
  passportCountry: optStr,
  idNumber: optStr,
  ssn: optStr,
  nationality: optStr,
  residence: optStr,
  role: optStr,
  phone: phoneSchema,
  address: addressSchema,
  identifications: z.array(identificationSchema).optional(),
});

const entitySchema = z.object({
  name: nonEmpty,
  commercialName: optStr,
  incorporationNumber: optStr,
  incorporationState: optStr,
  incorporationCountryCode: optStr,
  incorporationDate: optStr,
  phone: phoneSchema,
  address: addressSchema,
  directors: z.array(directorSchema).optional(),
});

const partySchema = z
  .object({
    reason: optStr,
    comments: optStr,
    entity: entitySchema.optional(),
    person: personSchema.optional(),
  })
  .refine((p) => Boolean(p.person) !== Boolean(p.entity), {
    message: 'Each party must be either a person or an entity',
  });

const goodsSchema = z.object({
  itemType: nonEmpty,
  itemMake: optStr,
  description: optStr,
  presentlyRegisteredTo: optStr,
  statusCode: optStr,
  estimatedValue: z.number({ invalid_type_error: 'Estimated value is required' }),
  currencyCode: optStr,
  size: z.number().optional(),
  sizeUom: optStr,
  registrationDate: optStr,
  disposedValue: z.number().optional(),
  statusComments: optStr,
  registrationNumber: optStr,
  identificationNumber: optStr,
});

export const dpmsrSchema = z.object({
  rentityBranch: optStr,
  entityReference: nonEmpty,
  submissionDate: nonEmpty,
  fiuRefNumber: optStr,
  reason: optStr,
  action: optStr,
  indicators: z.array(z.string()).optional(),
  reportingPerson: personSchema,
  location: addressSchema,
  parties: z.array(partySchema).min(1, 'At least one party is required'),
  goods: z.array(goodsSchema).min(1, 'At least one goods item is required'),
});

export type DpmsrSchemaInput = z.input<typeof dpmsrSchema>;

/** Validate a built payload against the mirror. Returns flat `path → message` issues on failure. */
export function validateDpmsr(payload: unknown):
  | { ok: true; data: z.infer<typeof dpmsrSchema> }
  | { ok: false; issues: { path: string; message: string }[] } {
  const result = dpmsrSchema.safeParse(payload);
  if (result.success) {
    return { ok: true, data: result.data };
  }
  return {
    ok: false,
    issues: result.error.issues.map((i) => ({ path: i.path.join('.'), message: i.message })),
  };
}
