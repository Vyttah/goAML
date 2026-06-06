import axios, { type AxiosError, type AxiosInstance } from 'axios';
import { API_BASE_URL } from '../lib/config';
import { clearToken, getToken } from '../auth/tokenStore';

/**
 * The shared axios instance for every backend call.
 *
 * Request interceptor  → attaches `Authorization: Bearer <token>` when one is present.
 * Response interceptor → on HTTP 401, clears the token and invokes the registered unauthorized
 *                        handler (the app wires this to a redirect-to-login). There is no refresh
 *                        token, so a 401 means "re-login".
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

let unauthorizedHandler: () => void = () => {};

/** Register what happens on a 401 (app wires this to a router redirect to /login). */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      clearToken();
      unauthorizedHandler();
    }
    return Promise.reject(error);
  },
);

/** A small normalized error the UI can render. The backend error body is `{status,error,message,...}`. */
export interface ApiErrorBody {
  status?: number;
  error?: string;
  message?: string;
  fiuError?: string;
  [key: string]: unknown;
}

/** Extract a human-readable message from an unknown thrown value (axios error or otherwise). */
export function errorMessage(err: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiErrorBody | undefined;
    if (body?.message) return body.message;
    if (err.message) return err.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}
