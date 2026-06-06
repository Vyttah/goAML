/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// goAML SPA build/dev config.
//  - dev: proxy /api + /actuator to the Spring backend on :8080 so the SPA is same-origin in dev
//    (no CORS needed). Override the backend with VITE_BACKEND_URL.
//  - build: emits to dist/ (Phase 14 packaging wires dist/ into ../src/main/resources/static).
//  - test: Vitest (jsdom) with a shared setup; coverage focuses on the logic layers (api/auth/routes).
const backend = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/actuator': { target: backend, changeOrigin: true },
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
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/api/**', 'src/auth/**', 'src/routes/**', 'src/lib/**'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**'],
    },
  },
});
