import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import Pagination from "../components/Pagination";

describe("Pagination", () => {
  it("does not render when totalPages <= 1", () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} onPageChange={vi.fn()} />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders page info and buttons", () => {
    render(
      <Pagination page={1} totalPages={5} onPageChange={vi.fn()} />,
    );
    expect(screen.getByText("Page 2 of 5")).toBeInTheDocument();
    expect(screen.getByText("Previous")).toBeInTheDocument();
    expect(screen.getByText("Next")).toBeInTheDocument();
  });

  it("disables Previous on first page", () => {
    render(
      <Pagination page={0} totalPages={3} onPageChange={vi.fn()} />,
    );
    expect(screen.getByText("Previous")).toBeDisabled();
  });

  it("disables Next on last page", () => {
    render(
      <Pagination page={2} totalPages={3} onPageChange={vi.fn()} />,
    );
    expect(screen.getByText("Next")).toBeDisabled();
  });

  it("calls onPageChange with correct values", () => {
    const onPageChange = vi.fn();
    render(
      <Pagination page={1} totalPages={5} onPageChange={onPageChange} />,
    );
    fireEvent.click(screen.getByText("Previous"));
    expect(onPageChange).toHaveBeenCalledWith(0);
    fireEvent.click(screen.getByText("Next"));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });
});
