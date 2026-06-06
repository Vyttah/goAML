/**
 * TypeScript mirrors of backend DTOs. This file holds the cross-cutting/auth types established in
 * 13.3; each feature step adds its own area's types (reports, lookups, imports, admin, …) alongside.
 * The backend remains the source of truth — keep these in lockstep with the Java records.
 */

/** `POST /api/v1/auth/login` request — mirrors `model.dto.auth.LoginRequest`. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** `POST /api/v1/auth/login` response — mirrors `model.dto.auth.LoginResponse`. */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

// ---- Reports ---------------------------------------------------------------------------------

/** Report lifecycle statuses (backend `Report.status` / submission outcomes). */
export const REPORT_STATUSES = [
  'DRAFT',
  'VALID',
  'INVALID',
  'SUBMITTED',
  'ACCEPTED',
  'REJECTED',
  'FAILED',
] as const;

export type ReportStatus = (typeof REPORT_STATUSES)[number];

/** A single validation finding — mirrors `engine.validation.ValidationMessage`. */
export interface ValidationMessage {
  severity: 'ERROR' | 'WARNING';
  path: string;
  code: string;
  message: string;
}

/** Report summary for list/get — mirrors `ReportResponses.ReportView`. */
export interface ReportView {
  id: string;
  entityReference: string;
  reportCode: string;
  status: string;
  rentityId: number | null;
  createdAt: string;
}

/** Create/validate result — mirrors `ReportResponses.CreateReportResponse`. */
export interface CreateReportResponse {
  reportId: string;
  status: string;
  validationMessages: ValidationMessage[];
}

/** FIU submission result — mirrors `ReportResponses.SubmissionView`. */
export interface SubmissionView {
  submissionId: string;
  reportKey: string | null;
  status: string;
}

/** Latest FIU status for a report — mirrors `ReportResponses.StatusView`. */
export interface StatusView {
  reportKey: string | null;
  status: string;
  errors: string | null;
}

/** Report attachment metadata — mirrors `model.dto.attachment.AttachmentView`. */
export interface AttachmentView {
  id: string;
  reportId: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  createdAt: string;
}
