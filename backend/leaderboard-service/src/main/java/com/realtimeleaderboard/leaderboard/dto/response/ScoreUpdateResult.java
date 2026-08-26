package com.realtimeleaderboard.leaderboard.dto.response;

public record ScoreUpdateResult(
        String message,
        boolean updated,
        String sport
) {}
