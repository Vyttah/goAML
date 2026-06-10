import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  approveReport,
  getReviewQueue,
  rejectReport,
  submitReportForReview,
} from '../../api/reports';
import { reportsQueryKey } from '../dashboard/useReports';
import { reportQueryKey } from './useReportDetail';

export const reviewQueueQueryKey = ['review-queue'] as const;

/** Reports awaiting MLRO review (PENDING_REVIEW) for the active tenant. */
export function useReviewQueue() {
  return useQuery({ queryKey: reviewQueueQueryKey, queryFn: getReviewQueue });
}

/** Invalidate everything a review decision can change: the queue, the dashboard list, and the report. */
function useInvalidateAfterReview() {
  const queryClient = useQueryClient();
  return (id: string) => {
    queryClient.invalidateQueries({ queryKey: reviewQueueQueryKey });
    queryClient.invalidateQueries({ queryKey: reportsQueryKey });
    queryClient.invalidateQueries({ queryKey: reportQueryKey(id) });
  };
}

/** Submit a VALID report into the review queue (ANALYST or MLRO). */
export function useSubmitForReview(id: string) {
  const invalidate = useInvalidateAfterReview();
  return useMutation({
    mutationFn: (remark?: string) => submitReportForReview(id, remark),
    onSuccess: () => invalidate(id),
  });
}

/** Approve a PENDING_REVIEW report (MLRO). */
export function useApproveReport() {
  const invalidate = useInvalidateAfterReview();
  return useMutation({
    mutationFn: ({ id, remark }: { id: string; remark?: string }) => approveReport(id, remark),
    onSuccess: (_data, { id }) => invalidate(id),
  });
}

/** Reject a PENDING_REVIEW report back to VALID (MLRO) — remark required. */
export function useRejectReport() {
  const invalidate = useInvalidateAfterReview();
  return useMutation({
    mutationFn: ({ id, remark }: { id: string; remark: string }) => rejectReport(id, remark),
    onSuccess: (_data, { id }) => invalidate(id),
  });
}
