/**
 * DPMSR builder draft autosave. The access token is short-lived (15 min, no refresh), so a long
 * report build can be interrupted by an expiry/reload. We debounce-save the raw AntD form values to
 * sessionStorage so the work survives a reload within the tab, restore them on mount, and clear them
 * once the report is created. sessionStorage (not localStorage) keeps the draft tab-scoped and gone
 * when the tab closes. Dayjs values are stored as ISO strings and rehydrated to Dayjs on restore so
 * the DatePicker fields round-trip.
 */
import dayjs from 'dayjs';

const DRAFT_KEY = 'goaml.dpmsrDraft';

/**
 * An ISO-8601 datetime as produced by `JSON.stringify(dayjs)` (Dayjs#toJSON → toISOString). Date-less
 * strings (names, codes, references) never match, so they round-trip untouched.
 */
const ISO_DATETIME_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;

/**
 * Deep-walk a parsed draft and rehydrate every ISO datetime string back to Dayjs, at any depth. The
 * DPMSR form has nested DatePickers everywhere (party birthdates, identification issue/expiry dates,
 * entity incorporation dates, goods registration dates) — AntD v5 DatePickers throw at render if fed
 * the raw strings.
 */
function rehydrateDates(value: unknown): unknown {
  if (typeof value === 'string' && ISO_DATETIME_RE.test(value)) {
    const d = dayjs(value);
    return d.isValid() ? d : value;
  }
  if (Array.isArray(value)) return value.map(rehydrateDates);
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [key, raw] of Object.entries(value as Record<string, unknown>)) {
      out[key] = rehydrateDates(raw);
    }
    return out;
  }
  return value;
}

function safeSessionStorage(): Storage | null {
  try {
    return typeof sessionStorage !== 'undefined' ? sessionStorage : null;
  } catch {
    return null;
  }
}

/** Persist the current form values (debounce in the caller). No-op if storage is unavailable. */
export function saveDpmsrDraft(values: Record<string, unknown>): void {
  try {
    safeSessionStorage()?.setItem(DRAFT_KEY, JSON.stringify(values));
  } catch {
    // Quota/serialization failures are non-fatal — autosave is best-effort.
  }
}

/** Drop the saved draft (called after a successful create). */
export function clearDpmsrDraft(): void {
  safeSessionStorage()?.removeItem(DRAFT_KEY);
}

/**
 * Read a saved draft, rehydrating date fields (at any nesting depth) back to Dayjs. Returns null when
 * there is no draft or it can't be parsed (treated as no draft).
 */
export function loadDpmsrDraft(): Record<string, unknown> | null {
  const raw = safeSessionStorage()?.getItem(DRAFT_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
    return rehydrateDates(parsed) as Record<string, unknown>;
  } catch {
    return null;
  }
}
