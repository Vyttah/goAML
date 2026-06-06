import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { NotificationBell } from './NotificationBell';
import type { NotificationView } from '../../types';

function unread(id: string): NotificationView {
  return {
    id,
    type: 'REPORT_REJECTED',
    reportId: 'rep-1',
    title: `Notice ${id}`,
    body: 'something happened',
    readAt: null,
    createdAt: '2026-06-01T10:00:00Z',
  };
}

function renderBell() {
  setToken(makeToken({ roles: ['MLRO'] }));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('NotificationBell', () => {
  it('shows the unread count badge', async () => {
    server.use(
      http.get('*/api/v1/notifications', () => HttpResponse.json([unread('a'), unread('b')])),
    );
    renderBell();
    expect(await screen.findByText('2')).toBeInTheDocument();
  });
});
