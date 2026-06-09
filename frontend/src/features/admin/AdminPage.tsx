import { Typography } from 'antd';
import { useAuth } from '../../auth/AuthContext';
import { ROLES } from '../../auth/roles';
import { TenantsPanel } from './TenantsPanel';
import { UsersPanel } from './UsersPanel';
import { GoamlConfigPanel } from './GoamlConfigPanel';
import { GoamlPersonsPanel } from './GoamlPersonsPanel';

/**
 * Administration — role-branched over the 13.2 admin API:
 *  - SUPER_ADMIN: tenant management (provision/list).
 *  - TENANT_ADMIN: user management + goAML B2B config, scoped to the caller's own tenant.
 * The route is already gated to those two roles (RequireRole); this picks the right panels.
 */
export function AdminPage() {
  const { can } = useAuth();

  return (
    <>
      <Typography.Title level={3}>Administration</Typography.Title>
      {can(ROLES.SUPER_ADMIN) && <TenantsPanel />}
      {can(ROLES.TENANT_ADMIN) && (
        <>
          <UsersPanel />
          <GoamlPersonsPanel />
          <GoamlConfigPanel />
        </>
      )}
    </>
  );
}
