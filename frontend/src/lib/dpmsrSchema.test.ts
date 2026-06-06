import { describe, expect, it } from 'vitest';
import { validateDpmsr } from './dpmsrSchema';

const validPayload = {
  entityReference: 'REF-1',
  submissionDate: '2026-06-06T00:00:00.000Z',
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
});
