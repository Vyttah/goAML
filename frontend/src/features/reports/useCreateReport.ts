import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createReport } from '../../api/reports';
import { reportsQueryKey } from '../dashboard/useReports';
import type { CreateReportResponse } from '../../types';
import type { DpmsrCreateRequest } from '../../types/dpmsr';

/** Create a DPMSR report; invalidates the report list on success so the dashboard refreshes. */
export function useCreateReport() {
  const queryClient = useQueryClient();
  return useMutation<CreateReportResponse, unknown, DpmsrCreateRequest>({
    mutationFn: createReport,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reportsQueryKey });
    },
  });
}
