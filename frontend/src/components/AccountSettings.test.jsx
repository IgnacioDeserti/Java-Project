import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AccountSettings from "./AccountSettings.jsx";
import { auth } from "../api/client.js";
import { DialogProvider } from "../context/DialogContext.jsx";

vi.mock("../api/client.js", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    auth: {
      updateProfile: vi.fn(),
      changePassword: vi.fn(),
      deleteAccount: vi.fn(),
    },
  };
});

const user = {
  email: "alice@example.com",
  displayName: "Alice",
  emailVerified: true,
  hasPassword: true,
};

// AccountSettings' delete flow opens our own confirm dialog (via useDialog()), so it
// needs a DialogProvider ancestor — not a window.confirm mock.
function renderAccountSettings(props) {
  return render(
    <DialogProvider>
      <AccountSettings
        user={user}
        onClose={vi.fn()}
        onProfileUpdated={vi.fn()}
        onAccountDeleted={vi.fn()}
        {...props}
      />
    </DialogProvider>
  );
}

describe("AccountSettings", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("saves a new display name", async () => {
    auth.updateProfile.mockResolvedValue({ ...user, displayName: "Alicia" });
    const onProfileUpdated = vi.fn();
    const u = userEvent.setup();

    renderAccountSettings({ onProfileUpdated });

    const nameInput = screen.getByLabelText("Display name");
    await u.clear(nameInput);
    await u.type(nameInput, "Alicia");
    await u.click(screen.getByRole("button", { name: "Save name" }));

    await waitFor(() => expect(auth.updateProfile).toHaveBeenCalledWith("Alicia"));
    expect(onProfileUpdated).toHaveBeenCalledWith({ ...user, displayName: "Alicia" });
  });

  it("changes the password and shows the session-logout notice", async () => {
    auth.changePassword.mockResolvedValue({ message: "Password updated" });
    const u = userEvent.setup();

    renderAccountSettings();

    await u.type(screen.getByLabelText("Current password"), "old-secret");
    await u.type(screen.getByLabelText("New password"), "new-secret-123");
    await u.click(screen.getByRole("button", { name: "Update password" }));

    await waitFor(() =>
      expect(auth.changePassword).toHaveBeenCalledWith("old-secret", "new-secret-123")
    );
    expect(await screen.findByText(/other sessions were logged out/i)).toBeInTheDocument();
  });

  it("shows the backend error when the current password is wrong", async () => {
    auth.changePassword.mockRejectedValue({
      response: { data: { error: "Current password is incorrect" } },
    });
    const u = userEvent.setup();

    renderAccountSettings();

    await u.type(screen.getByLabelText("Current password"), "wrong");
    await u.type(screen.getByLabelText("New password"), "new-secret-123");
    await u.click(screen.getByRole("button", { name: "Update password" }));

    expect(await screen.findByText("Current password is incorrect")).toBeInTheDocument();
  });

  it("deletes the account after confirming in the dialog", async () => {
    auth.deleteAccount.mockResolvedValue();
    const onAccountDeleted = vi.fn();
    const u = userEvent.setup();

    renderAccountSettings({ onAccountDeleted });

    await u.type(screen.getByLabelText("Confirm your password"), "secret123");
    await u.click(screen.getByRole("button", { name: "Delete my account" }));

    // Our own confirm dialog, not window.confirm — it must actually appear before deleting.
    const dialogConfirm = await screen.findByRole("button", { name: "Yes, delete it" });
    expect(auth.deleteAccount).not.toHaveBeenCalled();
    await u.click(dialogConfirm);

    await waitFor(() => expect(auth.deleteAccount).toHaveBeenCalledWith("secret123"));
    expect(onAccountDeleted).toHaveBeenCalled();
  });

  it("does not call deleteAccount if the confirmation dialog is cancelled", async () => {
    const u = userEvent.setup();

    renderAccountSettings();

    await u.type(screen.getByLabelText("Confirm your password"), "secret123");
    await u.click(screen.getByRole("button", { name: "Delete my account" }));

    const cancelButton = await screen.findByRole("button", { name: "Cancel" });
    await u.click(cancelButton);

    expect(auth.deleteAccount).not.toHaveBeenCalled();
  });
});

describe("AccountSettings for a Google-only account (no password set)", () => {
  const googleUser = { ...user, hasPassword: false };

  function renderForGoogleUser(props) {
    return render(
      <DialogProvider>
        <AccountSettings
          user={googleUser}
          onClose={vi.fn()}
          onProfileUpdated={vi.fn()}
          onAccountDeleted={vi.fn()}
          {...props}
        />
      </DialogProvider>
    );
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("offers to set a password instead of change it, with no current-password field", () => {
    renderForGoogleUser();

    expect(screen.getByText("Set a password")).toBeInTheDocument();
    expect(screen.queryByLabelText("Current password")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set password" })).toBeInTheDocument();
  });

  it("sets the first password without sending a current one", async () => {
    auth.changePassword.mockResolvedValue({ message: "Password updated" });
    const u = userEvent.setup();

    renderForGoogleUser();

    await u.type(screen.getByLabelText("New password"), "brand-new-123");
    await u.click(screen.getByRole("button", { name: "Set password" }));

    await waitFor(() => expect(auth.changePassword).toHaveBeenCalledWith("", "brand-new-123"));
  });

  it("deletes the account with no password field to fill in", async () => {
    auth.deleteAccount.mockResolvedValue();
    const onAccountDeleted = vi.fn();
    const u = userEvent.setup();

    renderForGoogleUser({ onAccountDeleted });

    expect(screen.queryByLabelText("Confirm your password")).not.toBeInTheDocument();
    await u.click(screen.getByRole("button", { name: "Delete my account" }));

    const dialogConfirm = await screen.findByRole("button", { name: "Yes, delete it" });
    await u.click(dialogConfirm);

    await waitFor(() => expect(auth.deleteAccount).toHaveBeenCalledWith(""));
    expect(onAccountDeleted).toHaveBeenCalled();
  });
});
