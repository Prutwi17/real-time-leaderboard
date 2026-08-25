package com.realtimeleaderboard.leaderboard.dto.response;

public record ScoreAggregation(
        Long userId,
        Long sportId,
        double totalScore
) {}
