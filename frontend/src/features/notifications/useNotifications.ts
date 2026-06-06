import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listNotifications, markNotificationRead } from '../../api/notifications';

export const notificationsQueryKey = (unreadOnly: boolean) =>
  ['notifications', unreadOnly] as const;

/** The caller's notifications (all, or unread-only). */
export function useNotifications(unreadOnly = false) {
  return useQuery({
    queryKey: notificationsQueryKey(unreadOnly),
    queryFn: () => listNotifications(unreadOnly),
  });
}

/** Unread count for the header badge; polls so it stays roughly fresh. */
export function useUnreadNotifications() {
  return useQuery({
    queryKey: notificationsQueryKey(true),
    queryFn: () => listNotifications(true),
    refetchInterval: 60_000,
  });
}

/** Mark a notification read; refreshes every notification query. */
export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
}
