import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';
import type { AttachmentView } from '../types';

const base = (reportId: string) => `${API_PREFIX}/reports/${reportId}/attachments`;

/** List a report's attachments (metadata only — bytes stay in S3, fetched on demand by downloadAttachment). */
export async function listAttachments(reportId: string): Promise<AttachmentView[]> {
  const { data } = await apiClient.get<AttachmentView[]>(base(reportId));
  return data;
}

/** Fetch an attachment's bytes (proxied from S3 through the API) as a Blob for download. */
export async function downloadAttachment(reportId: string, attachmentId: string): Promise<Blob> {
  const { data } = await apiClient.get(`${base(reportId)}/${attachmentId}/content`, {
    responseType: 'blob',
  });
  return data as Blob;
}

/** Upload an attachment (multipart, proxied through the API to S3). */
export async function addAttachment(reportId: string, file: File): Promise<AttachmentView> {
  const form = new FormData();
  form.append('file', file);
  // Clear the instance's JSON default so axios sets multipart/form-data with the correct boundary.
  const { data } = await apiClient.post<AttachmentView>(base(reportId), form, {
    headers: { 'Content-Type': undefined },
  });
  return data;
}

/** Remove an attachment. */
export async function removeAttachment(reportId: string, attachmentId: string): Promise<void> {
  await apiClient.delete(`${base(reportId)}/${attachmentId}`);
}
