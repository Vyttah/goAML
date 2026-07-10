import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { makeToken } from '../../test/util';
import { getToken, setToken } from '../../auth/tokenStore';
import { LoginPage } from './LoginPage';

function routes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/dashboard" element={<div>DASHBOARD PAGE</div>} />
      <Route path="/admin" element={<div>ADMIN PAGE</div>} />
      <Route path="/reports/new" element={<div>NEW REPORT PAGE</div>} />
    </Routes>
  );
}

async function fillAndSubmit() {
  await userEvent.type(screen.getByPlaceholderText('your-company-id'), 'acme');
  await userEvent.type(screen.getByPlaceholderText('you@example.com'), 'mlro@acme.test');
  await userEvent.type(screen.getByPlaceholderText('Password'), 'P@ssw0rd!');
  await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
}

describe('LoginPage', () => {
  it('logs in, stores the token, and navigates to the dashboard', async () => {
    const token = makeToken({ email: 'mlro@acme.test', roles: ['MLRO'] });
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 }),
      ),
    );

    renderWithProviders(routes(), ['/login']);
    await fillAndSubmit();

    expect(await screen.findByText('DASHBOARD PAGE')).toBeInTheDocument();
    expect(getToken()).toBe(token);
  });

  it('sends a SUPER_ADMIN to /admin (no tenant dashboard)', async () => {
    const token = makeToken({ email: 'superadmin@goaml.local', roles: ['SUPER_ADMIN'] });
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 }),
      ),
    );

    renderWithProviders(routes(), ['/login']);
    await userEvent.type(screen.getByPlaceholderText('your-company-id'), 'PLATFORM');
    await userEvent.type(screen.getByPlaceholderText('you@example.com'), 'superadmin@goaml.local');
    await userEvent.type(screen.getByPlaceholderText('Password'), 'P@ssw0rd!');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('ADMIN PAGE')).toBeInTheDocument();
    expect(screen.queryByText('DASHBOARD PAGE')).not.toBeInTheDocument();
  });

  it('shows an error and stays on /login for bad credentials', async () => {
    server.use(http.post('*/api/v1/auth/login', () => new HttpResponse(null, { status: 401 })));

    renderWithProviders(routes(), ['/login']);
    await fillAndSubmit();

    expect(await screen.findByText('Invalid company ID, email, or password')).toBeInTheDocument();
    expect(screen.queryByText('DASHBOARD PAGE')).not.toBeInTheDocument();
    expect(getToken()).toBeNull();
  });

  it('redirects an already-authenticated visitor away from /login', () => {
    setToken(makeToken({ roles: ['ANALYST'] }));
    renderWithProviders(routes(), ['/login']);
    expect(screen.getByText('DASHBOARD PAGE')).toBeInTheDocument();
  });
});
