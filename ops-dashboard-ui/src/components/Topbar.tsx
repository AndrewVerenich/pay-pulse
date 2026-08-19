import { NavLink, useNavigate } from "react-router-dom";
import { Activity, LogOut } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";

export function Topbar() {
  const { username, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate("/login", { replace: true });
  };

  const navClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      "rounded-md px-3 py-1.5 text-sm transition-colors",
      isActive ? "bg-slate-800 text-slate-100" : "text-slate-400 hover:text-slate-200",
    );

  return (
    <header className="flex items-center justify-between border-b border-slate-800 bg-slate-900/70 px-6 py-3 backdrop-blur">
      <div className="flex items-center gap-6">
        <button
          onClick={() => navigate("/live")}
          className="flex items-center gap-2 text-lg font-semibold tracking-tight"
        >
          <Activity className="h-5 w-5 text-indigo-400" />
          PayPulse <span className="text-slate-500">Ops</span>
        </button>
        <nav className="flex items-center gap-1">
          <NavLink to="/live" className={navClass}>Live</NavLink>
          <NavLink to="/alerts" className={navClass}>Alerts</NavLink>
          <NavLink to="/rules" className={navClass}>Rules</NavLink>
          <NavLink to="/sagas/stuck" className={navClass}>Stuck</NavLink>
          <NavLink to="/health" className={navClass}>Health</NavLink>
        </nav>
      </div>
      <div className="flex items-center gap-4 text-sm text-slate-400">
        <span>{username ?? "operator"}</span>
        <Button variant="ghost" onClick={handleLogout} className="px-3 py-1.5">
          <LogOut className="h-4 w-4" />
          Logout
        </Button>
      </div>
    </header>
  );
}
