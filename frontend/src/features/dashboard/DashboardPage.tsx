import { Typography } from 'antd';
import { useAuth } from '../../auth/AuthContext';

/** Placeholder dashboard — the report list + filters land in 13.5. */
export function DashboardPage() {
  const { identity } = useAuth();
  return (
    <>
      <Typography.Title level={3}>Dashboard</Typography.Title>
      <Typography.Paragraph type="secondary">
        Signed in as {identity?.email}. Report list arrives in step 13.5.
      </Typography.Paragraph>
    </>
  );
}
