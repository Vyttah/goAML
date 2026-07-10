import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { landingPathFor } from '../../auth/roles';
import { identityFromToken } from '../../auth/jwt';
import { useLogin } from './useLogin';
import { errorMessage, httpStatus } from '../../api/client';
import type { LoginRequest } from '../../types';

interface LocationState {
  from?: { pathname?: string };
}

/**
 * Email/password sign-in. On success the access token is stored, its claims become the in-app
 * identity, and the user is sent to wherever they were headed, or to their role's landing area
 * (SUPER_ADMIN → /admin, others → /dashboard). Already-authenticated visitors are bounced straight
 * there. There is no refresh token — a 401 elsewhere returns here.
 */
export function LoginPage() {
  const { isAuthenticated, identity, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const loginMutation = useLogin();
  const [error, setError] = useState<string | null>(null);

  const explicitFrom = (location.state as LocationState | null)?.from?.pathname;

  if (isAuthenticated) {
    return <Navigate to={explicitFrom ?? landingPathFor(identity?.roles ?? [])} replace />;
  }

  const onFinish = async (values: LoginRequest) => {
    setError(null);
    try {
      const res = await loginMutation.mutateAsync(values);
      signIn(res.accessToken);
      const roles = identityFromToken(res.accessToken)?.roles ?? [];
      navigate(explicitFrom ?? landingPathFor(roles), { replace: true });
    } catch (err) {
      setError(
        httpStatus(err) === 401
          ? 'Invalid company ID, email, or password'
          : errorMessage(err, 'Login failed'),
      );
    }
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: '12vh' }}>
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 4 }}>
          goAML
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
          Sign in to continue
        </Typography.Paragraph>
        {error && (
          <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} role="alert" />
        )}
        <Form
          layout="vertical"
          onFinish={onFinish}
          requiredMark={false}
          disabled={loginMutation.isPending}
        >
          <Form.Item
            label="Company ID"
            name="companyId"
            rules={[{ required: true, message: 'Company ID is required' }]}
          >
            <Input placeholder="your-company-id" autoComplete="organization" />
          </Form.Item>
          <Form.Item
            label="Email"
            name="email"
            rules={[
              { required: true, message: 'Email is required' },
              { type: 'email', message: 'Enter a valid email' },
            ]}
          >
            <Input placeholder="you@example.com" autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="Password"
            name="password"
            rules={[{ required: true, message: 'Password is required' }]}
          >
            <Input.Password placeholder="Password" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={loginMutation.isPending}>
              Sign in
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
