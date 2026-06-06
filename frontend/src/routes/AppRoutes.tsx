import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './RequireAuth';
import { RequireRole } from './RequireRole';
import { AppShell } from '../components/AppShell';
import { ROLES } from '../auth/roles';
import { LoginPage } from '../features/auth/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { AdminPage } from '../features/admin/AdminPage';
import { ForbiddenPage, NotFoundPage } from '../features/misc/StatusPages';

/**
 * Route table. Public: /login, /forbidden. Everything else is behind RequireAuth (→ /login when
 * unauthenticated) and rendered inside the AppShell; /admin is further gated by RequireRole.
 * Exported separately from <AppRouter> so tests can mount it inside a MemoryRouter.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forbidden" element={<ForbiddenPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />

          <Route element={<RequireRole allowed={[ROLES.SUPER_ADMIN, ROLES.TENANT_ADMIN]} />}>
            <Route path="admin" element={<AdminPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
