import { Link } from "react-router-dom";
import RankBadge from "./RankBadge";
import type { LeaderboardEntry } from "../types";

interface LeaderboardTableProps {
  entries: LeaderboardEntry[];
  updatedIds?: Set<number>;
  showPlayerLink?: boolean;
  compact?: boolean;
}

export default function LeaderboardTable({
  entries,
  updatedIds = new Set(),
  showPlayerLink = true,
  compact = false,
}: LeaderboardTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          <tr className="border-b border-surface-700 text-xs font-semibold uppercase tracking-wider text-surface-400">
            <th className={`${compact ? "px-3 py-2" : "px-4 py-3"}`}>Rank</th>
            <th className={`${compact ? "px-3 py-2" : "px-4 py-3"}`}>Player</th>
            <th className={`${compact ? "px-3 py-2" : "px-4 py-3"} text-right`}>Score</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr
              key={entry.userId}
              className={`border-b border-surface-700/30 transition-colors hover:bg-surface-700/20 ${
                updatedIds.has(entry.userId) ? "animate-flash" : ""
              } ${entry.rank <= 3 ? "bg-surface-800/30" : ""}`}
            >
              <td className={`${compact ? "px-3 py-2" : "px-4 py-3"}`}>
                <RankBadge rank={entry.rank} />
              </td>
              <td className={`${compact ? "px-3 py-2" : "px-4 py-3"}`}>
                {showPlayerLink ? (
                  <Link
                    to={`/players/${entry.userId}`}
                    className="font-medium text-surface-100 hover:text-brand-400"
                  >
                    Player #{entry.userId}
                  </Link>
                ) : (
                  <span className="font-medium text-surface-100">
                    Player #{entry.userId}
                  </span>
                )}
              </td>
              <td
                className={`${
                  compact ? "px-3 py-2" : "px-4 py-3"
                } text-right font-mono font-semibold text-surface-100`}
              >
                {entry.score.toLocaleString(undefined, {
                  maximumFractionDigits: 2,
                })}
              </td>
            </tr>
          ))}
          {entries.length === 0 && (
            <tr>
              <td colSpan={3} className="px-4 py-8 text-center text-surface-500">
                No data available
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
