import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server runs on 4200 to match the backend's SA_FRONTEND_RESULT_URL default.
// /api is proxied to the Spring Boot backend so there are no CORS issues in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 4200,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
