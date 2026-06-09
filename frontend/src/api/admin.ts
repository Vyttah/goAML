import { apiClient, httpStatus } from './client';
import { API_PREFIX } from '../lib/config';
import type {
  CreateUserRequest,
  GoamlConfigRequest,
  GoamlConfigView,
  GoamlPersonRequest,
  GoamlPersonView,
  TenantProvisioningRequest,
  TenantView,
  UserView,
} from '../types';

const BASE = `${API_PREFIX}/admin`;

// ----- tenants (SUPER_ADMIN) -----

export async function listTenants(): Promise<TenantView[]> {
  const { data } = await apiClient.get<TenantView[]>(`${BASE}/tenants`);
  return data;
}

export async function provisionTenant(body: TenantProvisioningRequest): Promise<TenantView> {
  const { data } = await apiClient.post<TenantView>(`${BASE}/tenants`, body);
  return data;
}

// ----- users in the caller's tenant (TENANT_ADMIN) -----

export async function listUsers(): Promise<UserView[]> {
  const { data } = await apiClient.get<UserView[]>(`${BASE}/users`);
  return data;
}

export async function createUser(body: CreateUserRequest): Promise<UserView> {
  const { data } = await apiClient.post<UserView>(`${BASE}/users`, body);
  return data;
}

// ----- goAML config for the caller's tenant (TENANT_ADMIN) -----

/** Returns null when the tenant has no goAML config yet (404), so the UI shows an empty form. */
export async function getGoamlConfig(): Promise<GoamlConfigView | null> {
  try {
    const { data } = await apiClient.get<GoamlConfigView>(`${BASE}/goaml-config`);
    return data;
  } catch (err) {
    if (httpStatus(err) === 404) return null;
    throw err;
  }
}

export async function upsertGoamlConfig(body: GoamlConfigRequest): Promise<GoamlConfigView> {
  const { data } = await apiClient.put<GoamlConfigView>(`${BASE}/goaml-config`, body);
  return data;
}

// ----- goAML reporting persons for the caller's tenant (TENANT_ADMIN) -----
// The active person is the default goAML auto-injects into every report.

export async function listGoamlPersons(): Promise<GoamlPersonView[]> {
  const { data } = await apiClient.get<GoamlPersonView[]>(`${BASE}/goaml-persons`);
  return data;
}

export async function createGoamlPerson(body: GoamlPersonRequest): Promise<GoamlPersonView> {
  const { data } = await apiClient.post<GoamlPersonView>(`${BASE}/goaml-persons`, body);
  return data;
}

export async function updateGoamlPerson(id: string, body: GoamlPersonRequest): Promise<GoamlPersonView> {
  const { data } = await apiClient.put<GoamlPersonView>(`${BASE}/goaml-persons/${id}`, body);
  return data;
}

export async function deleteGoamlPerson(id: string): Promise<void> {
  await apiClient.delete(`${BASE}/goaml-persons/${id}`);
}
