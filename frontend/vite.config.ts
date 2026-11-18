/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 브라우저는 5173 한 주소만 사용하고, API와 카카오 로그인 경로는 Spring(8080)으로 넘긴다.
// Host 헤더(localhost:5173)를 그대로 전달해야 백엔드가 카카오 redirect_uri와 Origin 검사 기준을
// 5173 주소로 만든다. 주소 문자열만 쓰면 Vite가 changeOrigin: true로 처리해 Host를 8080으로 바꾸므로 객체로 지정한다.
const backend = { target: 'http://localhost:8080', changeOrigin: false }

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/oauth2': backend,
      '/login/oauth2': backend,
    },
  },
})
