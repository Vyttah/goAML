import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAuth } from './RequireAuth';
import { RequireRole } from './RequireRole';
import { ROLES } from '../auth/roles';
import { setToken } from '../auth/tokenStore';
import { makeToken } from '../test/util';

function renderRoutes(initialPath: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div>LOGIN</div>} />
          <Route path="/forbidden" element={<div>FORBIDDEN</div>} />
          <Route element={<RequireAuth />}>
            <Route path="/dashboard" element={<div>DASHBOARD</div>} />
            <Route element={<RequireRole allowed={[ROLES.TENANT_ADMIN]} />}>
              <Route path="/admin" element={<div>ADMIN AREA</div>} />
            </Route>
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('RequireAuth', () => {
  it('redirects unauthenticated users to /login', () => {
    renderRoutes('/dashboard');
    expect(screen.getByText('LOGIN')).toBeInTheDocument();
  });

  it('renders the route for an authenticated user', () => {
    setToken(makeToken({ roles: ['ANALYST'] }));
    renderRoutes('/dashboard');
    expect(screen.getByText('DASHBOARD')).toBeInTheDocument();
  });
});

describe('RequireRole', () => {
  it('allows a user holding the required role', () => {
    setToken(makeToken({ roles: ['TENANT_ADMIN'] }));
    renderRoutes('/admin');
    expect(screen.getByText('ADMIN AREA')).toBeInTheDocument();
  });

  it('sends a user lacking the role to /forbidden', () => {
    setToken(makeToken({ roles: ['ANALYST'] }));
    renderRoutes('/admin');
    expect(screen.getByText('FORBIDDEN')).toBeInTheDocument();
  });
});
