import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import { fileURLToPath } from 'node:url'

// Served by the Studio server at /learn. In dev, /api is proxied to it;
// if it isn't running, the app falls back to the bundled curriculum.
// `--mode ios` builds the copy that ships inside the iOS app: served from the phone at /.
export default defineConfig(({ mode }) => {
  const ios = mode === 'ios'
  return {
    base: ios ? '/' : '/learn/',
    // The app is loaded from the phone, so it must be told which server to talk to.
    define: ios
      ? { 'import.meta.env.VITE_API_ORIGIN': JSON.stringify(process.env.VITE_API_ORIGIN ?? 'https://loba-web-production.up.railway.app') }
      : {},
    plugins: [tanstackRouter({ target: 'react', autoCodeSplitting: true }), react()],
    server: {
      port: 5173,
      proxy: { '/api': 'http://localhost:3000' },
      fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] },
    },
    build: { outDir: ios ? 'dist-ios' : 'dist', emptyOutDir: true, assetsInlineLimit: 0 },
    test: { environment: 'node', include: ['src/**/*.test.ts'] },
  }
})
