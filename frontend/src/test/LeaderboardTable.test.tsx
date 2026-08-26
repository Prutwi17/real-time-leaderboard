import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";
import LeaderboardTable from "../components/LeaderboardTable";
import type { LeaderboardEntry } from "../types";

const mockEntries: LeaderboardEntry[] = [
  { rank: 1, userId: 101, score: 950 },
  { rank: 2, userId: 102, score: 870 },
  { rank: 3, userId: 103, score: 820 },
];

function renderTable(entries: LeaderboardEntry[] = mockEntries) {
  return render(
    <MemoryRouter>
      <LeaderboardTable entries={entries} />
    </MemoryRouter>,
  );
}

describe("LeaderboardTable", () => {
  it("renders rank, player, and score columns", () => {
    renderTable();
    expect(screen.getByText("Rank")).toBeInTheDocument();
    expect(screen.getByText("Player")).toBeInTheDocument();
    expect(screen.getByText("Score")).toBeInTheDocument();
  });

  it("renders player entries", () => {
    renderTable();
    expect(screen.getByText("Player #101")).toBeInTheDocument();
    expect(screen.getByText("Player #102")).toBeInTheDocument();
    expect(screen.getByText("Player #103")).toBeInTheDocument();
  });

  it("displays scores", () => {
    renderTable();
    expect(screen.getByText("950")).toBeInTheDocument();
    expect(screen.getByText("870")).toBeInTheDocument();
    expect(screen.getByText("820")).toBeInTheDocument();
  });

  it("shows empty message when no entries", () => {
    renderTable([]);
    expect(screen.getByText("No data available")).toBeInTheDocument();
  });
});
