import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs';
import { buildDpmsrPayload } from './dpmsrForm';

describe('buildDpmsrPayload', () => {
  it('converts Dayjs dates to ISO strings', () => {
    const payload = buildDpmsrPayload({
      entityReference: 'REF',
      submissionDate: dayjs('2026-06-06T00:00:00.000Z'),
      reportingPerson: { firstName: 'A', lastName: 'B' },
      parties: [],
      goods: [],
    }) as { submissionDate: string };
    expect(payload.submissionDate).toBe('2026-06-06T00:00:00.000Z');
  });

  it('keeps only the selected party branch and drops the _type flag', () => {
    const payload = buildDpmsrPayload({
      parties: [
        { _type: 'person', person: { firstName: 'J', lastName: 'D' }, entity: { name: 'ignored' } },
        { _type: 'entity', entity: { name: 'Acme' }, person: { firstName: 'x', lastName: 'y' } },
      ],
    }) as { parties: Array<Record<string, unknown>> };

    expect(payload.parties[0]).toHaveProperty('person');
    expect(payload.parties[0]).not.toHaveProperty('entity');
    expect(payload.parties[0]).not.toHaveProperty('_type');
    expect(payload.parties[1]).toHaveProperty('entity');
    expect(payload.parties[1]).not.toHaveProperty('person');
  });

  it('prunes empty strings, nulls, and empty objects/arrays', () => {
    const payload = buildDpmsrPayload({
      entityReference: 'REF',
      reason: '',
      fiuRefNumber: '   ',
      reportingPerson: { firstName: 'A', lastName: 'B', occupation: '', phone: {} },
      parties: [],
      goods: [],
    }) as Record<string, unknown>;

    expect(payload).not.toHaveProperty('reason');
    expect(payload).not.toHaveProperty('fiuRefNumber');
    expect(payload).not.toHaveProperty('parties'); // empty array pruned
    const rp = payload.reportingPerson as Record<string, unknown>;
    expect(rp).not.toHaveProperty('occupation');
    expect(rp).not.toHaveProperty('phone');
  });

  it('preserves a zero estimated value', () => {
    const payload = buildDpmsrPayload({
      goods: [{ itemType: 'Gold', estimatedValue: 0 }],
    }) as { goods: Array<Record<string, unknown>> };
    expect(payload.goods[0].estimatedValue).toBe(0);
  });
});
