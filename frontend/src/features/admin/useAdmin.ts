import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createUser,
  getGoamlConfig,
  listTenants,
  listUsers,
  provisionTenant,
  upsertGoamlConfig,
} from '../../api/admin';

export const tenantsKey = ['admin', 'tenants'] as const;
export const usersKey = ['admin', 'users'] as const;
export const goamlConfigKey = ['admin', 'goaml-config'] as const;

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
