/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// goAML SPA build/dev config.
//  - dev: proxy /api + /actuator to the Spring backend on :8080 so the SPA is same-origin in dev
//    (no CORS needed). Override the backend with VITE_BACKEND_URL (e.g. http://localhost:8090).
//  - build: emits to dist/ (Phase 14 packaging wires dist/ into ../src/main/resources/static).
//  - test: Vitest (jsdom) with a shared setup; coverage focuses on the logic layers (api/auth/routes).
const backend = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

// The dev proxy is meant to make the SPA look same-origin to the backend (so no CORS is needed). But
// http-proxy forwards the browser's Origin header, which makes the backend's CORS filter reject the
// request (403) unless the SPA's dev origin happens to be in GOAML_ALLOWED_ORIGINS. Stripping Origin on
// the proxied request restores the intended same-origin behaviour regardless of the backend's allow-list.
const sameOrigin = (proxy: { on: (e: string, cb: (req: { removeHeader: (h: string) => void }) => void) => void }) =>
  proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true, configure: sameOrigin },
      '/actuator': { target: backend, changeOrigin: true, configure: sameOrigin },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // The AntD form-heavy flow specs do many userEvent keystrokes; under full-suite parallel load a
    // few brush against the 5s default. Give them headroom so the gate is deterministic.
    testTimeout: 15000,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/api/**', 'src/auth/**', 'src/routes/**', 'src/lib/**'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**'],
    },
  },
});
