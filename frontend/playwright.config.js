import { defineConfig, devices } from "@playwright/test";

// These tests need the real backend + Postgres running behind the frontend, since they
// exercise actual auth, board persistence and drag-and-drop against the live API — not
// mocked. Point BASE_URL at whichever origin is up (Docker/nginx by default).
const baseURL = process.env.BASE_URL || "http://localhost:5173";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
