package com.realtimeleaderboard.leaderboard.dto.response;

public record LeaderboardEntryResponse(
        int rank,
        Long userId,
        Double score
) {}
