const TOKEN_KEY = "kanban_token";
const REFRESH_TOKEN_KEY = "kanban_refresh_token";

/** Registers a fresh user directly against the API (through the same origin the app uses,
 *  so no CORS involved) — faster and more independent than driving the signup form for
 *  tests that aren't specifically about the signup UI itself. */
export async function registerViaApi(request, { emailPrefix = "e2e" } = {}) {
  const email = `${emailPrefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const response = await request.post("/api/auth/register", {
    data: { email, password: "secret123", displayName: "E2E Tester" },
  });
  if (!response.ok()) {
    throw new Error(`Register failed: ${response.status()} ${await response.text()}`);
  }
  const session = await response.json();
  return { ...session, email };
}

/** Seeds the browser's localStorage with a session before the app's own JS runs, so the
 *  page loads already logged in — the same storage shape LoginForm itself would write. */
export async function loginAs(page, session) {
  await page.addInitScript(
    ({ tokenKey, refreshKey, token, refreshToken }) => {
      localStorage.setItem(tokenKey, token);
      localStorage.setItem(refreshKey, refreshToken);
    },
    {
      tokenKey: TOKEN_KEY,
      refreshKey: REFRESH_TOKEN_KEY,
      token: session.token,
      refreshToken: session.refreshToken,
    }
  );
}

/**
 * Moves a draggable via @hello-pangea/dnd's built-in keyboard support instead of
 * simulating mouse movement. Mouse-simulated drags are notoriously flaky against
 * react-beautiful-dnd-family libraries in headless browsers (their sensor times drag
 * recognition against animation frames); the drag handle is already keyboard-operable
 * for accessibility (Space to lift, arrow keys to move, Space to drop), which is both
 * the standard way to automate these libraries in tests and a better a11y check for free.
 */
export async function moveByKeyboard(sourceLocator, { key, times = 1 }) {
  await sourceLocator.focus();
  await sourceLocator.press("Space"); // lift
  for (let i = 0; i < times; i++) {
    await sourceLocator.press(key);
  }
  await sourceLocator.press("Space"); // drop
}
