import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi } from "vitest";
import ProtectedRoute from "../components/ProtectedRoute";
import { AuthProvider } from "../context/AuthContext";
import { ToastProvider } from "../context/ToastContext";

vi.mock("../api/endpoints", () => ({
  authApi: {
    login: vi.fn(),
    me: vi.fn().mockRejectedValue(new Error("no session")),
    logout: vi.fn(),
  },
}));

function renderProtected(initialRoute: string) {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <ToastProvider>
        <AuthProvider>
          <ProtectedRoute>
            <div>Protected Content</div>
          </ProtectedRoute>
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("does not render protected content when unauthenticated", async () => {
    renderProtected("/scores");
    // After loading completes, protected content should not be visible
    await vi.waitFor(() => {
      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });
  });
});
