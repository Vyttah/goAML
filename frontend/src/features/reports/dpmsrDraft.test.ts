import { afterEach, describe, expect, it } from 'vitest';
import dayjs, { type Dayjs } from 'dayjs';
import { clearDpmsrDraft, loadDpmsrDraft, saveDpmsrDraft } from './dpmsrDraft';

afterEach(() => {
  clearDpmsrDraft();
  sessionStorage.clear();
});

/** Walk an object path (the drafts are deeply nested untyped JSON). */
function at(value: unknown, path: (string | number)[]): unknown {
  return path.reduce<unknown>(
    (acc, key) => (acc as Record<string | number, unknown>)[key],
    value,
  );
}

function expectDayjsAt(draft: unknown, path: (string | number)[], date: string) {
  const value = at(draft, path);
  expect(dayjs.isDayjs(value)).toBe(true);
  expect((value as Dayjs).format('YYYY-MM-DD')).toBe(date);
}

describe('dpmsrDraft', () => {
  it('round-trips nested Dayjs values (party birthdate + identification dates) back to Dayjs', () => {
    saveDpmsrDraft({
      entityReference: 'REF-1',
      submissionDate: dayjs('2026-06-06'),
      parties: [
        {
          _type: 'person',
          person: {
            firstName: 'John',
            lastName: 'Doe',
            birthdate: dayjs('1990-05-01'),
            identifications: [
              { type: 'PASS', issueDate: dayjs('2020-01-02'), expiryDate: dayjs('2030-01-02') },
            ],
          },
        },
        {
          _type: 'entity',
          entity: {
            name: 'Acme',
            incorporationDate: dayjs('2015-03-09'),
            directors: [{ firstName: 'D', lastName: 'IR', birthdate: dayjs('1980-12-31') }],
          },
        },
      ],
      goods: [{ itemType: 'GOLD', registrationDate: dayjs('2026-01-15') }],
    });

    const draft = loadDpmsrDraft();
    expect(draft).not.toBeNull();

    expectDayjsAt(draft, ['submissionDate'], '2026-06-06');
    expectDayjsAt(draft, ['parties', 0, 'person', 'birthdate'], '1990-05-01');
    expectDayjsAt(draft, ['parties', 0, 'person', 'identifications', 0, 'issueDate'], '2020-01-02');
    expectDayjsAt(draft, ['parties', 0, 'person', 'identifications', 0, 'expiryDate'], '2030-01-02');
    expectDayjsAt(draft, ['parties', 1, 'entity', 'incorporationDate'], '2015-03-09');
    expectDayjsAt(draft, ['parties', 1, 'entity', 'directors', 0, 'birthdate'], '1980-12-31');
    expectDayjsAt(draft, ['goods', 0, 'registrationDate'], '2026-01-15');
  });

  it('leaves date-less strings untouched at any depth', () => {
    saveDpmsrDraft({
      entityReference: 'REF-2026',
      parties: [{ _type: 'person', person: { firstName: 'John', nationality: 'AE' } }],
    });

    const draft = loadDpmsrDraft();
    expect(at(draft, ['entityReference'])).toBe('REF-2026');
    expect(at(draft, ['parties', 0, 'person', 'firstName'])).toBe('John');
    expect(at(draft, ['parties', 0, 'person', 'nationality'])).toBe('AE');
    expect(at(draft, ['parties', 0, '_type'])).toBe('person');
  });

  it('returns null when there is no draft or it cannot be parsed', () => {
    expect(loadDpmsrDraft()).toBeNull();
    sessionStorage.setItem('goaml.dpmsrDraft', '{not json');
    expect(loadDpmsrDraft()).toBeNull();
  });
});
