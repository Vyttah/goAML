import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import { AuthProvider } from './auth/AuthContext';
import { ExpiryWarning } from './auth/SessionExpiryWarning';
import { queryClient } from './api/queryClient';
import { AppRoutes } from './routes/AppRoutes';

/** Top-level providers: Ant Design theme + message context, TanStack Query, auth, router. */
export function App() {
  // Serve correctly under a non-root context (Tomcat's /goaml). BASE_URL is '/' at the root context and
  // '/goaml/' for the WAR build; strip the trailing slash so react-router treats it as the basename.
  const routerBasename = import.meta.env.BASE_URL.replace(/\/$/, '');
  return (
    <ConfigProvider>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <ExpiryWarning />
            <BrowserRouter basename={routerBasename}>
              <AppRoutes />
            </BrowserRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
