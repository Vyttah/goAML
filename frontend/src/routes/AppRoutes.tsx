import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './RequireAuth';
import { RequireRole } from './RequireRole';
import { AppShell } from '../components/AppShell';
import { ROLES, landingPathFor } from '../auth/roles';
import { useAuth } from '../auth/AuthContext';
import { LoginPage } from '../features/auth/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { DpmsrBuilderPage } from '../features/reports/DpmsrBuilderPage';
import { ReportDetailPage } from '../features/reports/ReportDetailPage';
import { ReviewQueuePage } from '../features/reports/ReviewQueuePage';
import { ImportPage } from '../features/imports/ImportPage';
import { NotificationsPage } from '../features/notifications/NotificationsPage';
import { LookupsBrowserPage } from '../features/lookups/LookupsBrowserPage';
import { ScreeningSubjectsPage } from '../features/screening/ScreeningSubjectsPage';
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
          <Route index element={<HomeRedirect />} />
          <Route path="dashboard" element={<DashboardPage />} />

          <Route element={<RequireRole allowed={[ROLES.ANALYST, ROLES.MLRO]} />}>
            <Route path="reports/new" element={<DpmsrBuilderPage />} />
          </Route>
          <Route element={<RequireRole allowed={[ROLES.MLRO, ROLES.TENANT_ADMIN]} />}>
            <Route path="reports/review" element={<ReviewQueuePage />} />
          </Route>
          <Route path="reports/:id" element={<ReportDetailPage />} />
          <Route path="imports" element={<ImportPage />} />
          <Route element={<RequireRole allowed={[ROLES.ANALYST, ROLES.MLRO, ROLES.TENANT_ADMIN]} />}>
            <Route path="screening" element={<ScreeningSubjectsPage />} />
          </Route>
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="reference" element={<LookupsBrowserPage />} />

          <Route element={<RequireRole allowed={[ROLES.SUPER_ADMIN, ROLES.TENANT_ADMIN]} />}>
            <Route path="admin" element={<AdminPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

/** Index redirect: sends each role to its landing area (SUPER_ADMIN → /admin, others → /dashboard). */
function HomeRedirect() {
  const { identity } = useAuth();
  return <Navigate to={landingPathFor(identity?.roles ?? [])} replace />;
}
