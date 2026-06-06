import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';
import type { NotificationView } from '../types';

const BASE = `${API_PREFIX}/notifications`;

/** The caller's notifications (optionally only unread). */
export async function listNotifications(unreadOnly = false): Promise<NotificationView[]> {
  const { data } = await apiClient.get<NotificationView[]>(BASE, { params: { unread: unreadOnly } });
  return data;
}

/** Mark one of the caller's notifications read. */
export async function markNotificationRead(id: string): Promise<NotificationView> {
  const { data } = await apiClient.post<NotificationView>(`${BASE}/${id}/read`, {});
  return data;
}
