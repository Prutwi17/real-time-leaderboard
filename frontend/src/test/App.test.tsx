import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";
import App from "../App";
import { AuthProvider } from "../context/AuthContext";
import { ToastProvider } from "../context/ToastContext";

function renderApp(initialRoute = "/") {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <ToastProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe("App routing", () => {
  it("renders home page at /", () => {
    renderApp("/");
    expect(screen.getByText(/Real-Time Leaderboard/)).toBeInTheDocument();
  });

  it("renders login page at /login", () => {
    renderApp("/login");
    expect(screen.getAllByText("Sign In").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
  });

  it("renders register page at /register", () => {
    renderApp("/register");
    expect(screen.getAllByText("Create Account").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("renders leaderboard page at /leaderboards/football", () => {
    renderApp("/leaderboards/football");
    expect(screen.getByText(/Football Leaderboard/)).toBeInTheDocument();
  });
});
