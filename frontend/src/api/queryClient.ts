import { QueryClient } from '@tanstack/react-query';

/**
 * Shared TanStack Query client. Server state is cached here; mutations invalidate the relevant keys.
 * Retries are off for 4xx (a 401 is handled by the axios interceptor; 4xx won't fix itself on retry).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});
