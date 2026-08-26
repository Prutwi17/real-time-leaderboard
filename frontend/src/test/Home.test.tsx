import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi } from "vitest";
import Home from "../pages/Home";
import { AuthProvider } from "../context/AuthContext";
import { ToastProvider } from "../context/ToastContext";

vi.mock("../api/endpoints", () => ({
  leaderboardApi: {
    top: vi.fn().mockResolvedValue({
      data: { entries: [], totalPlayers: 0, page: 0, size: 10 },
    }),
  },
  sportApi: {
    list: vi.fn().mockResolvedValue({ data: [] }),
  },
}));

vi.mock("../hooks/useWebSocket", () => ({
  useWebSocket: () => ({ status: "LIVE" }),
}));

function renderHome() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <Home />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe("Home page", () => {
  it("renders heading and description", () => {
    renderHome();
    expect(screen.getByText(/Real-Time Leaderboard/)).toBeInTheDocument();
    expect(screen.getByText(/Multi-sport leaderboard/)).toBeInTheDocument();
  });

  it("renders sport selector with three sports", () => {
    renderHome();
    const buttons = screen.getAllByRole("tab");
    expect(buttons.length).toBe(3);
  });

  it("renders empty leaderboard state", async () => {
    renderHome();
    expect(await screen.findByText("No leaderboard data available.")).toBeInTheDocument();
  });

  it("renders sport cards", () => {
    renderHome();
    const footballLinks = screen.getAllByText("Football");
    const cardLink = footballLinks.find((el) => el.closest("a")?.getAttribute("href") === "/leaderboards/football");
    expect(cardLink).toBeTruthy();
  });
});
