import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';
import type {
  CreateReportResponse,
  ReportView,
  ReviewView,
  StatusView,
  SubmissionView,
} from '../types';
import type { DpmsrReportPayload } from '../types/dpmsrPayload';

const BASE = `${API_PREFIX}/reports`;

/** List the tenant's reports (newest-first ordering is the backend's). */
export async function listReports(): Promise<ReportView[]> {
  const { data } = await apiClient.get<ReportView[]>(BASE);
  return data;
}

/** Fetch a single report summary. */
export async function getReport(id: string): Promise<ReportView> {
  const { data } = await apiClient.get<ReportView>(`${BASE}/${id}`);
  return data;
}

/** Create + validate a DPMSR report (full-fidelity payload). Returns the validation outcome. */
export async function createReport(body: DpmsrReportPayload): Promise<CreateReportResponse> {
  const { data } = await apiClient.post<CreateReportResponse>(BASE, body);
  return data;
}

/** Fetch the marshalled goAML XML for a report (served as application/xml, read as text). */
export async function getReportXml(id: string): Promise<string> {
  const { data } = await apiClient.get(`${BASE}/${id}/xml`, { responseType: 'text' });
  return data as string;
}

/** Submit a report to the FIU (MLRO only — backend enforces). */
export async function submitReport(id: string): Promise<SubmissionView> {
  const { data } = await apiClient.post<SubmissionView>(`${BASE}/${id}/submit`, {});
  return data;
}

/** Refresh + return the latest FIU status for a report. */
export async function getReportStatus(id: string): Promise<StatusView> {
  const { data } = await apiClient.get<StatusView>(`${BASE}/${id}/status`);
  return data;
}

// ---- Review gate (Phase D.2, opt-in per tenant) ----------------------------------------------

/** Reports awaiting MLRO review (PENDING_REVIEW) for this tenant — the review queue. */
export async function getReviewQueue(): Promise<ReportView[]> {
  const { data } = await apiClient.get<ReportView[]>(`${BASE}/review-queue`);
  return data;
}

/** Submit a VALID report into the review queue (ANALYST or MLRO). */
export async function submitReportForReview(id: string, remark?: string): Promise<ReviewView> {
  const { data } = await apiClient.post<ReviewView>(`${BASE}/${id}/submit-for-review`, { remark });
  return data;
}

/** Approve a PENDING_REVIEW report (MLRO). */
export async function approveReport(id: string, remark?: string): Promise<ReviewView> {
  const { data } = await apiClient.post<ReviewView>(`${BASE}/${id}/approve`, { remark });
  return data;
}

/** Reject a PENDING_REVIEW report back to VALID (MLRO) — a remark is required. */
export async function rejectReport(id: string, remark: string): Promise<ReviewView> {
  const { data } = await apiClient.post<ReviewView>(`${BASE}/${id}/reject`, { remark });
  return data;
}
