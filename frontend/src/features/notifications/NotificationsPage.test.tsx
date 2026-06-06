import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { NotificationsPage } from './NotificationsPage';
import type { NotificationView } from '../../types';

function notif(over: Partial<NotificationView> = {}): NotificationView {
  return {
    id: 'n-1',
    type: 'REPORT_ACCEPTED',
    reportId: 'rep-1',
    title: 'Report accepted',
    body: 'DPMSR-1 was accepted by the FIU',
    readAt: null,
    createdAt: '2026-06-01T10:00:00Z',
    ...over,
  };
}

function routes() {
  return (
    <Routes>
      <Route path="/notifications" element={<NotificationsPage />} />
      <Route path="/reports/:id" element={<div>REPORT DETAIL PAGE</div>} />
    </Routes>
  );
}

function render() {
  setToken(makeToken({ roles: ['MLRO'] }));
  return renderWithProviders(routes(), ['/notifications']);
}

describe('NotificationsPage', () => {
  it('lists notifications and marks an unread one read', async () => {
    let read = false;
    server.use(
      http.get('*/api/v1/notifications', () =>
        HttpResponse.json([notif({ readAt: read ? '2026-06-02T00:00:00Z' : null })]),
      ),
      http.post('*/api/v1/notifications/n-1/read', () => {
        read = true;
        return HttpResponse.json(notif({ readAt: '2026-06-02T00:00:00Z' }));
      }),
    );
    render();

    expect(await screen.findByText('Report accepted')).toBeInTheDocument();
    expect(screen.getByText('new')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Mark read' }));

    // after refetch the "new" tag is gone
    expect(await screen.findByText('Report accepted')).toBeInTheDocument();
    expect(screen.queryByText('new')).not.toBeInTheDocument();
  });

  it('opens the related report', async () => {
    server.use(
      http.get('*/api/v1/notifications', () => HttpResponse.json([notif()])),
      http.post('*/api/v1/notifications/n-1/read', () => HttpResponse.json(notif({ readAt: 'x' }))),
    );
    render();

    await userEvent.click(await screen.findByRole('button', { name: 'Open report' }));
    expect(await screen.findByText('REPORT DETAIL PAGE')).toBeInTheDocument();
  });
});
