import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev proxy /api -> http://localhost:8080 by default, overridable with
// VITE_API_TARGET (PROMPT.md section 7). Production build output goes to
// frontend/dist; Step 8 copies it into the Spring static folder.
export default defineConfig(({ mode }) => {
  const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080';
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true },
        '/actuator': { target: apiTarget, changeOrigin: true },
      },
    },
    build: {
      outDir: 'dist',
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./tests/setup.js'],
      globals: true,
      css: true,
    },
  };
});
