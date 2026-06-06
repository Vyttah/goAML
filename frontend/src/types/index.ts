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
