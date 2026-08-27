import { test, expect } from "@playwright/test";

test.describe("authentication", () => {
  test("registers a new account through the real UI and lands in the app", async ({ page }) => {
    const email = `e2e-signup-${Date.now()}@example.com`;

    await page.goto("/");
    await page.getByText("Need an account? Sign up").click();

    await page.getByPlaceholder("Display name").fill("Playwright User");
    await page.getByPlaceholder("Email").fill(email);
    await page.getByPlaceholder("Password").fill("secret123");
    await page.getByRole("button", { name: "Sign up" }).click();

    // Registering logs the user straight in (verification only gates future logins).
    await expect(page.getByText("Playwright User")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Your boards" })).toBeVisible();
  });

  test("rejects a login with the wrong password", async ({ page, request }) => {
    const email = `e2e-badlogin-${Date.now()}@example.com`;
    await request.post("/api/auth/register", {
      data: { email, password: "secret123", displayName: "Bad Login" },
    });

    await page.goto("/");
    await page.getByPlaceholder("Email").fill(email);
    await page.getByPlaceholder("Password").fill("totally-wrong-password");
    await page.getByRole("button", { name: "Log in" }).click();

    await expect(page.getByText(/invalid credentials/i)).toBeVisible();
  });

  test("logs out back to the login screen", async ({ page, request }) => {
    const email = `e2e-logout-${Date.now()}@example.com`;
    const reg = await request.post("/api/auth/register", {
      data: { email, password: "secret123", displayName: "Logout Tester" },
    });
    const session = await reg.json();

    await page.addInitScript(
      ({ token, refreshToken }) => {
        localStorage.setItem("kanban_token", token);
        localStorage.setItem("kanban_refresh_token", refreshToken);
      },
      { token: session.token, refreshToken: session.refreshToken }
    );
    await page.goto("/");
    await expect(page.getByText("Logout Tester")).toBeVisible();

    await page.getByRole("button", { name: "Log out" }).click();

    await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
  });
});
