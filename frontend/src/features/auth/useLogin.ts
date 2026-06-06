import { useMutation } from '@tanstack/react-query';
import { login } from '../../api/auth';
import type { LoginRequest, LoginResponse } from '../../types';

/** Login mutation — exposes isPending/isError for the form's loading + error UX. */
export function useLogin() {
  return useMutation<LoginResponse, unknown, LoginRequest>({ mutationFn: login });
}
