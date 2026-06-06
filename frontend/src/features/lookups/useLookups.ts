import { useQuery } from '@tanstack/react-query';
import {
  DEFAULT_JURISDICTION,
  getLookupSet,
  listJurisdictions,
  listLookupSets,
} from '../../api/lookups';

/** Codes for one lookup set (e.g. countries, currencies). Cached per (jurisdiction, set). */
export function useLookupCodes(set: string, jurisdiction = DEFAULT_JURISDICTION) {
  return useQuery({
    queryKey: ['lookups', jurisdiction, set],
    queryFn: () => getLookupSet(set, jurisdiction).then((r) => r.codes),
    enabled: Boolean(set) && Boolean(jurisdiction),
    staleTime: 5 * 60_000,
  });
}

/** All jurisdictions (for the lookups browser, 13.9). */
export function useJurisdictions() {
  return useQuery({ queryKey: ['lookups', 'jurisdictions'], queryFn: listJurisdictions });
}

/** Set names for a jurisdiction (for the lookups browser, 13.9). */
export function useLookupSets(jurisdiction = DEFAULT_JURISDICTION) {
  return useQuery({
    queryKey: ['lookups', jurisdiction, '__sets__'],
    queryFn: () => listLookupSets(jurisdiction),
    enabled: Boolean(jurisdiction),
  });
}
