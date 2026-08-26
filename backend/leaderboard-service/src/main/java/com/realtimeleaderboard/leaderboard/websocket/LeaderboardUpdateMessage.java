package com.realtimeleaderboard.leaderboard.websocket;

import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardEntryResponse;
import java.time.Instant;
import java.util.List;

public record LeaderboardUpdateMessage(
        String eventId,
        String eventType,
        String sport,
        Instant timestamp,
        LeaderboardSnapshot leaderboard
) {
    public record LeaderboardSnapshot(
            String sport,
            List<LeaderboardEntryResponse> entries,
            long totalPlayers
    ) {}
}
