import { useState, useCallback, useEffect } from "react";
import { useParams } from "react-router-dom";
import { leaderboardApi } from "../api/endpoints";
import { normalizeApiError } from "../api/client";
import { useLeaderboard } from "../hooks/useLeaderboard";
import { useWebSocket } from "../hooks/useWebSocket";
import { useAuth } from "../context/AuthContext";
import SportSelector from "../components/SportSelector";
import LeaderboardTable from "../components/LeaderboardTable";
import LiveStatus from "../components/LiveStatus";
import Pagination from "../components/Pagination";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ErrorState from "../components/ErrorState";
import type {
  SportCode,
  LeaderboardUpdateMessage,
  PlayerRankResponse,
} from "../types";

export default function Leaderboard() {
  const { sport: sportParam } = useParams<{ sport: string }>();
  const sport = (sportParam?.toUpperCase() || "FOOTBALL") as SportCode;
  const { isAuthenticated } = useAuth();

  const {
    entries,
    totalPlayers,
    loading,
    error,
    page,
    totalPages,
    setPage,
    handleWsUpdate,
    refresh,
  } = useLeaderboard(sport);

  const { status } = useWebSocket({ sport, onMessage: handleWsUpdate });

  const [myRank, setMyRank] = useState<PlayerRankResponse | null>(null);
  const [updatedIds, setUpdatedIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (!isAuthenticated) return;
    leaderboardApi
      .myRank(sport)
      .then(({ data }) => setMyRank(data))
      .catch(() => setMyRank(null));
  }, [sport, isAuthenticated]);

  const handleWs = useCallback(
    (msg: LeaderboardUpdateMessage) => {
      handleWsUpdate(msg);
      const ids = new Set(msg.leaderboard.entries.map((e) => e.userId));
      setUpdatedIds(ids);
      setTimeout(() => setUpdatedIds(new Set()), 1500);
    },
    [handleWsUpdate],
  );

  const { status: wsStatus } = useWebSocket({ sport, onMessage: handleWs });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">
            {sport.charAt(0) + sport.slice(1).toLowerCase()} Leaderboard
          </h1>
          <p className="mt-1 text-sm text-surface-400">
            {totalPlayers} player{totalPlayers !== 1 ? "s" : ""} ranked
          </p>
        </div>
        <div className="flex items-center gap-4">
          <LiveStatus status={wsStatus} />
        </div>
      </div>

      <SportSelector current={sport} />

      {isAuthenticated && myRank && (
        <div className="card flex items-center gap-6">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-surface-400">
              Your Rank
            </p>
            <p className="mt-1 text-3xl font-bold text-brand-400">
              #{myRank.rank}
            </p>
          </div>
          <div className="h-10 w-px bg-surface-700" />
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-surface-400">
              Your Score
            </p>
            <p className="mt-1 text-3xl font-bold text-white">
              {myRank.score.toLocaleString(undefined, {
                maximumFractionDigits: 2,
              })}
            </p>
          </div>
        </div>
      )}

      <div className="card">
        {loading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState message={error} onRetry={refresh} />
        ) : entries.length === 0 ? (
          <EmptyState />
        ) : (
          <>
            <LeaderboardTable entries={entries} updatedIds={updatedIds} />
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={setPage}
            />
          </>
        )}
      </div>
    </div>
  );
}
