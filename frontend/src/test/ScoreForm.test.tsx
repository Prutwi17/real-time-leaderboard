import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi } from "vitest";
import ScoreForm from "../components/ScoreForm";

describe("ScoreForm", () => {
  it("renders form fields", () => {
    render(
      <MemoryRouter>
        <ScoreForm sportId={1} sportName="Football" onSubmit={vi.fn()} submitting={false} />
      </MemoryRouter>,
    );
    expect(screen.getByLabelText("Score Type")).toBeInTheDocument();
    expect(screen.getByLabelText("Score")).toBeInTheDocument();
    expect(screen.getByLabelText("Event Name (optional)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /submit score/i })).toBeInTheDocument();
  });

  it("shows appropriate score types for Football", () => {
    render(
      <MemoryRouter>
        <ScoreForm sportId={1} sportName="Football" onSubmit={vi.fn()} submitting={false} />
      </MemoryRouter>,
    );
    const select = screen.getByLabelText("Score Type") as HTMLSelectElement;
    expect(select.options.length).toBe(2);
    expect(select.options[0].value).toBe("POINTS");
    expect(select.options[1].value).toBe("GOALS");
  });

  it("calls onSubmit with form data", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <MemoryRouter>
        <ScoreForm sportId={1} sportName="Football" onSubmit={onSubmit} submitting={false} />
      </MemoryRouter>,
    );
    fireEvent.change(screen.getByLabelText("Score"), {
      target: { value: "100" },
    });
    fireEvent.click(screen.getByRole("button", { name: /submit score/i }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ sportId: 1, value: 100 }),
    );
  });
});
