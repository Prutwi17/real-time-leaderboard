import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { leaderboardApi, sportApi } from "../api/endpoints";
import SportSelector from "../components/SportSelector";
import LeaderboardTable from "../components/LeaderboardTable";
import LiveStatus from "../components/LiveStatus";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ErrorState from "../components/ErrorState";
import { useWebSocket, type ConnectionStatus } from "../hooks/useWebSocket";
import type { LeaderboardEntry, LeaderboardUpdateMessage, SportCode } from "../types";

export default function Home() {
  const [sport, setSport] = useState<SportCode>("FOOTBALL");
  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [totalPlayers, setTotalPlayers] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [wsStatus, setWsStatus] = useState<ConnectionStatus>("OFFLINE");
  const [updatedIds, setUpdatedIds] = useState<Set<number>>(new Set());

  const handleWsMessage = useCallback(
    (msg: LeaderboardUpdateMessage) => {
      if (msg.sport.toUpperCase() === sport) {
        setEntries(msg.leaderboard.entries);
        setTotalPlayers(msg.leaderboard.totalPlayers);
        const ids = new Set(msg.leaderboard.entries.map((e) => e.userId));
        setUpdatedIds(ids);
        setTimeout(() => setUpdatedIds(new Set()), 1500);
      }
    },
    [sport],
  );

  const { status } = useWebSocket({ sport, onMessage: handleWsMessage });

  useEffect(() => {
    setWsStatus(status);
  }, [status]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    leaderboardApi
      .top(sport, 10)
      .then(({ data }) => {
        setEntries(data.entries);
        setTotalPlayers(data.totalPlayers);
      })
      .catch((err) => {
        setError(
          err?.response?.data?.message || "Unable to load leaderboard.",
        );
      })
      .finally(() => setLoading(false));
  }, [sport]);

  return (
    <div className="space-y-8">
      <section className="text-center">
        <h1 className="mb-3 text-4xl font-bold tracking-tight text-white md:text-5xl">
          ⚡ Real-Time Leaderboard
        </h1>
        <p className="mx-auto max-w-2xl text-lg text-surface-400">
          Multi-sport leaderboard platform with live rankings powered by
          WebSocket, Kafka, and Redis.
        </p>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-4">
        <SportSelector current={sport} onSelect={setSport} />
        <LiveStatus status={wsStatus} />
      </div>

      <div className="card">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-white">
            Top 10 — {sport.charAt(0) + sport.slice(1).toLowerCase()}
          </h2>
          <Link
            to={`/leaderboards/${sport.toLowerCase()}`}
            className="text-sm text-brand-400 hover:text-brand-300"
          >
            View Full Leaderboard →
          </Link>
        </div>
        {loading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState message={error} />
        ) : entries.length === 0 ? (
          <EmptyState />
        ) : (
          <LeaderboardTable entries={entries} updatedIds={updatedIds} />
        )}
        {totalPlayers > 0 && (
          <p className="mt-3 text-right text-xs text-surface-500">
            {totalPlayers} player{totalPlayers !== 1 ? "s" : ""} ranked
          </p>
        )}
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <Link
          to="/leaderboards/football"
          className="card group hover:border-sport-football/50 transition-colors"
        >
          <div className="mb-2 flex items-center gap-2">
            <span className="h-3 w-3 rounded-full bg-sport-football" />
            <span className="font-semibold text-white">Football</span>
          </div>
          <p className="text-sm text-surface-400">
            Points, goals, and match rankings.
          </p>
        </Link>
        <Link
          to="/leaderboards/cricket"
          className="card group hover:border-sport-cricket/50 transition-colors"
        >
          <div className="mb-2 flex items-center gap-2">
            <span className="h-3 w-3 rounded-full bg-sport-cricket" />
            <span className="font-semibold text-white">Cricket</span>
          </div>
          <p className="text-sm text-surface-400">
            Runs, wickets, and match performance.
          </p>
        </Link>
        <Link
          to="/leaderboards/f1"
          className="card group hover:border-sport-f1/50 transition-colors"
        >
          <div className="mb-2 flex items-center gap-2">
            <span className="h-3 w-3 rounded-full bg-sport-f1" />
            <span className="font-semibold text-white">F1</span>
          </div>
          <p className="text-sm text-surface-400">
            Race positions, laps, and championship points.
          </p>
        </Link>
      </div>
    </div>
  );
}
