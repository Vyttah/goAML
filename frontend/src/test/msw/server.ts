import { setupServer } from 'msw/node';

/**
 * Shared MSW server for tests. Starts with no handlers — each test registers what it needs via
 * `server.use(...)`. Lifecycle (listen/resetHandlers/close) is wired in src/test/setup.ts.
 */
export const server = setupServer();
