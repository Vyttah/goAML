import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';
import type { CreateReportResponse } from '../types';

/** A screened subject the AML screening software pushed — mirrors `ScreeningSubjectResponse`. */
export interface ScreeningSubjectView {
  subjectRef: string;
  subjectType: string;
  displayName: string;
  riskFlag: boolean;
  parties: unknown[];
  sanctionsContext: string | null;
}

/** The report-completing fields supplied when seeding a DPMSR from a subject — mirrors `ScreeningSeedRequest`. */
export interface ScreeningSeedRequest {
  entityReference: string;
  submissionDate: string;
  reason?: string;
  action?: string;
  indicators?: string[];
  reportingPerson: { firstName: string; lastName: string };
  goods: { itemType: string; estimatedValue: number; currencyCode?: string }[];
}

const BASE = `${API_PREFIX}/screening/subjects`;

/** All screened subjects in the caller's tenant. */
export async function listScreeningSubjects(): Promise<ScreeningSubjectView[]> {
  const { data } = await apiClient.get<ScreeningSubjectView[]>(BASE);
  return data;
}

/** One screened subject by its reference. */
export async function getScreeningSubject(subjectRef: string): Promise<ScreeningSubjectView> {
  const { data } = await apiClient.get<ScreeningSubjectView>(`${BASE}/${encodeURIComponent(subjectRef)}`);
  return data;
}

/** Seed a DPMSR draft from a screened subject (parties from the subject + supplied goods/report fields). */
export async function seedReportFromSubject(
  subjectRef: string,
  body: ScreeningSeedRequest,
): Promise<CreateReportResponse> {
  const { data } = await apiClient.post<CreateReportResponse>(
    `${BASE}/${encodeURIComponent(subjectRef)}/seed-report`,
    body,
  );
  return data;
}
