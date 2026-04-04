import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      // SSE endpoint — must not be buffered by the proxy
      "/api/grading/stream": {
        target: "http://localhost:8080",
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on("proxyRes", (proxyRes) => {
            proxyRes.headers["x-accel-buffering"] = "no";
            proxyRes.headers["cache-control"] = "no-cache";
          });
        },
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        proxyTimeout: 300_000,  // 5 min — PDF OCR can be slow
        timeout: 300_000,
      },
    },
  },
  build: {
    outDir: "dist",
  },
});

