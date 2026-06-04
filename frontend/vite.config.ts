import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// The API client always uses a relative base URL (`/api`). In dev we proxy that
// to the Spring Boot backend so the browser only ever talks to the Vite origin
// (no CORS). In production nginx does the same job (see nginx.conf). PROJECT.md §2.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      // Split the heavy charting/vendor code out of the app chunk.
      output: {
        manualChunks: {
          recharts: ['recharts'],
          klinecharts: ['klinecharts'],
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
});
