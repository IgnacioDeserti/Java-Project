import { useEffect, useState } from "react";
import { CheckCircle2, XCircle } from "lucide-react";
import { auth, errorMessage } from "../api/client.js";

export default function VerifyEmailPage({ token }) {
  const [status, setStatus] = useState("verifying"); // "verifying" | "ok" | "error"
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setMessage("Missing verification token.");
      return;
    }
    auth
      .verifyEmail(token)
      .then((res) => {
        setStatus("ok");
        setMessage(res.message);
      })
      .catch((err) => {
        setStatus("error");
        setMessage(errorMessage(err, "This verification link is invalid or expired."));
      });
  }, [token]);

  return (
    <div className="app-shell centered">
      <div className="login-form" style={{ alignItems: "center", textAlign: "center" }}>
        {status === "verifying" && <span className="spinner" />}
        {status === "ok" && <CheckCircle2 size={32} color="var(--color-success)" />}
        {status === "error" && <XCircle size={32} color="var(--color-danger)" />}
        <h2>Email verification</h2>
        {status === "verifying" && <p className="loading-copy">Verifying…</p>}
        {status !== "verifying" && (
          <p className={status === "error" ? "form-error" : ""}>{message}</p>
        )}
        <a className="link-btn" href="/">
          Go to login
        </a>
      </div>
    </div>
  );
}
