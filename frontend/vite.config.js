import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // Permite que Docker exponga el puerto al exterior
    port: 5173,
    watch: {
      usePolling: true // Vital en WSL/Docker para detectar cambios de archivos
    }
  }
})