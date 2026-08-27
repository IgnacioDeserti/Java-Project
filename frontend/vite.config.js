import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.js",
    globals: true,
    // e2e/**/*.spec.js are Playwright tests, not Vitest's — Vitest's default include
    // pattern matches *.spec.js too and would otherwise try (and fail) to run them.
    exclude: ["**/node_modules/**", "e2e/**"],
  },
});
