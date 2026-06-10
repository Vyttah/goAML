import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getReport, getReportDetail, getReportStatus, submitReport } from '../../api/reports';
import { addAttachment, listAttachments, removeAttachment } from '../../api/attachments';
import { reportsQueryKey } from '../dashboard/useReports';
import type { StatusView } from '../../types';

export const reportQueryKey = (id: string) => ['report', id] as const;
export const reportDetailQueryKey = (id: string) => ['report-detail', id] as const;
export const attachmentsQueryKey = (id: string) => ['attachments', id] as const;

/** Single report summary. */
export function useReport(id: string) {
  return useQuery({ queryKey: reportQueryKey(id), queryFn: () => getReport(id), enabled: !!id });
}

/** Full read view (Phase D.3): summary + stored input + validation + review trail. Drives the detail page. */
export function useReportFull(id: string) {
  return useQuery({ queryKey: reportDetailQueryKey(id), queryFn: () => getReportDetail(id), enabled: !!id });
}

/** Submit to the FIU (MLRO). Invalidates the report (summary + detail) + the dashboard list on success. */
export function useSubmitReport(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => submitReport(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reportQueryKey(id) });
      queryClient.invalidateQueries({ queryKey: reportDetailQueryKey(id) });
      queryClient.invalidateQueries({ queryKey: reportsQueryKey });
    },
  });
}

/** Refresh the latest FIU status on demand (only meaningful once submitted). */
export function useCheckStatus(id: string) {
  const queryClient = useQueryClient();
  return useMutation<StatusView, unknown, void>({
    mutationFn: () => getReportStatus(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reportQueryKey(id) });
      queryClient.invalidateQueries({ queryKey: reportDetailQueryKey(id) });
    },
  });
}

/** A report's attachments. */
export function useAttachments(id: string) {
  return useQuery({
    queryKey: attachmentsQueryKey(id),
    queryFn: () => listAttachments(id),
    enabled: !!id,
  });
}

export function useAddAttachment(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => addAttachment(id, file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: attachmentsQueryKey(id) }),
  });
}

export function useRemoveAttachment(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attachmentId: string) => removeAttachment(id, attachmentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: attachmentsQueryKey(id) }),
  });
}
