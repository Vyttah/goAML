import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';
import type { LoginRequest, LoginResponse } from '../types';

/** Authenticate with email/password → access token. Throws (axios) on 401 bad credentials. */
export async function login(body: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>(`${API_PREFIX}/auth/login`, body);
  return data;
}
