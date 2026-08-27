import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LoginForm from "./LoginForm.jsx";
import { auth } from "../api/client.js";
import { DialogProvider } from "../context/DialogContext.jsx";

// LoginForm calls useDialog() (for the "forgot password" prompt), so it needs a
// DialogProvider ancestor even in tests that never trigger that flow.
function renderLoginForm(props) {
  return render(
    <DialogProvider>
      <LoginForm {...props} />
    </DialogProvider>
  );
}

vi.mock("../api/client.js", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    auth: {
      login: vi.fn(),
      register: vi.fn(),
      resendVerification: vi.fn(),
      forgotPassword: vi.fn(),
    },
  };
});

describe("LoginForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("only shows the display name field in register mode", async () => {
    const user = userEvent.setup();
    renderLoginForm({ onAuthenticated: vi.fn() });

    expect(screen.queryByPlaceholderText("Display name")).not.toBeInTheDocument();

    await user.click(screen.getByText("Need an account? Sign up"));

    expect(screen.getByPlaceholderText("Display name")).toBeInTheDocument();
  });

  it("logs in and hands the session back to the caller", async () => {
    auth.login.mockResolvedValue({
      email: "alice@example.com",
      displayName: "Alice",
      emailVerified: true,
    });
    const onAuthenticated = vi.fn();
    const user = userEvent.setup();

    renderLoginForm({ onAuthenticated: onAuthenticated });

    await user.type(screen.getByPlaceholderText("Email"), "alice@example.com");
    await user.type(screen.getByPlaceholderText("Password"), "secret123");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    await waitFor(() =>
      expect(onAuthenticated).toHaveBeenCalledWith({
        email: "alice@example.com",
        displayName: "Alice",
        emailVerified: true,
      })
    );
    expect(auth.login).toHaveBeenCalledWith({ email: "alice@example.com", password: "secret123" });
  });

  it("shows the backend error message on failed login", async () => {
    auth.login.mockRejectedValue({ response: { data: { error: "Invalid credentials" } } });
    const user = userEvent.setup();

    renderLoginForm({ onAuthenticated: vi.fn() });

    await user.type(screen.getByPlaceholderText("Email"), "alice@example.com");
    await user.type(screen.getByPlaceholderText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByText("Invalid credentials")).toBeInTheDocument();
  });

  it("offers to resend the verification email when login is blocked by it", async () => {
    auth.login.mockRejectedValue({ response: { data: { code: "EMAIL_NOT_VERIFIED" } } });
    auth.resendVerification.mockResolvedValue({ message: "Verification email sent" });
    const user = userEvent.setup();

    renderLoginForm({ onAuthenticated: vi.fn() });

    await user.type(screen.getByPlaceholderText("Email"), "unverified@example.com");
    await user.type(screen.getByPlaceholderText("Password"), "secret123");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    const resendButton = await screen.findByText("Resend verification email");
    await user.click(resendButton);

    await waitFor(() => expect(auth.resendVerification).toHaveBeenCalledWith("unverified@example.com"));
    expect(await screen.findByText("Verification email sent")).toBeInTheDocument();
  });
});
