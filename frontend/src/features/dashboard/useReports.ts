import { useQuery } from '@tanstack/react-query';
import { listReports } from '../../api/reports';

/** Query key for the report list (mutations elsewhere invalidate this). */
export const reportsQueryKey = ['reports'] as const;

/** Load the tenant's reports. */
export function useReports() {
  return useQuery({ queryKey: reportsQueryKey, queryFn: listReports });
}
