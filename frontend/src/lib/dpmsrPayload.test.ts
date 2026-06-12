import { describe, expect, it } from 'vitest';
import { toDpmsrPayload } from './dpmsrPayload';
import type { DpmsrCreateRequest } from '../types/dpmsr';

const base = {
  entityReference: 'REF',
  submissionDate: '2026-06-06T00:00:00.000Z',
  reportingPerson: { firstName: 'A', lastName: 'B' },
};

describe('toDpmsrPayload', () => {
  it('maps a person party to the plain person slot and wraps phone with schema field names', () => {
    const req: DpmsrCreateRequest = {
      ...base,
      reportingPerson: { firstName: 'A', lastName: 'B', nationality: 'AE', ssn: '784', phone: { number: '050' } },
      parties: [{ reason: 'Seller', person: { firstName: 'J', lastName: 'D', passportNumber: 'P1' } }],
      goods: [{ itemType: 'GOLD', estimatedValue: 90000, disposedValue: 91000, registrationNumber: 'INV-1' }],
    };

    const out = toDpmsrPayload(req);

    expect(out.reportingPerson?.nationality1).toBe('AE');
    expect(out.reportingPerson?.ssn).toBe('784');
    expect(out.reportingPerson?.phones?.phone[0].tphNumber).toBe('050');
    expect(out.parties[0].person?.firstName).toBe('J');
    expect(out.parties[0].person?.passportNumber).toBe('P1');
    // UAE DPMSR activity-report parties are plain person/entity — never the *_my_client variants.
    expect(out.parties[0]).not.toHaveProperty('personMyClient');
    expect(out.goods[0].disposedValue).toBe(91000);
    expect(out.goods[0].registrationNumber).toBe('INV-1');
  });

  it('maps an entity party with directors to directorId and wraps the address', () => {
    const req: DpmsrCreateRequest = {
      ...base,
      parties: [
        {
          reason: 'Buyer',
          entity: {
            name: 'Acme',
            incorporationDate: '2023-06-05T00:00:00.000Z',
            address: { address: 'AL RAS', city: 'DUBAI', countryCode: 'AE' },
            directors: [{ firstName: 'D', lastName: 'IR', ssn: '784198', role: 'PRTNR' }],
          },
        },
      ],
      goods: [{ itemType: 'GOLD', estimatedValue: 90000 }],
    };

    const out = toDpmsrPayload(req);
    const e = out.parties[0].entity!;

    expect(e.incorporationDate).toBe('2023-06-05T00:00:00.000Z');
    expect(e.addresses?.address[0].address).toBe('AL RAS');
    expect(e.directorId?.[0].ssn).toBe('784198');
    expect(e.directorId?.[0].role).toBe('PRTNR');
    expect(out.parties[0]).not.toHaveProperty('person');
    expect(out.parties[0]).not.toHaveProperty('entityMyClient');
  });

  it('prunes empty wrappers and undefined fields but preserves a zero value', () => {
    const req: DpmsrCreateRequest = {
      ...base,
      parties: [{ person: { firstName: 'J', lastName: 'D' } }],
      goods: [{ itemType: 'GOLD', estimatedValue: 0 }],
    };

    const out = toDpmsrPayload(req);

    expect(out.reportingPerson).not.toHaveProperty('phones');
    expect(out.reportingPerson).not.toHaveProperty('ssn');
    expect(out.parties[0].person).not.toHaveProperty('identification');
    expect(out.goods[0].estimatedValue).toBe(0);
  });

  it('omits reportingPerson entirely when not supplied (server fills the configured MLRO)', () => {
    const req: DpmsrCreateRequest = {
      entityReference: 'REF',
      submissionDate: '2026-06-06T00:00:00Z',
      parties: [{ person: { firstName: 'J', lastName: 'D' } }],
      goods: [{ itemType: 'GOLD', estimatedValue: 90000 }],
    };

    const out = toDpmsrPayload(req);

    expect(out).not.toHaveProperty('reportingPerson');
  });
});
