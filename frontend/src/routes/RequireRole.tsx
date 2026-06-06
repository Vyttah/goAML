import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../auth/roles';

/**
 * Gate for role-restricted routes. An authenticated user lacking every one of `allowed` is sent to
 * /forbidden. This is UX only — the backend independently enforces RBAC on every request.
 */
export function RequireRole({ allowed }: { allowed: Role[] }) {
  const { can } = useAuth();
  if (!can(...allowed)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <Outlet />;
}
