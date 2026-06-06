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

// ---- Imports ---------------------------------------------------------------------------------

/** One row's outcome within an import job — mirrors `service.ingestion.ImportRowResult`. */
export interface ImportRowResult {
  row: number;
  entityReference: string | null;
  status: string; // VALID | INVALID | FAILED
  reportId: string | null;
  errors: string[];
}

/** An import job + its per-row results — mirrors `model.dto.ingestion.ImportJobView`. */
export interface ImportJobView {
  id: string;
  sourceType: string; // GOAML_XML | DPMSR_CSV
  filename: string;
  status: string; // COMPLETED | PARTIAL | FAILED
  totalRows: number;
  succeeded: number;
  failed: number;
  results: ImportRowResult[];
  createdAt: string;
}

// ---- Notifications ---------------------------------------------------------------------------

/** In-app notification — mirrors `model.dto.notification.NotificationView`. `readAt == null` = unread. */
export interface NotificationView {
  id: string;
  type: string;
  reportId: string | null;
  title: string;
  body: string;
  readAt: string | null;
  createdAt: string;
}

// ---- Admin -----------------------------------------------------------------------------------

/** Tenant summary — mirrors `AdminViews.TenantView`. */
export interface TenantView {
  id: string;
  slug: string;
  name: string;
  jurisdictionCode: string;
  schemaName: string;
  status: string;
  createdAt: string;
}

/** Provision a tenant — mirrors `model.dto.tenant.TenantProvisioningRequest`. */
export interface TenantProvisioningRequest {
  slug: string;
  name: string;
  jurisdictionCode: string;
  adminEmail: string;
  adminPassword: string;
  adminFirstName: string;
  adminLastName: string;
}

/** Create a user in the caller's tenant — mirrors `AdminViews.CreateUserRequest`. */
export interface CreateUserRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role: string; // ANALYST | MLRO | TENANT_ADMIN
}

/** User summary — mirrors `AdminViews.UserView`. */
export interface UserView {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: string;
  roles: string[];
  createdAt: string;
}

/** goAML B2B config upsert — mirrors `AdminViews.GoamlConfigRequest`. */
export interface GoamlConfigRequest {
  jurisdictionCode: string;
  rentityId: number;
  baseUrl: string;
  secretsPath: string;
  authMode: string; // TOKEN | BASIC
}

/** goAML B2B config view — mirrors `AdminViews.GoamlConfigView`. */
export interface GoamlConfigView {
  tenantId: string;
  jurisdictionCode: string;
  rentityId: number;
  baseUrl: string;
  secretsPath: string;
  authMode: string;
  updatedAt: string;
}
