import { defineConfig } from 'vite'
import { resolve } from 'path'

export default defineConfig({
  clearScreen: false,
  // index.html lives in src/
  root: resolve(__dirname, 'src'),
  server: {
    port: 1420,
    strictPort: true,
  },
  envPrefix: ['VITE_', 'TAURI_'],
  build: {
    target: ['es2021', 'chrome100', 'safari13'],
    minify: !process.env.TAURI_DEBUG ? 'esbuild' : false,
    sourcemap: !!process.env.TAURI_DEBUG,
    // Output relative to project root (not src/), Tauri reads '../dist'
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,
  },
})
