import { usePlayerList } from "../hooks/usePlayer";
import PlayerCard from "../components/PlayerCard";
import Pagination from "../components/Pagination";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ErrorState from "../components/ErrorState";

export default function Players() {
  const {
    players,
    loading,
    error,
    page,
    totalPages,
    setPage,
    search,
    setSearch,
    refresh,
  } = usePlayerList();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Players</h1>
        <p className="mt-1 text-sm text-surface-400">
          Browse registered players
        </p>
      </div>

      <div className="max-w-md">
        <input
          type="text"
          placeholder="Search players..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          className="input"
        />
      </div>

      {loading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState message={error} onRetry={refresh} />
      ) : players.length === 0 ? (
        <EmptyState message="No players found." />
      ) : (
        <div className="space-y-3">
          {players.map((p) => (
            <PlayerCard key={p.id} player={p} />
          ))}
        </div>
      )}

      <Pagination
        page={page}
        totalPages={totalPages}
        onPageChange={setPage}
      />
    </div>
  );
}
