import { useState } from "react";
import { X } from "lucide-react";
import { auth, errorMessage } from "../api/client.js";
import { useDialog } from "../context/DialogContext.jsx";
import Modal from "./Modal.jsx";

export default function AccountSettings({ user, onClose, onProfileUpdated, onAccountDeleted }) {
  const { confirm } = useDialog();

  const [displayName, setDisplayName] = useState(user.displayName);
  const [profileStatus, setProfileStatus] = useState(null);
  const [profileError, setProfileError] = useState(null);
  const [savingProfile, setSavingProfile] = useState(false);

  const [passwords, setPasswords] = useState({ current: "", next: "" });
  const [passwordStatus, setPasswordStatus] = useState(null);
  const [passwordError, setPasswordError] = useState(null);
  const [changingPassword, setChangingPassword] = useState(false);

  const [deletePassword, setDeletePassword] = useState("");
  const [deleteError, setDeleteError] = useState(null);
  const [deleting, setDeleting] = useState(false);

  async function handleSaveProfile(e) {
    e.preventDefault();
    setProfileError(null);
    setProfileStatus(null);
    if (!displayName.trim()) return;
    setSavingProfile(true);
    try {
      const updated = await auth.updateProfile(displayName.trim());
      onProfileUpdated(updated);
      setProfileStatus("Saved");
    } catch (err) {
      setProfileError(errorMessage(err));
    } finally {
      setSavingProfile(false);
    }
  }

  async function handleChangePassword(e) {
    e.preventDefault();
    setPasswordError(null);
    setPasswordStatus(null);
    setChangingPassword(true);
    try {
      await auth.changePassword(passwords.current, passwords.next);
      setPasswords({ current: "", next: "" });
      setPasswordStatus("Password updated. Other sessions were logged out.");
    } catch (err) {
      setPasswordError(errorMessage(err));
    } finally {
      setChangingPassword(false);
    }
  }

  async function handleDeleteAccount(e) {
    e.preventDefault();
    setDeleteError(null);
    const ok = await confirm({
      title: "Delete your account?",
      message: "Your account and every board you own will be permanently deleted. This can't be undone.",
      confirmLabel: "Yes, delete it",
      danger: true,
    });
    if (!ok) return;
    setDeleting(true);
    try {
      await auth.deleteAccount(deletePassword);
      onAccountDeleted();
    } catch (err) {
      setDeleteError(errorMessage(err));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Modal onClose={onClose} labelledBy="account-settings-title">
      <div className="modal-header">
        <h2 id="account-settings-title">Account settings</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Close">
          <X size={18} />
        </button>
      </div>

      <form className="settings-section" onSubmit={handleSaveProfile}>
        <h3>Profile</h3>
        <label htmlFor="displayName">Display name</label>
        <input
          id="displayName"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
        />
        {profileError && <div className="form-error">{profileError}</div>}
        {profileStatus && <div className="form-notice">{profileStatus}</div>}
        <button type="submit" className="btn-secondary" disabled={savingProfile}>
          {savingProfile ? "Saving…" : "Save name"}
        </button>
      </form>

      <form className="settings-section" onSubmit={handleChangePassword}>
        <h3>{user.hasPassword ? "Change password" : "Set a password"}</h3>
        {!user.hasPassword && (
          <p className="settings-danger-copy">
            You signed up with Google and have no password yet — set one to also be able to log
            in with your email.
          </p>
        )}
        {user.hasPassword && (
          <>
            <label htmlFor="currentPassword">Current password</label>
            <input
              id="currentPassword"
              type="password"
              value={passwords.current}
              onChange={(e) => setPasswords({ ...passwords, current: e.target.value })}
              required
            />
          </>
        )}
        <label htmlFor="newPassword">New password</label>
        <input
          id="newPassword"
          type="password"
          minLength={6}
          value={passwords.next}
          onChange={(e) => setPasswords({ ...passwords, next: e.target.value })}
          required
        />
        {passwordError && <div className="form-error">{passwordError}</div>}
        {passwordStatus && <div className="form-notice">{passwordStatus}</div>}
        <button type="submit" className="btn-secondary" disabled={changingPassword}>
          {changingPassword ? "Saving…" : user.hasPassword ? "Update password" : "Set password"}
        </button>
      </form>

      <form className="settings-section settings-danger" onSubmit={handleDeleteAccount}>
        <h3>Delete account</h3>
        <p className="settings-danger-copy">
          Permanently deletes your account and every board you own. This can't be undone.
        </p>
        {user.hasPassword && (
          <>
            <label htmlFor="deletePassword">Confirm your password</label>
            <input
              id="deletePassword"
              type="password"
              value={deletePassword}
              onChange={(e) => setDeletePassword(e.target.value)}
              required
            />
          </>
        )}
        {deleteError && <div className="form-error">{deleteError}</div>}
        <button type="submit" className="btn-danger" disabled={deleting}>
          {deleting ? "Deleting…" : "Delete my account"}
        </button>
      </form>
    </Modal>
  );
}
