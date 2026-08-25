package com.realtimeleaderboard.leaderboard.dto.response;

public record PlayerRankResponse(
        String sport,
        Long userId,
        int rank,
        Double score
) {}
