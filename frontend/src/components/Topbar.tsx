import AnimatedMoney from "./AnimatedMoney";
import type { Totals } from "../types";
import type { Page } from "../App";

interface TopbarProps {
  page: Page;
  setPage: (p: Page) => void;
  totals: Totals | null;
  invariantPulse: boolean;
  authEmail: string | null;
  authRole: string | null;
  onLogout: () => void;
}

export default function Topbar({ page, setPage, totals, invariantPulse, authEmail, authRole, onLogout }: TopbarProps) {
  return (
    <nav className="topbar">
      <button className="topbar-logo" onClick={() => setPage("landing")}>FinLedger</button>
      <div className="topbar-links">
        <button className={`topbar-link ${page === "accounts" || page === "account-detail" ? "active" : ""}`}
          onClick={() => setPage("accounts")}>Accounts</button>
        <button className={`topbar-link ${page === "pipeline" ? "active" : ""}`}
          onClick={() => setPage("pipeline")}>Pipeline</button>
        {authRole === "ADMIN" && (
          <button className={`topbar-link ${page === "admin" ? "active" : ""}`}
            onClick={() => setPage("admin")}>Admin</button>
        )}
      </div>
      {totals && (
        <div className={`topbar-inv ${invariantPulse ? "pulse" : ""}`}>
          <AnimatedMoney value={totals.cashPool} /> = <AnimatedMoney value={totals.totalLiabilities} />
          <span className="topbar-badge">{totals.balanced ? "balanced" : "UNBALANCED"}</span>
        </div>
      )}
      {authEmail && (
        <div className="topbar-user">
          <span className="topbar-role-badge">{authRole}</span>
          <span className="topbar-email">{authEmail.split("@")[0]}</span>
          <button className="topbar-logout" onClick={onLogout}>Sign out</button>
        </div>
      )}
    </nav>
  );
}
