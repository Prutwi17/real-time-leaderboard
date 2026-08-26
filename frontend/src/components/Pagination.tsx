interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export default function Pagination({
  page,
  totalPages,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-center gap-2 py-4">
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        className="btn-secondary text-sm disabled:opacity-40"
      >
        Previous
      </button>
      <span className="px-3 text-sm text-surface-400">
        Page {page + 1} of {totalPages}
      </span>
      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        className="btn-secondary text-sm disabled:opacity-40"
      >
        Next
      </button>
    </div>
  );
}
