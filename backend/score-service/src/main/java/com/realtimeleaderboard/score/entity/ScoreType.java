package com.realtimeleaderboard.score.entity;

/**
 * Describes what the numeric value represents. The leaderboard ultimately
 * ranks by a single number regardless of type; scoreType is metadata that
 * tells clients how to display and interpret it.
 *
 * POINTS   - generic points (default for sports without a native unit)
 * GOALS    - goals scored in a football match/event
 * RUNS     - runs scored in a cricket match/event
 * LAP_TIME - F1 lap/race time in seconds (lower is better; ranking direction
 *            will be decided by the leaderboard phase)
 * POSITION - finishing position, 1 = winner (lower is better)
 */
public enum ScoreType {
    POINTS,
    GOALS,
    RUNS,
    LAP_TIME,
    POSITION
}
