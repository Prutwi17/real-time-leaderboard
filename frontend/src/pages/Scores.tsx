import { useState, useEffect } from "react";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { sportApi } from "../api/endpoints";
import { useScoreSubmit, useScores } from "../hooks/useScores";
import ScoreForm from "../components/ScoreForm";
import Pagination from "../components/Pagination";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ErrorState from "../components/ErrorState";
import type { SportResponse, ScoreType } from "../types";

export default function Scores() {
  const { user } = useAuth();
  const { addToast } = useToast();
  const [sports, setSports] = useState<SportResponse[]>([]);
  const [selectedSport, setSelectedSport] = useState<SportResponse | null>(null);
  const { submit, submitting } = useScoreSubmit();
  const {
    scores,
    loading,
    error,
    page,
    totalPages,
    setPage,
    fetchScores,
  } = useScores();

  useEffect(() => {
    sportApi.list().then(({ data }) => {
      setSports(data);
      if (data.length > 0) setSelectedSport(data[0]);
    });
  }, []);

  useEffect(() => {
    fetchScores();
  }, [fetchScores]);

  const handleSubmit = async (req: {
    sportId: number;
    value: number;
    scoreType: ScoreType;
    eventName?: string;
  }) => {
    await submit({
      sportId: req.sportId,
      value: req.value,
      scoreType: req.scoreType,
      eventName: req.eventName,
      submissionId: `fe-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    });
    addToast(
      "Score submitted! Waiting for Kafka → Redis → WebSocket pipeline.",
      "success",
    );
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">Submit Score</h1>
        <p className="mt-1 text-sm text-surface-400">
          Submit a score that flows through Kafka → Redis → WebSocket → Live
          Leaderboard.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div className="card">
          <h2 className="mb-4 text-lg font-semibold text-white">New Score</h2>
          {selectedSport ? (
            <ScoreForm
              sportId={selectedSport.id}
              sportName={selectedSport.name}
              onSubmit={handleSubmit}
              submitting={submitting}
            />
          ) : (
            <LoadingState message="Loading sports..." />
          )}

          {sports.length > 1 && (
            <div className="mt-4">
              <label className="mb-1 block text-sm font-medium text-surface-300">
                Sport
              </label>
              <select
                value={selectedSport?.id || ""}
                onChange={(e) => {
                  const s = sports.find((sp) => sp.id === Number(e.target.value));
                  if (s) setSelectedSport(s);
                }}
                className="input"
              >
                {sports.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>

        <div className="card">
          <h2 className="mb-4 text-lg font-semibold text-white">My Scores</h2>
          {loading ? (
            <LoadingState />
          ) : error ? (
            <ErrorState message={error} />
          ) : scores.length === 0 ? (
            <EmptyState message="No scores submitted yet." />
          ) : (
            <div className="space-y-2">
              {scores.map((s) => (
                <div
                  key={s.id}
                  className="flex items-center justify-between rounded-lg bg-surface-700/30 px-4 py-2.5"
                >
                  <div>
                    <p className="text-sm font-medium text-surface-100">
                      {s.scoreType.replace("_", " ")}
                      {s.eventName ? ` — ${s.eventName}` : ""}
                    </p>
                    <p className="text-xs text-surface-500">
                      {new Date(s.recordedAt).toLocaleString()}
                    </p>
                  </div>
                  <span className="font-mono font-semibold text-brand-400">
                    {s.value}
                  </span>
                </div>
              ))}
            </div>
          )}
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </div>
      </div>
    </div>
  );
}
