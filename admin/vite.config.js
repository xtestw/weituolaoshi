import { defineConfig } from 'vite'
export default defineConfig({
  base: '/admin/',
  server: { proxy: { '/admin/api': 'http://127.0.0.1:18080' } }
})
