import { useState, useEffect } from "react";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import {
  sportApi,
  competitionApi,
  playerApi,
} from "../api/endpoints";
import { normalizeApiError } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import type {
  SportResponse,
  CompetitionResponse,
  PlayerResponse,
  PageResponse,
} from "../types";

export default function Admin() {
  const { user } = useAuth();
  const { addToast } = useToast();
  const [tab, setTab] = useState<"sports" | "competitions" | "players">("sports");

  const [sports, setSports] = useState<SportResponse[]>([]);
  const [competitions, setCompetitions] = useState<CompetitionResponse[]>([]);
  const [players, setPlayers] = useState<PlayerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAll = async () => {
    setLoading(true);
    setError(null);
    try {
      const [sportsRes, compsRes, playersRes] = await Promise.all([
        sportApi.list(),
        competitionApi.list(),
        playerApi.list(0, 50),
      ]);
      setSports(sportsRes.data);
      setCompetitions(compsRes.data);
      setPlayers(playersRes.data.items || playersRes.data.content || []);
    } catch (err) {
      setError(normalizeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
  }, []);

  const toggleSport = async (sport: SportResponse) => {
    try {
      await sportApi.setStatus(sport.id, { active: !sport.active });
      addToast(`Sport ${sport.name} ${sport.active ? "deactivated" : "activated"}.`, "success");
      loadAll();
    } catch (err) {
      addToast(normalizeApiError(err), "error");
    }
  };

  const toggleCompetition = async (comp: CompetitionResponse) => {
    try {
      await competitionApi.setStatus(comp.id, { active: !comp.active });
      addToast(`Competition ${comp.name} ${comp.active ? "deactivated" : "activated"}.`, "success");
      loadAll();
    } catch (err) {
      addToast(normalizeApiError(err), "error");
    }
  };

  const deactivatePlayer = async (player: PlayerResponse) => {
    try {
      await playerApi.deactivate(player.id);
      addToast(`Player ${player.displayName} deactivated.`, "success");
      loadAll();
    } catch (err) {
      addToast(normalizeApiError(err), "error");
    }
  };

  if (user?.role !== "ADMIN") {
    return (
      <ErrorState message="Access denied. Admin role required." />
    );
  }

  const tabs = [
    { key: "sports" as const, label: "Sports", count: sports.length },
    { key: "competitions" as const, label: "Competitions", count: competitions.length },
    { key: "players" as const, label: "Players", count: players.length },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Admin Dashboard</h1>
        <p className="mt-1 text-sm text-surface-400">
          Manage sports, competitions, and players.
        </p>
      </div>

      <div className="flex gap-2 border-b border-surface-700 pb-0">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
              tab === t.key
                ? "border-brand-400 text-brand-400"
                : "border-transparent text-surface-400 hover:text-surface-200"
            }`}
          >
            {t.label} ({t.count})
          </button>
        ))}
      </div>

      {loading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState message={error} onRetry={loadAll} />
      ) : (
        <div className="card">
          {tab === "sports" && (
            <div className="space-y-3">
              {sports.map((s) => (
                <div
                  key={s.id}
                  className="flex items-center justify-between rounded-lg bg-surface-700/30 px-4 py-3"
                >
                  <div>
                    <p className="font-medium text-surface-100">{s.name}</p>
                    <p className="text-xs text-surface-500">{s.code}</p>
                  </div>
                  <button
                    onClick={() => toggleSport(s)}
                    className={`btn text-xs ${
                      s.active ? "btn-secondary" : "btn-primary"
                    }`}
                  >
                    {s.active ? "Deactivate" : "Activate"}
                  </button>
                </div>
              ))}
            </div>
          )}

          {tab === "competitions" && (
            <div className="space-y-3">
              {competitions.length === 0 ? (
                <p className="text-sm text-surface-400">No competitions found.</p>
              ) : (
                competitions.map((c) => (
                  <div
                    key={c.id}
                    className="flex items-center justify-between rounded-lg bg-surface-700/30 px-4 py-3"
                  >
                    <div>
                      <p className="font-medium text-surface-100">{c.name}</p>
                      <p className="text-xs text-surface-500">
                        {c.sportCode} · {c.code}
                      </p>
                    </div>
                    <button
                      onClick={() => toggleCompetition(c)}
                      className={`btn text-xs ${
                        c.active ? "btn-secondary" : "btn-primary"
                      }`}
                    >
                      {c.active ? "Deactivate" : "Activate"}
                    </button>
                  </div>
                ))
              )}
            </div>
          )}

          {tab === "players" && (
            <div className="space-y-3">
              {players.map((p) => (
                <div
                  key={p.id}
                  className="flex items-center justify-between rounded-lg bg-surface-700/30 px-4 py-3"
                >
                  <div>
                    <p className="font-medium text-surface-100">
                      {p.displayName}
                    </p>
                    <p className="text-xs text-surface-500">{p.email}</p>
                  </div>
                  {p.active && (
                    <button
                      onClick={() => deactivatePlayer(p)}
                      className="btn-danger text-xs"
                    >
                      Deactivate
                    </button>
                  )}
                  {!p.active && (
                    <span className="text-xs text-surface-500">Inactive</span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
