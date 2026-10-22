import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * The Studio is served by the same Spring Boot instance that serves the API, mounted under /studio so the legacy Thymeleaf UI keeps working side by side during the transition.
 */
export default defineConfig(({ mode }) => {
  // aponte para outra instancia com VITE_API_TARGET (ex.: um container em outra porta)
  const target = loadEnv(mode, process.cwd(), 'VITE_').VITE_API_TARGET ?? 'http://localhost:22000';

  return {
    base: '/studio/',
    plugins: [react()],
    build: {
      outDir: '../target/classes/static/studio',
      emptyOutDir: true,
      // Sourcemaps ficam no modo dev; o bundle empacotado no jar nao os carrega.
      sourcemap: false,
    },
    server: {
      port: 5173,
      proxy: {
        '/api': target,
        '/query_interface': target,
      },
    },
  };
});
