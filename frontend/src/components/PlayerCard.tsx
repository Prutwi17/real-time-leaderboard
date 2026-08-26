import { Link } from "react-router-dom";
import type { PlayerResponse } from "../types";

interface PlayerCardProps {
  player: PlayerResponse;
}

export default function PlayerCard({ player }: PlayerCardProps) {
  return (
    <div className="card flex items-center gap-4">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-600/20 text-lg font-bold text-brand-400">
        {player.displayName.charAt(0).toUpperCase()}
      </div>
      <div className="flex-1 min-w-0">
        <Link
          to={`/players/${player.id}`}
          className="font-semibold text-surface-100 hover:text-brand-400"
        >
          {player.displayName}
        </Link>
        {player.bio && (
          <p className="truncate text-sm text-surface-400">{player.bio}</p>
        )}
      </div>
      <div className={`rounded-full px-2 py-0.5 text-xs font-medium ${player.active ? "bg-green-900/40 text-green-400" : "bg-surface-700 text-surface-400"}`}>
        {player.active ? "Active" : "Inactive"}
      </div>
    </div>
  );
}
