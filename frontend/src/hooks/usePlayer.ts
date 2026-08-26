import { useState, useEffect, useCallback } from "react";
import { playerApi } from "../api/endpoints";
import { normalizeApiError } from "../api/client";
import type { PlayerResponse, PageResponse } from "../types";

export function usePlayer(id: number | null) {
  const [player, setPlayer] = useState<PlayerResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchPlayer = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const { data } = await playerApi.get(id);
      setPlayer(data);
    } catch (err) {
      setError(normalizeApiError(err));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchPlayer();
  }, [fetchPlayer]);

  return { player, loading, error, refresh: fetchPlayer };
}

export function usePlayerList() {
  const [data, setData] = useState<PageResponse<PlayerResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");

  const fetchPlayers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data: res } = await playerApi.list(page, 20, search || undefined);
      setData(res);
    } catch (err) {
      setError(normalizeApiError(err));
    } finally {
      setLoading(false);
    }
  }, [page, search]);

  useEffect(() => {
    fetchPlayers();
  }, [fetchPlayers]);

  const items = data?.items || data?.content || [];

  return {
    players: items,
    totalElements: data?.totalElements || 0,
    totalPages: data?.totalPages || 0,
    loading,
    error,
    page,
    setPage,
    search,
    setSearch,
    refresh: fetchPlayers,
  };
}
