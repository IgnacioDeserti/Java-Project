import { test, expect } from "@playwright/test";
import { registerViaApi, loginAs } from "./helpers.js";

test.describe("search, filters and keyboard shortcuts", () => {
  test.beforeEach(async ({ page, request }) => {
    const session = await registerViaApi(request, { emailPrefix: "e2e-filters" });
    await loginAs(page, session);
    await page.goto("/");

    await page.getByRole("button", { name: "New board" }).click();
    await page.getByRole("dialog").getByLabel("Board name").fill("Filter Board");
    await page.getByRole("dialog").getByRole("button", { name: "Create" }).click();

    // Wait for the board view to actually mount before any test body runs. The keyboard
    // tests press a key as their very first action, and a key pressed before Board has
    // attached its listener is simply lost — nothing would auto-wait for it afterwards.
    await expect(page.getByLabel("Search cards")).toBeVisible();
  });

  async function addCard(page, columnTitle, cardTitle) {
    const column = page.locator(".column", { hasText: columnTitle });
    await column.getByRole("button", { name: "Add card" }).click();
    await page.getByRole("dialog").getByLabel("Card title").fill(cardTitle);
    await page.getByRole("dialog").getByRole("button", { name: "Add" }).click();
    await expect(page.locator(".card-item", { hasText: cardTitle })).toBeVisible();
  }

  test("filters cards by search text and reports how many matched", async ({ page }) => {
    await addCard(page, "To Do", "Write documentation");
    await addCard(page, "To Do", "Fix the login bug");

    await page.getByLabel("Search cards").fill("documentation");

    await expect(page.locator(".card-item", { hasText: "Write documentation" })).toBeVisible();
    await expect(page.locator(".card-item", { hasText: "Fix the login bug" })).toHaveCount(0);
    await expect(page.getByText("1 of 2 cards")).toBeVisible();

    await page.getByRole("button", { name: /clear/i }).click();
    await expect(page.locator(".card-item", { hasText: "Fix the login bug" })).toBeVisible();
  });

  test("filters by priority", async ({ page }) => {
    await addCard(page, "To Do", "Normal work");

    // New cards default to MEDIUM, so filtering to HIGH should hide it.
    await page.getByLabel("Filter by priority").selectOption("HIGH");
    await expect(page.locator(".card-item", { hasText: "Normal work" })).toHaveCount(0);
    await expect(page.getByText("0 of 1 cards")).toBeVisible();

    await page.getByLabel("Filter by priority").selectOption("MEDIUM");
    await expect(page.locator(".card-item", { hasText: "Normal work" })).toBeVisible();
  });

  test("filters by label, and labels set in the editor persist", async ({ page }) => {
    await addCard(page, "To Do", "Labelled card");
    await addCard(page, "To Do", "Plain card");

    // Tag the first card red through the inline editor.
    const card = page.locator(".card-item", { hasText: "Labelled card" });
    await card.hover();
    await card.getByTitle("Edit card").click();
    const editor = page.locator(".card-editor");
    await editor.getByRole("button", { name: "red label" }).click();
    await editor.getByRole("button", { name: "Save" }).click();

    await expect(
      page.locator(".card-item", { hasText: "Labelled card" }).locator('[data-label="RED"]')
    ).toBeVisible();

    await page.getByRole("button", { name: "Filter by red label" }).click();
    await expect(page.locator(".card-item", { hasText: "Labelled card" })).toBeVisible();
    await expect(page.locator(".card-item", { hasText: "Plain card" })).toHaveCount(0);
  });

  test("pauses dragging while a filter is active", async ({ page }) => {
    await addCard(page, "To Do", "Some card");
    await page.getByLabel("Search cards").fill("Some");

    await expect(page.getByText("drag paused")).toBeVisible();
    // dnd marks a disabled draggable by dropping its drag-handle attribute entirely.
    await expect(page.locator(".card-item").first()).not.toHaveAttribute(
      "data-rfd-drag-handle-draggable-id",
      /.*/
    );
  });

  test("opens the shortcuts help with ? and closes it with Escape", async ({ page }) => {
    await page.locator("body").press("?");
    await expect(page.getByRole("heading", { name: "Keyboard shortcuts" })).toBeVisible();

    // Escape closes it (handled by the shared Modal).
    await page.keyboard.press("Escape");
    await expect(page.getByRole("heading", { name: "Keyboard shortcuts" })).toHaveCount(0);
  });

  test("focuses the search box with /", async ({ page }) => {
    await page.locator("body").press("/");
    await expect(page.getByLabel("Search cards")).toBeFocused();
  });

  test("does not hijack letter keys while typing in a field", async ({ page }) => {
    const search = page.getByLabel("Search cards");
    await search.click();
    await search.fill("");
    await search.type("nice cards");

    // "n" and "c" are shortcuts, but typing them must not open dialogs.
    await expect(search).toHaveValue("nice cards");
    await expect(page.getByRole("dialog")).toHaveCount(0);
  });
});
