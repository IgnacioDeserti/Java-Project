import { useCallback, useEffect, useState } from "react";
import { KanbanSquare, LayoutGrid, LogOut, Plus, User, X } from "lucide-react";
import LoginForm from "./components/LoginForm.jsx";
import Board from "./components/Board.jsx";
import VerifyEmailPage from "./components/VerifyEmailPage.jsx";
import ResetPasswordPage from "./components/ResetPasswordPage.jsx";
import OAuthCallbackPage from "./components/OAuthCallbackPage.jsx";
import AccountSettings from "./components/AccountSettings.jsx";
import { useToast } from "./context/ToastContext.jsx";
import { useDialog } from "./context/DialogContext.jsx";
import {
  auth,
  boards as boardsApi,
  errorMessage,
  setSessionExpiredHandler,
  TOKEN_KEY,
} from "./api/client.js";

// No router library: the app only ever needs these two extra "pages", both reached by
// clicking a link in an email, so a plain pathname check is enough.
function useRoute() {
  const path = window.location.pathname;
  const token = new URLSearchParams(window.location.search).get("token");
  if (path === "/verify-email") return { page: "verify-email", token };
  if (path === "/reset-password") return { page: "reset-password", token };
  if (path === "/oauth-callback") return { page: "oauth-callback" };
  return { page: "app" };
}

export default function App() {
  const route = useRoute();
  const { showToast } = useToast();
  const { confirm, prompt } = useDialog();

  const [user, setUser] = useState(null);
  const [booting, setBooting] = useState(true);
  const [boardList, setBoardList] = useState([]);
  const [activeBoard, setActiveBoard] = useState(null);
  const [loading, setLoading] = useState(false);
  const [sessionNotice, setSessionNotice] = useState(null);
  const [showAccountSettings, setShowAccountSettings] = useState(false);

  const logout = useCallback(() => {
    auth.logout();
    setUser(null);
    setBoardList([]);
    setActiveBoard(null);
  }, []);

  // A refresh failure (expired/revoked refresh token) drops us back to the login screen.
  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser(null);
      setBoardList([]);
      setActiveBoard(null);
      setSessionNotice("Your session expired — please log in again.");
    });
  }, []);

  // Restore the session from a stored token, but only if the backend still accepts it.
  useEffect(() => {
    if (route.page !== "app") {
      setBooting(false);
      return;
    }
    if (!localStorage.getItem(TOKEN_KEY)) {
      setBooting(false);
      return;
    }
    auth
      .me()
      .then(setUser)
      .catch(() => {})
      .finally(() => setBooting(false));
  }, [route.page]);

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    boardsApi
      .list()
      .then(setBoardList)
      .catch((err) => showToast(errorMessage(err, "Could not load your boards"), { type: "error" }))
      .finally(() => setLoading(false));
  }, [user, showToast]);

  async function handleCreateBoard() {
    const name = await prompt({ title: "New board", label: "Board name", confirmLabel: "Create" });
    if (!name) return;
    try {
      const board = await boardsApi.create({ name });
      // The create response is the full board (with default columns); the list only
      // needs the summary, so we open it straight away.
      setBoardList((current) => [...current, board]);
      setActiveBoard(board);
    } catch (err) {
      showToast(errorMessage(err, "Could not create the board"), { type: "error" });
    }
  }

  async function handleDeleteBoard(boardId, name) {
    const ok = await confirm({
      title: "Delete board?",
      message: `"${name}" and all of its cards will be permanently deleted.`,
      confirmLabel: "Delete board",
      danger: true,
    });
    if (!ok) return;
    try {
      await boardsApi.remove(boardId);
      setBoardList((current) => current.filter((b) => b.id !== boardId));
      setActiveBoard((current) => (current?.id === boardId ? null : current));
    } catch (err) {
      showToast(errorMessage(err, "Could not delete the board"), { type: "error" });
    }
  }

  async function handleOpenBoard(boardId) {
    try {
      setActiveBoard(await boardsApi.get(boardId));
    } catch (err) {
      showToast(errorMessage(err, "Could not open the board"), { type: "error" });
    }
  }

  async function handleResendVerification() {
    try {
      const res = await auth.resendVerification(user.email);
      showToast(res.message, { type: "info" });
    } catch (err) {
      showToast(errorMessage(err), { type: "error" });
    }
  }

  if (route.page === "verify-email") {
    return <VerifyEmailPage token={route.token} />;
  }
  if (route.page === "reset-password") {
    return <ResetPasswordPage token={route.token} />;
  }
  if (route.page === "oauth-callback") {
    return <OAuthCallbackPage onAuthenticated={setUser} />;
  }

  if (booting) {
    return (
      <div className="app-shell centered">
        <span className="spinner" />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="app-shell centered">
        <LoginForm onAuthenticated={setUser} notice={sessionNotice} />
      </div>
    );
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>
          <span style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem" }}>
            <KanbanSquare size={22} strokeWidth={2.25} color="var(--color-accent)" />
            Kanban Board
          </span>
        </h1>
        <div className="app-header-right">
          <span className="user-badge">
            <User size={14} />
            {user.displayName}
          </span>
          <button onClick={() => setShowAccountSettings(true)}>Account</button>
          <button onClick={logout}>
            <LogOut size={14} style={{ marginRight: "0.35rem", verticalAlign: -2 }} />
            Log out
          </button>
        </div>
      </header>

      {showAccountSettings && (
        <AccountSettings
          user={user}
          onClose={() => setShowAccountSettings(false)}
          onProfileUpdated={(updated) => setUser({ ...user, ...updated })}
          onAccountDeleted={() => {
            setShowAccountSettings(false);
            setUser(null);
            setBoardList([]);
            setActiveBoard(null);
          }}
        />
      )}

      {!user.emailVerified && (
        <div className="banner-warning">
          Your email isn't verified yet.
          <button className="link-btn" onClick={handleResendVerification}>
            Resend verification email
          </button>
        </div>
      )}

      {!activeBoard && (
        <div className="board-list">
          <div className="board-list-header">
            <h2>Your boards</h2>
            <button className="primary-btn" onClick={handleCreateBoard}>
              <Plus size={16} />
              New board
            </button>
          </div>

          {loading && <p className="loading-copy">Loading boards…</p>}
          {!loading && boardList.length === 0 && (
            <p className="empty-hint">No boards yet — create one to get started.</p>
          )}
          {!loading && boardList.length > 0 && (
            <ul className="board-grid">
              {boardList.map((b) => (
                <li key={b.id}>
                  <button className="board-card" onClick={() => handleOpenBoard(b.id)}>
                    <span className="board-card-icon">
                      <LayoutGrid size={18} />
                    </span>
                    <span className="board-card-name">{b.name}</span>
                  </button>
                  <button
                    className="danger-link board-card-delete"
                    title="Delete board"
                    aria-label={`Delete board "${b.name}"`}
                    onClick={() => handleDeleteBoard(b.id, b.name)}
                  >
                    <X size={16} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {activeBoard && (
        <>
          <button className="back-btn" onClick={() => setActiveBoard(null)}>
            ← Back to boards
          </button>
          <Board
            board={activeBoard}
            onBoardChange={setActiveBoard}
            onBoardDeleted={() => {
              setActiveBoard(null);
              setBoardList((current) => current.filter((b) => b.id !== activeBoard.id));
              showToast("This board was deleted.", { type: "info" });
            }}
          />
        </>
      )}
    </div>
  );
}
