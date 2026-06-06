import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import { server } from './msw/server';
import { clearToken } from '../auth/tokenStore';

// MSW: error on any unhandled request so tests can't silently hit the network.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  server.resetHandlers();
  cleanup();
  clearToken();
  localStorage.clear();
});

afterAll(() => server.close());
