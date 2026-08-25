package com.realtimeleaderboard.score.dto.request;

import com.realtimeleaderboard.score.entity.ScoreType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateScoreRequest(
        @NotNull(message = "sportId is required")
        Long sportId,
        @NotNull(message = "value is required")
        @PositiveOrZero(message = "value must not be negative")
        @DecimalMax(value = "1000000", message = "value must not exceed 1000000")
        @Digits(integer = 10, fraction = 2, message = "value must have at most 10 integer digits and 2 decimal places")
        BigDecimal value,
        @Size(max = 150, message = "eventName must not exceed 150 characters")
        String eventName,
        @Size(max = 100, message = "eventId must not exceed 100 characters")
        String eventId,
        @NotNull(message = "scoreType is required")
        ScoreType scoreType,
        @Size(max = 64, message = "submissionId must not exceed 64 characters")
        String submissionId) {
}
