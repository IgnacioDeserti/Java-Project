import { useState } from "react";
import { CheckCircle2 } from "lucide-react";
import { auth, errorMessage } from "../api/client.js";

export default function ResetPasswordPage({ token }) {
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState("idle"); // "idle" | "done" | "error"
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!token) {
      setStatus("error");
      setMessage("Missing reset token.");
      return;
    }
    setSubmitting(true);
    try {
      const res = await auth.resetPassword(token, password);
      setStatus("done");
      setMessage(res.message);
    } catch (err) {
      setStatus("error");
      setMessage(errorMessage(err, "This reset link is invalid or expired."));
    } finally {
      setSubmitting(false);
    }
  }

  if (status === "done") {
    return (
      <div className="app-shell centered">
        <div className="login-form" style={{ alignItems: "center", textAlign: "center" }}>
          <CheckCircle2 size={32} color="var(--color-success)" />
          <h2>Password reset</h2>
          <p>{message}</p>
          <a className="link-btn" href="/">
            Go to login
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell centered">
      <form className="login-form" onSubmit={handleSubmit}>
        <h2>Choose a new password</h2>
        <input
          type="password"
          placeholder="New password"
          minLength={6}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {status === "error" && <div className="form-error">{message}</div>}
        <button type="submit" disabled={submitting}>
          {submitting ? "Please wait…" : "Reset password"}
        </button>
        <a className="link-btn" href="/">
          Cancel
        </a>
      </form>
    </div>
  );
}
