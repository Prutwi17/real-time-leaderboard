import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi } from "vitest";
import SportSelector from "../components/SportSelector";

describe("SportSelector", () => {
  it("renders all three sports", () => {
    render(
      <MemoryRouter>
        <SportSelector />
      </MemoryRouter>,
    );
    expect(screen.getByText("Football")).toBeInTheDocument();
    expect(screen.getByText("Cricket")).toBeInTheDocument();
    expect(screen.getByText("F1")).toBeInTheDocument();
  });

  it("calls onSelect when a sport is clicked", () => {
    const onSelect = vi.fn();
    render(
      <MemoryRouter>
        <SportSelector onSelect={onSelect} />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByText("Cricket"));
    expect(onSelect).toHaveBeenCalledWith("CRICKET");
  });

  it("links to leaderboard pages when no onSelect", () => {
    render(
      <MemoryRouter>
        <SportSelector />
      </MemoryRouter>,
    );
    expect(screen.getByText("Football").closest("a")).toHaveAttribute(
      "href",
      "/leaderboards/football",
    );
  });
});
