import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi } from "vitest";
import Login from "../pages/Login";
import { AuthProvider } from "../context/AuthContext";
import { ToastProvider } from "../context/ToastContext";

vi.mock("../api/endpoints", () => ({
  authApi: {
    login: vi.fn(),
    me: vi.fn().mockRejectedValue(new Error("no session")),
  },
}));

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <ToastProvider>
        <AuthProvider>
          <Login />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe("Login page", () => {
  it("renders sign in form", () => {
    renderLogin();
    expect(screen.getAllByText("Sign In").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("shows link to register", () => {
    renderLogin();
    expect(screen.getByText("Register")).toHaveAttribute("href", "/register");
  });
});
