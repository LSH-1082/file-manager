import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발·미리보기에서 /api를 백엔드(기본 8080)로 넘긴다(같은 출처라 CORS가 필요 없다)
const proxy = { '/api': 'http://localhost:8080' }

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: { proxy },
  preview: { proxy },
})
