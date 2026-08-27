import { test, expect } from "@playwright/test";
import { registerViaApi, loginAs } from "./helpers.js";

test.describe("boards, columns and cards", () => {
  test.beforeEach(async ({ page, request }) => {
    const session = await registerViaApi(request, { emailPrefix: "e2e-boards" });
    await loginAs(page, session);
    await page.goto("/");
  });

  test("creates a board with the three default columns", async ({ page }) => {
    await page.getByRole("button", { name: "New board" }).click();

    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Board name").fill("Sprint Planning");
    await dialog.getByRole("button", { name: "Create" }).click();

    await expect(page.getByRole("heading", { name: "Sprint Planning" })).toBeVisible();
    await expect(page.getByText("To Do", { exact: true })).toBeVisible();
    await expect(page.getByText("In Progress", { exact: true })).toBeVisible();
    await expect(page.getByText("Done", { exact: true })).toBeVisible();
  });

  test("adds a card to a column and can edit and delete it", async ({ page }) => {
    await page.getByRole("button", { name: "New board" }).click();
    await page.getByRole("dialog").getByLabel("Board name").fill("Card Lifecycle");
    await page.getByRole("dialog").getByRole("button", { name: "Create" }).click();

    const todoColumn = page.locator(".column", { hasText: "To Do" });
    await todoColumn.getByRole("button", { name: "Add card" }).click();
    await page.getByRole("dialog").getByLabel("Card title").fill("Write the README");
    await page.getByRole("dialog").getByRole("button", { name: "Add" }).click();

    const card = page.locator(".card-item", { hasText: "Write the README" });
    await expect(card).toBeVisible();

    // The edit/delete icon buttons are only shown on hover (see .card-actions in
    // index.css) — hover the card first so Playwright can actually see and click them.
    await card.hover();
    await card.getByTitle("Edit card").click();

    // Once editing, the title becomes an <input value="..."> — hasText matches rendered
    // text nodes, not input values, so the `card` locator above would no longer resolve
    // to this element. Only one card is being edited at a time, so scope by the editor
    // form instead of by the (now-stale) title text.
    const editor = page.locator(".card-editor");
    await editor.locator("textarea").fill("Explain the setup steps");
    await editor.getByRole("button", { name: "Save" }).click();

    const editedCard = page.locator(".card-item", { hasText: "Write the README" });
    await expect(editedCard.getByText("Explain the setup steps")).toBeVisible();

    await editedCard.hover();
    await editedCard.getByTitle("Delete card").click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: /delete card/i })
      .click();
    await expect(page.locator(".card-item", { hasText: "Write the README" })).toHaveCount(0);
  });

  test("adds and deletes a column", async ({ page }) => {
    await page.getByRole("button", { name: "New board" }).click();
    await page.getByRole("dialog").getByLabel("Board name").fill("Column Lifecycle");
    await page.getByRole("dialog").getByRole("button", { name: "Create" }).click();

    await page.getByRole("button", { name: "Add column" }).click();
    await page.getByRole("dialog").getByLabel("Column title").fill("Backlog");
    await page.getByRole("dialog").getByRole("button", { name: "Add" }).click();

    const backlogColumn = page.locator(".column", { hasText: "Backlog" });
    await expect(backlogColumn).toBeVisible();

    // Not getByRole here: the column header is itself a drag handle with role="button"
    // (from @hello-pangea/dnd), and its computed accessible name folds in the nested
    // delete button's aria-label too — ambiguous. The title attribute is unambiguous.
    await backlogColumn.getByTitle("Delete column").click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: /delete column/i })
      .click();
    await expect(page.locator(".column", { hasText: "Backlog" })).toHaveCount(0);
  });

  test("deletes a board from the board list", async ({ page }) => {
    await page.getByRole("button", { name: "New board" }).click();
    await page.getByRole("dialog").getByLabel("Board name").fill("Board To Delete");
    await page.getByRole("dialog").getByRole("button", { name: "Create" }).click();
    await page.getByRole("button", { name: "← Back to boards" }).click();

    await expect(page.getByText("Board To Delete")).toBeVisible();

    await page.getByRole("button", { name: /delete board "board to delete"/i }).click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: /delete board/i })
      .click();

    await expect(page.getByText("Board To Delete")).toHaveCount(0);
  });
});
