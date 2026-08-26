import { Link } from "react-router-dom";
import RankBadge from "./RankBadge";
import type { LeaderboardEntry } from "../types";

interface LeaderboardRowProps {
  entry: LeaderboardEntry;
  isUpdated?: boolean;
}

export default function LeaderboardRow({ entry, isUpdated }: LeaderboardRowProps) {
  return (
    <tr
      className={`border-b border-surface-700/30 transition-colors hover:bg-surface-700/20 ${
        isUpdated ? "animate-flash" : ""
      } ${entry.rank <= 3 ? "bg-surface-800/30" : ""}`}
    >
      <td className="px-4 py-3">
        <RankBadge rank={entry.rank} />
      </td>
      <td className="px-4 py-3">
        <Link
          to={`/players/${entry.userId}`}
          className="font-medium text-surface-100 hover:text-brand-400"
        >
          Player #{entry.userId}
        </Link>
      </td>
      <td className="px-4 py-3 text-right font-mono font-semibold text-surface-100">
        {entry.score.toLocaleString(undefined, { maximumFractionDigits: 2 })}
      </td>
    </tr>
  );
}
