import { Typography } from 'antd';

/** Placeholder login page — the real email/password form lands in 13.4. */
export function LoginPage() {
  return (
    <div style={{ maxWidth: 360, margin: '15vh auto', textAlign: 'center' }}>
      <Typography.Title level={3}>goAML</Typography.Title>
      <Typography.Paragraph type="secondary">Sign-in form arrives in step 13.4.</Typography.Paragraph>
    </div>
  );
}
