import { useEffect, useState } from "react";
import { XCircle } from "lucide-react";
import { auth } from "../api/client.js";

/**
 * Lands here after the Google OAuth redirect dance completes. The backend puts the new
 * session in the URL fragment (never sent to servers/proxies, unlike a query string) —
 * grab it, store it the same way a normal login would, and hand off to the app shell.
 */
export default function OAuthCallbackPage({ onAuthenticated }) {
  const [error, setError] = useState(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1));
    const token = params.get("token");
    const refreshToken = params.get("refreshToken");

    if (params.get("error") || !token || !refreshToken) {
      setError("Google sign-in didn't complete. Please try again.");
      return;
    }

    auth.completeOAuthLogin(token, refreshToken);
    auth
      .me()
      .then((user) => {
        window.history.replaceState(null, "", "/");
        onAuthenticated(user);
      })
      .catch(() => setError("Google sign-in didn't complete. Please try again."));
  }, [onAuthenticated]);

  if (error) {
    return (
      <div className="app-shell centered">
        <div className="login-form" style={{ alignItems: "center", textAlign: "center" }}>
          <XCircle size={32} color="var(--color-danger)" />
          <h2>Sign-in failed</h2>
          <p className="form-error">{error}</p>
          <a className="link-btn" href="/">
            Back to login
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell centered">
      <span className="spinner" />
    </div>
  );
}
