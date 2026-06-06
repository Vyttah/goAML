import { Typography } from 'antd';

/** Placeholder admin area (role-gated) — tenant/user/config management lands in 13.10. */
export function AdminPage() {
  return (
    <>
      <Typography.Title level={3}>Admin</Typography.Title>
      <Typography.Paragraph type="secondary">
        Tenant, user, and goAML-config management arrives in step 13.10.
      </Typography.Paragraph>
    </>
  );
}
