import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createGoamlPerson,
  createTenantExternalRef,
  createTenantUser,
  createTrustedService,
  createUser,
  deleteGoamlPerson,
  deleteTenantExternalRef,
  deleteTenantUser,
  deleteTrustedService,
  deleteUser,
  getGoamlConfig,
  listGoamlPersons,
  listTenantExternalRefs,
  listTenantUsers,
  listTenants,
  listTrustedServices,
  listUsers,
  provisionTenant,
  resetTenantUserPassword,
  updateGoamlPerson,
  updateTenantUser,
  updateTrustedService,
  updateUser,
  upsertGoamlConfig,
} from '../../api/admin';
import type {
  CreateUserRequest,
  GoamlPersonRequest,
  UpdateTrustedServiceRequest,
  UpdateUserRequest,
} from '../../types';

export const tenantsKey = ['admin', 'tenants'] as const;
export const usersKey = ['admin', 'users'] as const;
export const goamlConfigKey = ['admin', 'goaml-config'] as const;
export const goamlPersonsKey = ['admin', 'goaml-persons'] as const;
export const trustedServicesKey = ['admin', 'trusted-services'] as const;
export const tenantExternalRefsKey = ['admin', 'tenant-external-refs'] as const;

export function useTenants() {
  return useQuery({ queryKey: tenantsKey, queryFn: listTenants });
}

export function useProvisionTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: provisionTenant,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tenantsKey }),
  });
}

export function useUsers() {
  return useQuery({ queryKey: usersKey, queryFn: listUsers });
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: usersKey }),
  });
}

export function useUpdateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateUserRequest }) => updateUser(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: usersKey }),
  });
}

export function useDeleteUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: usersKey }),
  });
}

export function useGoamlConfig() {
  return useQuery({ queryKey: goamlConfigKey, queryFn: getGoamlConfig });
}

export function useUpsertGoamlConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: upsertGoamlConfig,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: goamlConfigKey }),
  });
}

export function useGoamlPersons() {
  return useQuery({ queryKey: goamlPersonsKey, queryFn: listGoamlPersons });
}

export function useCreateGoamlPerson() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createGoamlPerson,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: goamlPersonsKey }),
  });
}

export function useUpdateGoamlPerson() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: GoamlPersonRequest }) => updateGoamlPerson(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: goamlPersonsKey }),
  });
}

export function useDeleteGoamlPerson() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteGoamlPerson,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: goamlPersonsKey }),
  });
}

// ----- cross-tenant user management (SUPER_ADMIN) -----

export const tenantUsersKey = (tenantId: string) => ['admin', 'tenant-users', tenantId] as const;

export function useTenantUsers(tenantId: string | undefined) {
  return useQuery({
    queryKey: ['admin', 'tenant-users', tenantId ?? ''],
    queryFn: () => listTenantUsers(tenantId as string),
    enabled: !!tenantId,
  });
}

export function useCreateTenantUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ tenantId, body }: { tenantId: string; body: CreateUserRequest }) =>
      createTenantUser(tenantId, body),
    onSuccess: (_data, { tenantId }) =>
      queryClient.invalidateQueries({ queryKey: tenantUsersKey(tenantId) }),
  });
}

export function useUpdateTenantUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ tenantId, userId, body }: { tenantId: string; userId: string; body: UpdateUserRequest }) =>
      updateTenantUser(tenantId, userId, body),
    onSuccess: (_data, { tenantId }) =>
      queryClient.invalidateQueries({ queryKey: tenantUsersKey(tenantId) }),
  });
}

export function useDeleteTenantUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ tenantId, userId }: { tenantId: string; userId: string }) =>
      deleteTenantUser(tenantId, userId),
    onSuccess: (_data, { tenantId }) =>
      queryClient.invalidateQueries({ queryKey: tenantUsersKey(tenantId) }),
  });
}

export function useResetTenantUserPassword() {
  return useMutation({
    mutationFn: ({ tenantId, userId, password }: { tenantId: string; userId: string; password: string }) =>
      resetTenantUserPassword(tenantId, userId, { password }),
  });
}

// ----- suite connections (SUPER_ADMIN) -----

export function useTrustedServices() {
  return useQuery({ queryKey: trustedServicesKey, queryFn: listTrustedServices });
}

export function useCreateTrustedService() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createTrustedService,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: trustedServicesKey }),
  });
}

export function useUpdateTrustedService() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateTrustedServiceRequest }) =>
      updateTrustedService(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: trustedServicesKey }),
  });
}

export function useDeleteTrustedService() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteTrustedService,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: trustedServicesKey }),
  });
}

export function useTenantExternalRefs() {
  return useQuery({ queryKey: tenantExternalRefsKey, queryFn: listTenantExternalRefs });
}

export function useCreateTenantExternalRef() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createTenantExternalRef,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tenantExternalRefsKey }),
  });
}

export function useDeleteTenantExternalRef() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteTenantExternalRef,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tenantExternalRefsKey }),
  });
}
