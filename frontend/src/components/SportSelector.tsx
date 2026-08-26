import { Link, useLocation } from "react-router-dom";
import type { SportCode } from "../types";

const SPORTS: { code: SportCode; label: string; color: string }[] = [
  { code: "FOOTBALL", label: "Football", color: "bg-sport-football" },
  { code: "CRICKET", label: "Cricket", color: "bg-sport-cricket" },
  { code: "F1", label: "F1", color: "bg-sport-f1" },
];

interface SportSelectorProps {
  current?: SportCode;
  onSelect?: (sport: SportCode) => void;
}

export default function SportSelector({ current, onSelect }: SportSelectorProps) {
  const location = useLocation();

  return (
    <div className="flex gap-2" role="tablist" aria-label="Select sport">
      {SPORTS.map((sport) => {
        const isActive = current === sport.code;
        return onSelect ? (
          <button
            key={sport.code}
            role="tab"
            aria-selected={isActive}
            onClick={() => onSelect(sport.code)}
            className={`flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition-all ${
              isActive
                ? "bg-surface-700 text-white shadow-lg"
                : "bg-surface-800/50 text-surface-400 hover:bg-surface-700/50 hover:text-surface-200"
            }`}
          >
            <span className={`h-2.5 w-2.5 rounded-full ${sport.color}`} />
            {sport.label}
          </button>
        ) : (
          <Link
            key={sport.code}
            to={`/leaderboards/${sport.code.toLowerCase()}`}
            className={`flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition-all ${
              location.pathname.includes(sport.code.toLowerCase())
                ? "bg-surface-700 text-white shadow-lg"
                : "bg-surface-800/50 text-surface-400 hover:bg-surface-700/50 hover:text-surface-200"
            }`}
          >
            <span className={`h-2.5 w-2.5 rounded-full ${sport.color}`} />
            {sport.label}
          </Link>
        );
      })}
    </div>
  );
}
