import { test, expect } from "@playwright/test";
import { registerViaApi, loginAs, moveByKeyboard } from "./helpers.js";

test.describe("drag-and-drop", () => {
  test.beforeEach(async ({ page, request }) => {
    const session = await registerViaApi(request, { emailPrefix: "e2e-dnd" });
    await loginAs(page, session);
    await page.goto("/");

    await page.getByRole("button", { name: "New board" }).click();
    await page.getByRole("dialog").getByLabel("Board name").fill("Drag Test Board");
    await page.getByRole("dialog").getByRole("button", { name: "Create" }).click();
  });

  async function addCard(page, columnTitle, cardTitle) {
    const column = page.locator(".column", { hasText: columnTitle });
    await column.getByRole("button", { name: "Add card" }).click();
    await page.getByRole("dialog").getByLabel("Card title").fill(cardTitle);
    await page.getByRole("dialog").getByRole("button", { name: "Add" }).click();
    await expect(page.locator(".card-item", { hasText: cardTitle })).toBeVisible();
  }

  test("moves a card from To Do into In Progress", async ({ page }) => {
    await addCard(page, "To Do", "Design the schema");

    const card = page.locator(".card-item", { hasText: "Design the schema" });
    // Cards list vertically within a column, so moving *between* columns is the
    // perpendicular axis: ArrowRight, per @hello-pangea/dnd's keyboard interaction.
    await moveByKeyboard(card, { key: "ArrowRight", times: 1 });

    await expect(
      page
        .locator(".column", { hasText: "In Progress" })
        .locator(".card-item", { hasText: "Design the schema" })
    ).toBeVisible();
    await expect(
      page
        .locator(".column", { hasText: "To Do" })
        .locator(".card-item", { hasText: "Design the schema" })
    ).toHaveCount(0);

    // Confirm it actually persisted server-side, not just moved visually. The app has no
    // deep link into a specific board (activeBoard is in-memory only), so a full reload
    // would just land back on the board list — instead, leave and reopen the board
    // through the UI, which re-fetches it from the API.
    await page.getByRole("button", { name: "← Back to boards" }).click();
    await page.getByRole("button", { name: "Drag Test Board", exact: true }).click();
    await expect(
      page
        .locator(".column", { hasText: "In Progress" })
        .locator(".card-item", { hasText: "Design the schema" })
    ).toBeVisible();
  });

  test("reorders columns by dragging the column header", async ({ page }) => {
    const todoHeader = page.locator(".column", { hasText: "To Do" }).locator(".column-header");

    // Columns are a horizontal list, so moving within it uses ArrowRight too.
    await moveByKeyboard(todoHeader, { key: "ArrowRight", times: 2 });

    const columnTitles = await page.locator(".column-title").allTextContents();
    expect(columnTitles[0]).not.toBe("To Do");
    expect(columnTitles).toContain("To Do");

    // Confirm it persisted server-side by leaving and reopening the board (see the note
    // in the card-move test above about why this isn't a plain page.reload()).
    await page.getByRole("button", { name: "← Back to boards" }).click();
    await page.getByRole("button", { name: "Drag Test Board", exact: true }).click();
    const columnTitlesAfterReopen = await page.locator(".column-title").allTextContents();
    expect(columnTitlesAfterReopen[0]).not.toBe("To Do");
  });
});
