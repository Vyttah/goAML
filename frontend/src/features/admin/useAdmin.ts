import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createGoamlPerson,
  createUser,
  deleteGoamlPerson,
  getGoamlConfig,
  listGoamlPersons,
  listTenants,
  listUsers,
  provisionTenant,
  updateGoamlPerson,
  upsertGoamlConfig,
} from '../../api/admin';
import type { GoamlPersonRequest } from '../../types';

export const tenantsKey = ['admin', 'tenants'] as const;
export const usersKey = ['admin', 'users'] as const;
export const goamlConfigKey = ['admin', 'goaml-config'] as const;
export const goamlPersonsKey = ['admin', 'goaml-persons'] as const;

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
