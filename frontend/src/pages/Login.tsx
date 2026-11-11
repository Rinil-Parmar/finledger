import { useState } from "react";
import { api, setToken } from "../api/client";
import type { Page } from "../App";

interface LoginProps {
  setPage: (p: Page) => void;
  onAuth: (email: string, role: string) => void;
  onRefresh: () => void;
  showSuccess: (msg: string) => void;
}

export default function Login({ setPage, onAuth, onRefresh, showSuccess }: LoginProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [role, setRole] = useState("USER");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleAuth() {
    setLoading(true); setError(null);
    try {
      const res = mode === "login"
        ? await api.login(email, password)
        : await api.register(email, password, role);
      setToken(res.token);
      onAuth(res.email, res.role);
      setEmail(""); setPassword("");
      setPage("accounts");
      onRefresh();
      showSuccess(mode === "login" ? "Logged in" : "Account created");
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <button className="login-back" onClick={() => setPage("landing")}>&larr; Back</button>
        <div className="login-logo">FinLedger</div>
        <h1 className="login-title">{mode === "login" ? "Sign in" : "Create account"}</h1>
        <p className="login-sub">
          {mode === "login"
            ? "Enter your credentials to access the dashboard"
            : "Register to start using the ledger system"}
        </p>

        {error && <div className="login-error">{error}</div>}

        <div className="login-form">
          <label className="login-label">Email</label>
          <input type="email" value={email} onChange={e => setEmail(e.target.value)}
            placeholder="you@example.com" className="login-input"
            onKeyDown={e => e.key === "Enter" && handleAuth()} />

          <label className="login-label">Password</label>
          <input type="password" value={password} onChange={e => setPassword(e.target.value)}
            placeholder={mode === "register" ? "Min 6 characters" : "Your password"} className="login-input"
            onKeyDown={e => e.key === "Enter" && handleAuth()} />

          {mode === "register" && (
            <>
              <label className="login-label">Role</label>
              <div className="login-roles">
                <button className={`login-role ${role === "USER" ? "active" : ""}`}
                  onClick={() => setRole("USER")}>
                  <span className="login-role-name">User</span>
                  <span className="login-role-desc">View, deposit, withdraw, transfer</span>
                </button>
                <button className={`login-role ${role === "ADMIN" ? "active" : ""}`}
                  onClick={() => setRole("ADMIN")}>
                  <span className="login-role-name">Admin</span>
                  <span className="login-role-desc">Full access + create accounts, outbox, recon</span>
                </button>
              </div>
            </>
          )}

          <button onClick={handleAuth} disabled={loading || !email || !password} className="btn-primary login-submit">
            {loading ? <span className="spinner" /> : mode === "login" ? "Sign In" : "Create Account"}
          </button>
        </div>

        <div className="login-switch">
          {mode === "login" ? (
            <>No account? <button onClick={() => { setMode("register"); setError(null); }}>Create one</button></>
          ) : (
            <>Have an account? <button onClick={() => { setMode("login"); setError(null); }}>Sign in</button></>
          )}
        </div>
      </div>
    </div>
  );
}
