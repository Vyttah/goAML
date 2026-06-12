import { describe, expect, it } from 'vitest';
import { validateDpmsr } from './dpmsrSchema';

const validPayload = {
  entityReference: 'REF-1',
  submissionDate: '2026-06-06T00:00:00.000Z',
  indicators: ['ACTRC'],
  reportingPerson: { firstName: 'Jane', lastName: 'Roe' },
  parties: [{ person: { firstName: 'John', lastName: 'Doe' } }],
  goods: [{ itemType: 'Gold bar', estimatedValue: 60000 }],
};

describe('validateDpmsr', () => {
  it('accepts a minimal valid payload', () => {
    const result = validateDpmsr(validPayload);
    expect(result.ok).toBe(true);
  });

  it('flags a missing entity reference', () => {
    const result = validateDpmsr({ ...validPayload, entityReference: '' });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.issues.some((i) => i.path === 'entityReference')).toBe(true);
  });

  it('rejects a party that is neither a person nor an entity', () => {
    const result = validateDpmsr({ ...validPayload, parties: [{ reason: 'x' }] });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.issues.some((i) => i.path.startsWith('parties'))).toBe(true);
  });

  it('rejects a party that is both a person and an entity', () => {
    const result = validateDpmsr({
      ...validPayload,
      parties: [
        { person: { firstName: 'A', lastName: 'B' }, entity: { name: 'Acme' } },
      ],
    });
    expect(result.ok).toBe(false);
  });

  it('requires at least one goods item with a numeric estimated value', () => {
    expect(validateDpmsr({ ...validPayload, goods: [] }).ok).toBe(false);
    const missingValue = validateDpmsr({
      ...validPayload,
      goods: [{ itemType: 'Gold' }],
    });
    expect(missingValue.ok).toBe(false);
  });

  it('requires at least one report indicator', () => {
    const omitted = validateDpmsr({ ...validPayload, indicators: undefined });
    expect(omitted.ok).toBe(false);
    if (!omitted.ok) {
      expect(omitted.issues.some((i) => i.path === 'indicators')).toBe(true);
    }
    expect(validateDpmsr({ ...validPayload, indicators: [] }).ok).toBe(false);
  });

  it('accepts an omitted reporting person (the server fills the configured MLRO)', () => {
    expect(validateDpmsr({ ...validPayload, reportingPerson: undefined }).ok).toBe(true);
  });

  it('rejects a partially-filled reporting person (names must come together)', () => {
    const partial = validateDpmsr({ ...validPayload, reportingPerson: { firstName: 'Jane' } });
    expect(partial.ok).toBe(false);
    if (!partial.ok) {
      expect(partial.issues.some((i) => i.path === 'reportingPerson')).toBe(true);
    }
    expect(
      validateDpmsr({ ...validPayload, reportingPerson: { occupation: 'MLRO' } }).ok,
    ).toBe(false);
  });
});
