package com.realtimeleaderboard.leaderboard.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeaderboardScoreUpdateRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "sportId is required")
        Long sportId,

        @NotNull(message = "score is required")
        @Min(value = 0, message = "score must not be negative")
        Double score,

        @NotBlank(message = "scoreId is required")
        String scoreId
) {}
