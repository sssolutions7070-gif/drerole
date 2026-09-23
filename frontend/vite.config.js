import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
    host: true
  },
  build: {
    rollupOptions: {
      output: {
        // Vite 8's default bundler (rolldown) only accepts a function here, not the
        // old object-map shorthand rollup allowed.
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (/[\\/](react|react-dom|react-router-dom)[\\/]/.test(id)) return 'vendor'
            if (id.includes('lucide-react')) return 'icons'
            if (id.includes('axios')) return 'http'
          }
        }
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js'
  }
})
