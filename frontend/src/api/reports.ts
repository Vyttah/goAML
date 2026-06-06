import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';
import type { ReportView, StatusView, SubmissionView } from '../types';

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

// createReport (DPMSR builder) lands in 13.6 alongside the DpmsrCreateRequest type.

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
