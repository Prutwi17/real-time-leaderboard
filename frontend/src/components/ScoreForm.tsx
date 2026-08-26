import { useState } from "react";
import type { SportCode, ScoreType } from "../types";

interface ScoreFormProps {
  sportId: number;
  sportName: string;
  onSubmit: (data: {
    sportId: number;
    value: number;
    scoreType: ScoreType;
    eventName?: string;
  }) => Promise<void>;
  submitting: boolean;
}

const SCORE_TYPES: Record<SportCode, ScoreType[]> = {
  FOOTBALL: ["POINTS", "GOALS"],
  CRICKET: ["POINTS", "RUNS"],
  F1: ["POSITION", "LAP_TIME"],
};

export default function ScoreForm({
  sportId,
  sportName,
  onSubmit,
  submitting,
}: ScoreFormProps) {
  const [value, setValue] = useState("");
  const [scoreType, setScoreType] = useState<ScoreType>("POINTS");
  const [eventName, setEventName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const sportCode = sportName.toUpperCase().replace(" ", "_") as SportCode;
  const types = SCORE_TYPES[sportCode] || ["POINTS"];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);

    const numVal = parseFloat(value);
    if (isNaN(numVal) || numVal < 0) {
      setError("Please enter a valid positive score.");
      return;
    }
    if (numVal > 1000000) {
      setError("Score must be at most 1,000,000.");
      return;
    }

    try {
      await onSubmit({
        sportId,
        value: numVal,
        scoreType,
        eventName: eventName || undefined,
      });
      setSuccess(true);
      setValue("");
      setEventName("");
    } catch {
      setError("Submission failed. Please try again.");
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && (
        <div className="rounded-lg bg-red-900/30 p-3 text-sm text-red-300" role="alert">
          {error}
        </div>
      )}
      {success && (
        <div className="rounded-lg bg-green-900/30 p-3 text-sm text-green-300" role="status">
          Score submitted! Processing via Kafka → Redis → Leaderboard...
        </div>
      )}

      <div>
        <label htmlFor="scoreType" className="mb-1 block text-sm font-medium text-surface-300">
          Score Type
        </label>
        <select
          id="scoreType"
          value={scoreType}
          onChange={(e) => setScoreType(e.target.value as ScoreType)}
          className="input"
        >
          {types.map((t) => (
            <option key={t} value={t}>
              {t.replace("_", " ")}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label htmlFor="scoreValue" className="mb-1 block text-sm font-medium text-surface-300">
          Score
        </label>
        <input
          id="scoreValue"
          type="number"
          step="0.01"
          min="0"
          max="1000000"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="e.g. 100"
          className="input"
          required
        />
      </div>

      <div>
        <label htmlFor="eventName" className="mb-1 block text-sm font-medium text-surface-300">
          Event Name (optional)
        </label>
        <input
          id="eventName"
          type="text"
          value={eventName}
          onChange={(e) => setEventName(e.target.value)}
          placeholder="e.g. Match Day 1"
          maxLength={150}
          className="input"
        />
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="btn-primary w-full"
      >
        {submitting ? "Submitting..." : "Submit Score"}
      </button>
    </form>
  );
}
