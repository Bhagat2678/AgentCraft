import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'https://05dad236f26c7452-106-51-195-253.serveousercontent.com',
        changeOrigin: true,
        secure: false,
      }
    }
  }
});
