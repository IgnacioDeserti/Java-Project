import { useState } from "react";
import { auth, errorMessage, googleLoginUrl, isEmailNotVerified } from "../api/client.js";
import { useDialog } from "../context/DialogContext.jsx";
import GoogleIcon from "./GoogleIcon.jsx";

export default function LoginForm({ onAuthenticated, notice }) {
  const { prompt } = useDialog();
  const [mode, setMode] = useState("login"); // "login" | "register"
  const [form, setForm] = useState({ email: "", password: "", displayName: "" });
  const [error, setError] = useState(null);
  const [info, setInfo] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [unverifiedEmail, setUnverifiedEmail] = useState(null);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setInfo(null);
    setUnverifiedEmail(null);
    setSubmitting(true);
    try {
      const payload = mode === "login" ? { email: form.email, password: form.password } : form;
      const response = mode === "login" ? await auth.login(payload) : await auth.register(payload);
      onAuthenticated({
        email: response.email,
        displayName: response.displayName,
        emailVerified: response.emailVerified,
        hasPassword: response.hasPassword,
      });
    } catch (err) {
      if (isEmailNotVerified(err)) {
        setError("Please verify your email before logging in.");
        setUnverifiedEmail(form.email);
      } else {
        setError(errorMessage(err));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResendVerification() {
    try {
      const res = await auth.resendVerification(unverifiedEmail);
      setInfo(res.message);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleForgotPassword() {
    const email = await prompt({
      title: "Reset your password",
      label: "Account email",
      initialValue: form.email,
      confirmLabel: "Send reset link",
    });
    if (!email) return;
    try {
      const res = await auth.forgotPassword(email);
      setInfo(res.message);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <form className="login-form" onSubmit={handleSubmit}>
      <h2>{mode === "login" ? "Log in" : "Create account"}</h2>

      <a className="google-btn" href={googleLoginUrl()}>
        <GoogleIcon />
        Continue with Google
      </a>

      <div className="form-divider">
        <span>or</span>
      </div>

      {notice && !error && !info && <div className="form-notice">{notice}</div>}

      {mode === "register" && (
        <input
          placeholder="Display name"
          value={form.displayName}
          onChange={(e) => setForm({ ...form, displayName: e.target.value })}
          required
        />
      )}
      <input
        type="email"
        placeholder="Email"
        value={form.email}
        onChange={(e) => setForm({ ...form, email: e.target.value })}
        required
      />
      <input
        type="password"
        placeholder="Password"
        minLength={6}
        value={form.password}
        onChange={(e) => setForm({ ...form, password: e.target.value })}
        required
      />

      {error && <div className="form-error">{error}</div>}
      {info && <div className="form-notice">{info}</div>}

      {unverifiedEmail && (
        <button type="button" className="link-btn" onClick={handleResendVerification}>
          Resend verification email
        </button>
      )}

      <button type="submit" disabled={submitting}>
        {submitting ? "Please wait…" : mode === "login" ? "Log in" : "Sign up"}
      </button>

      {mode === "login" && (
        <button type="button" className="link-btn" onClick={handleForgotPassword}>
          Forgot password?
        </button>
      )}

      <button
        type="button"
        className="link-btn"
        onClick={() => {
          setError(null);
          setInfo(null);
          setUnverifiedEmail(null);
          setMode(mode === "login" ? "register" : "login");
        }}
      >
        {mode === "login" ? "Need an account? Sign up" : "Already have an account? Log in"}
      </button>
    </form>
  );
}
