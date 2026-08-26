import { useState, useCallback } from "react";
import { scoreApi } from "../api/endpoints";
import { normalizeApiError } from "../api/client";
import type { CreateScoreRequest, ScoreResponse, PageResponse } from "../types";

export function useScores() {
  const [data, setData] = useState<PageResponse<ScoreResponse> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const fetchScores = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data: res } = await scoreApi.mine(page, 20);
      setData(res);
    } catch (err) {
      setError(normalizeApiError(err));
    } finally {
      setLoading(false);
    }
  }, [page]);

  const items = data?.content || data?.items || [];

  return {
    scores: items,
    totalElements: data?.totalElements || 0,
    totalPages: data?.totalPages || 0,
    loading,
    error,
    page,
    setPage,
    fetchScores,
  };
}

export function useScoreSubmit() {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<ScoreResponse | null>(null);

  const submit = useCallback(async (req: CreateScoreRequest) => {
    setSubmitting(true);
    setError(null);
    setLastResult(null);
    try {
      const { data } = await scoreApi.submit(req);
      setLastResult(data);
      return data;
    } catch (err) {
      const msg = normalizeApiError(err);
      setError(msg);
      throw new Error(msg);
    } finally {
      setSubmitting(false);
    }
  }, []);

  return { submit, submitting, error, lastResult };
}
