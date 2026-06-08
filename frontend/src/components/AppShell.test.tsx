import { beforeEach, describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../test/msw/server';
import { renderWithProviders } from '../test/render';
import { makeToken } from '../test/util';
import { getToken, setToken } from '../auth/tokenStore';
import { AppShell } from './AppShell';

// The shell mounts the notification bell, which polls the notifications endpoint.
beforeEach(() => {
  server.use(http.get('*/api/v1/notifications', () => HttpResponse.json([])));
});

function shellRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/dashboard" element={<div>DASHBOARD CONTENT</div>} />
      </Route>
      <Route path="/login" element={<div>LOGIN SCREEN</div>} />
    </Routes>
  );
}

describe('AppShell', () => {
  it('shows the signed-in email + role and renders the routed page', () => {
    setToken(makeToken({ email: 'analyst@acme.test', roles: ['ANALYST'] }));
    renderWithProviders(shellRoutes(), ['/dashboard']);

    expect(screen.getByText('analyst@acme.test')).toBeInTheDocument();
    expect(screen.getByText('ANALYST')).toBeInTheDocument();
    expect(screen.getByText('DASHBOARD CONTENT')).toBeInTheDocument();
  });

  it('hides the Admin link for non-admins and shows it for admins', () => {
    setToken(makeToken({ roles: ['ANALYST'] }));
    const { unmount } = renderWithProviders(shellRoutes(), ['/dashboard']);
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
    unmount();

    setToken(makeToken({ roles: ['TENANT_ADMIN'] }));
    renderWithProviders(shellRoutes(), ['/dashboard']);
    expect(screen.getByRole('link', { name: 'Admin' })).toBeInTheDocument();
  });

  it('hides tenant features (Dashboard, notification bell) from a platform SUPER_ADMIN', () => {
    setToken(makeToken({ email: 'superadmin@goaml.local', roles: ['SUPER_ADMIN'] }));
    renderWithProviders(shellRoutes(), ['/dashboard']);

    expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /notifications/i })).not.toBeInTheDocument();
    // the platform-level areas they can use are present
    expect(screen.getByRole('link', { name: 'Admin' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reference' })).toBeInTheDocument();
  });

  it('signs out: clears the token and returns to /login', async () => {
    setToken(makeToken({ roles: ['MLRO'] }));
    renderWithProviders(shellRoutes(), ['/dashboard']);

    await userEvent.click(screen.getByRole('button', { name: /sign out/i }));

    expect(await screen.findByText('LOGIN SCREEN')).toBeInTheDocument();
    expect(getToken()).toBeNull();
  });
});
