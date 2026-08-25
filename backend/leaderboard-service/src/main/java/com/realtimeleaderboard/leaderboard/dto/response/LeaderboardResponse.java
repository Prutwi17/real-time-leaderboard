package com.realtimeleaderboard.leaderboard.dto.response;

import java.util.List;

public record LeaderboardResponse(
        String sport,
        List<LeaderboardEntryResponse> entries,
        int page,
        int size,
        long totalPlayers
) {}
