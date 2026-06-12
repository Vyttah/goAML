import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import { AuthProvider } from './auth/AuthContext';
import { ExpiryWarning } from './auth/SessionExpiryWarning';
import { queryClient } from './api/queryClient';
import { AppRoutes } from './routes/AppRoutes';

/** Top-level providers: Ant Design theme + message context, TanStack Query, auth, router. */
export function App() {
  return (
    <ConfigProvider>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <ExpiryWarning />
            <BrowserRouter>
              <AppRoutes />
            </BrowserRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
