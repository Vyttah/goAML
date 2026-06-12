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

/** Form fields that hold a Dayjs value and must be rehydrated on restore. */
const DATE_FIELDS = ['submissionDate'] as const;

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
 * Read a saved draft, rehydrating known date fields back to Dayjs. Returns null when there is no
 * draft or it can't be parsed (treated as no draft).
 */
export function loadDpmsrDraft(): Record<string, unknown> | null {
  const raw = safeSessionStorage()?.getItem(DRAFT_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (!parsed || typeof parsed !== 'object') return null;
    for (const field of DATE_FIELDS) {
      const value = parsed[field];
      if (typeof value === 'string') {
        const d = dayjs(value);
        if (d.isValid()) parsed[field] = d;
      }
    }
    return parsed;
  } catch {
    return null;
  }
}
