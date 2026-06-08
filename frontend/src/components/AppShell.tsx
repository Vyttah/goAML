import { Layout, Menu, Typography, Button, Space, Tag } from 'antd';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ROLES } from '../auth/roles';
import { NotificationBell } from './notifications/NotificationBell';

const { Header, Content } = Layout;

/**
 * App shell for authenticated routes: top nav + signed-in identity + sign-out, with the routed page
 * in <Outlet>. Minimal in 13.3 (scaffold) — 13.4 fleshes out the nav/menu and branding.
 */
export function AppShell() {
  const { identity, signOut, can } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Tenant-scoped features (reports dashboard, imports, notifications) don't apply to a platform
  // SUPER_ADMIN, who has no tenant — they'd only hit access-denied/empty schemas.
  const isTenantUser = can(ROLES.ANALYST, ROLES.MLRO, ROLES.TENANT_ADMIN);

  const items = [
    ...(isTenantUser ? [{ key: '/dashboard', label: <Link to="/dashboard">Dashboard</Link> }] : []),
    ...(isTenantUser ? [{ key: '/imports', label: <Link to="/imports">Import</Link> }] : []),
    { key: '/reference', label: <Link to="/reference">Reference</Link> },
    ...(can(ROLES.SUPER_ADMIN, ROLES.TENANT_ADMIN)
      ? [{ key: '/admin', label: <Link to="/admin">Admin</Link> }]
      : []),
  ];

  const selected = items.map((i) => i.key).filter((k) => location.pathname.startsWith(k));

  const handleSignOut = () => {
    signOut();
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <Typography.Text strong style={{ color: '#fff' }}>
          goAML
        </Typography.Text>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={selected}
          items={items}
          style={{ flex: 1, minWidth: 0 }}
        />
        <Space>
          {isTenantUser && <NotificationBell />}
          {identity?.roles[0] && <Tag color="blue">{identity.roles[0]}</Tag>}
          <Typography.Text style={{ color: 'rgba(255,255,255,0.85)' }}>
            {identity?.email}
          </Typography.Text>
          <Button size="small" onClick={handleSignOut}>
            Sign out
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
