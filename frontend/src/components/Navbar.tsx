import { Link, useLocation, useNavigate } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../context/AuthContext";

export default function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  const navLinks = [
    { to: "/", label: "Home" },
    { to: "/leaderboards/football", label: "Leaderboards" },
    { to: "/players", label: "Players" },
    ...(isAuthenticated ? [{ to: "/scores", label: "Scores" }] : []),
    ...(user?.role === "ADMIN"
      ? [{ to: "/admin", label: "Admin" }]
      : []),
  ];

  return (
    <nav className="sticky top-0 z-40 border-b border-surface-700/50 bg-surface-950/80 backdrop-blur-md">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
        <Link to="/" className="flex items-center gap-2 text-lg font-bold">
          <span className="text-brand-400">⚡</span>
          <span className="text-surface-100">Leaderboard</span>
        </Link>

        <div className="hidden items-center gap-1 md:flex">
          {navLinks.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={`rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                location.pathname === link.to ||
                (link.to !== "/" && location.pathname.startsWith(link.to))
                  ? "bg-surface-700 text-white"
                  : "text-surface-300 hover:bg-surface-800 hover:text-white"
              }`}
            >
              {link.label}
            </Link>
          ))}
        </div>

        <div className="hidden items-center gap-3 md:flex">
          {isAuthenticated ? (
            <>
              <span className="text-sm text-surface-400">
                {user?.username}
                {user?.role === "ADMIN" && (
                  <span className="ml-1 rounded bg-brand-600 px-1.5 py-0.5 text-xs text-white">
                    ADMIN
                  </span>
                )}
              </span>
              <button onClick={handleLogout} className="btn-ghost text-sm">
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn-ghost text-sm">
                Login
              </Link>
              <Link to="/register" className="btn-primary text-sm">
                Register
              </Link>
            </>
          )}
        </div>

        <button
          className="md:hidden"
          onClick={() => setMobileOpen(!mobileOpen)}
          aria-label="Toggle navigation"
        >
          <svg className="h-6 w-6 text-surface-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            {mobileOpen ? (
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            ) : (
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            )}
          </svg>
        </button>
      </div>

      {mobileOpen && (
        <div className="border-t border-surface-700 px-4 pb-4 pt-2 md:hidden">
          {navLinks.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              onClick={() => setMobileOpen(false)}
              className="block rounded-lg px-3 py-2 text-sm font-medium text-surface-300 hover:bg-surface-800 hover:text-white"
            >
              {link.label}
            </Link>
          ))}
          <div className="mt-3 border-t border-surface-700 pt-3">
            {isAuthenticated ? (
              <div className="flex items-center justify-between">
                <span className="text-sm text-surface-400">{user?.username}</span>
                <button onClick={handleLogout} className="btn-ghost text-sm">
                  Logout
                </button>
              </div>
            ) : (
              <div className="flex gap-2">
                <Link to="/login" onClick={() => setMobileOpen(false)} className="btn-ghost text-sm flex-1 text-center">
                  Login
                </Link>
                <Link to="/register" onClick={() => setMobileOpen(false)} className="btn-primary text-sm flex-1 text-center">
                  Register
                </Link>
              </div>
            )}
          </div>
        </div>
      )}
    </nav>
  );
}
