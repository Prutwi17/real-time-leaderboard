import { useParams, Link } from "react-router-dom";
import { usePlayer } from "../hooks/usePlayer";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";

export default function PlayerDetail() {
  const { id } = useParams<{ id: string }>();
  const playerId = id ? parseInt(id, 10) : null;
  const { player, loading, error } = usePlayer(playerId);

  if (loading) return <LoadingState />;
  if (error || !player)
    return (
      <ErrorState message={error || "Player not found."} />
    );

  return (
    <div className="space-y-6">
      <Link
        to="/players"
        className="text-sm text-brand-400 hover:text-brand-300"
      >
        ← Back to Players
      </Link>

      <div className="card">
        <div className="flex items-center gap-6">
          <div className="flex h-20 w-20 items-center justify-center rounded-full bg-brand-600/20 text-3xl font-bold text-brand-400">
            {player.displayName.charAt(0).toUpperCase()}
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white">
              {player.displayName}
            </h1>
            <p className="text-sm text-surface-400">{player.email}</p>
            <div className="mt-2">
              <span
                className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                  player.active
                    ? "bg-green-900/40 text-green-400"
                    : "bg-surface-700 text-surface-400"
                }`}
              >
                {player.active ? "Active" : "Inactive"}
              </span>
            </div>
          </div>
        </div>

        {player.bio && (
          <div className="mt-6 border-t border-surface-700 pt-4">
            <h2 className="text-sm font-semibold text-surface-400">Bio</h2>
            <p className="mt-1 text-surface-200">{player.bio}</p>
          </div>
        )}

        <div className="mt-6 border-t border-surface-700 pt-4">
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="text-surface-400">Player ID</dt>
              <dd className="font-mono text-surface-100">{player.id}</dd>
            </div>
            <div>
              <dt className="text-surface-400">Created</dt>
              <dd className="text-surface-100">
                {new Date(player.createdAt).toLocaleDateString()}
              </dd>
            </div>
          </dl>
        </div>
      </div>
    </div>
  );
}
