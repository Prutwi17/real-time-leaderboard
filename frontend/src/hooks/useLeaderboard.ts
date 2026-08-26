import { useState, useEffect, useCallback } from "react";
import { leaderboardApi } from "../api/endpoints";
import { normalizeApiError } from "../api/client";
import type {
  LeaderboardEntry,
  LeaderboardResponse,
  LeaderboardUpdateMessage,
} from "../types";

interface UseLeaderboardResult {
  entries: LeaderboardEntry[];
  totalPlayers: number;
  loading: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  setPage: (p: number) => void;
  handleWsUpdate: (msg: LeaderboardUpdateMessage) => void;
  refresh: () => Promise<void>;
}

export function useLeaderboard(sport: string): UseLeaderboardResult {
  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [totalPlayers, setTotalPlayers] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const pageSize = 20;

  const fetchLeaderboard = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await leaderboardApi.page(sport, page, pageSize);
      setEntries(data.entries);
      setTotalPlayers(data.totalPlayers);
      setTotalPages(Math.ceil(data.totalPlayers / pageSize) || 1);
    } catch (err) {
      setError(normalizeApiError(err));
    } finally {
      setLoading(false);
    }
  }, [sport, page]);

  useEffect(() => {
    setPage(0);
  }, [sport]);

  useEffect(() => {
    fetchLeaderboard();
  }, [fetchLeaderboard]);

  const handleWsUpdate = useCallback(
    (msg: LeaderboardUpdateMessage) => {
      if (msg.sport.toUpperCase() === sport.toUpperCase() && page === 0) {
        setEntries(msg.leaderboard.entries);
        setTotalPlayers(msg.leaderboard.totalPlayers);
        setTotalPages(
          Math.ceil(msg.leaderboard.totalPlayers / pageSize) || 1,
        );
      }
    },
    [sport, page],
  );

  return {
    entries,
    totalPlayers,
    loading,
    error,
    page,
    totalPages,
    setPage,
    handleWsUpdate,
    refresh: fetchLeaderboard,
  };
}
