import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';

/** UAE FIU — the only jurisdiction the platform validates against today. */
export const DEFAULT_JURISDICTION = 'ae';

export interface JurisdictionView {
  code: string;
  name: string;
  defaultCurrency: string;
  allowedReportTypes: string[];
  dpmsThreshold: number | null;
  lookupSet: string;
}

export interface LookupSetsView {
  jurisdiction: string;
  sets: string[];
}

export interface LookupSetView {
  jurisdiction: string;
  set: string;
  codes: string[];
}

const BASE = `${API_PREFIX}/lookups`;

/** Jurisdictions the platform validates against. */
export async function listJurisdictions(): Promise<JurisdictionView[]> {
  const { data } = await apiClient.get<JurisdictionView[]>(`${BASE}/jurisdictions`);
  return data;
}

/** The lookup-set names available for a jurisdiction (countries, currencies, …). */
export async function listLookupSets(jurisdiction = DEFAULT_JURISDICTION): Promise<LookupSetsView> {
  const { data } = await apiClient.get<LookupSetsView>(`${BASE}/${jurisdiction}`);
  return data;
}

/** The codes within one lookup set. */
export async function getLookupSet(
  set: string,
  jurisdiction = DEFAULT_JURISDICTION,
): Promise<LookupSetView> {
  const { data } = await apiClient.get<LookupSetView>(`${BASE}/${jurisdiction}/${set}`);
  return data;
}
