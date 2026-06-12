import dayjs from 'dayjs';

/**
 * Turn raw AntD form values into the `DpmsrCreateRequest` JSON the backend expects:
 *  - Dayjs date values → the picked local calendar date pinned to UTC midnight
 *    (`YYYY-MM-DDT00:00:00Z`) so the date the user saw is the date that is filed — `.toISOString()`
 *    would shift a UAE (+04:00) local-midnight birthdate to the previous day 20:00Z,
 *  - each party reduced to its selected branch (person XOR entity) via the UI-only `_type` flag,
 *  - empty strings / nulls / empty objects + arrays pruned so absent = not provided.
 *
 * Returns `unknown` — the caller validates the result with the Zod mirror before POSTing.
 */

function isEmpty(value: unknown): boolean {
  if (value === undefined || value === null || value === '') return true;
  if (Array.isArray(value)) return value.length === 0;
  if (dayjs.isDayjs(value)) return false;
  if (typeof value === 'object') return Object.keys(value as object).length === 0;
  return false;
}

function deepNormalize(value: unknown): unknown {
  if (dayjs.isDayjs(value)) return `${value.format('YYYY-MM-DD')}T00:00:00Z`;
  if (typeof value === 'string') return value.trim();
  if (Array.isArray(value)) {
    return value.map(deepNormalize).filter((v) => !isEmpty(v));
  }
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [key, raw] of Object.entries(value as Record<string, unknown>)) {
      const normalized = deepNormalize(raw);
      if (!isEmpty(normalized)) out[key] = normalized;
    }
    return out;
  }
  return value;
}

interface RawParty {
  reason?: unknown;
  comments?: unknown;
  person?: unknown;
  entity?: unknown;
  _type?: unknown;
}

/** Build the create payload from raw form values (pre-Zod). */
export function buildDpmsrPayload(values: Record<string, unknown>): unknown {
  const parties = Array.isArray(values.parties) ? (values.parties as RawParty[]) : [];
  const mappedParties = parties.map((p) => {
    const base: Record<string, unknown> = { reason: p.reason, comments: p.comments };
    if (p._type === 'entity') {
      base.entity = p.entity;
    } else {
      base.person = p.person;
    }
    return base;
  });

  return deepNormalize({ ...values, parties: mappedParties });
}
