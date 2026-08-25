package com.realtimeleaderboard.score.dto.response;

import com.realtimeleaderboard.score.entity.Score;
import com.realtimeleaderboard.score.entity.ScoreType;
import java.math.BigDecimal;
import java.time.Instant;

public record ScoreResponse(
        Long id,
        Long userId,
        Long sportId,
        BigDecimal value,
        String eventName,
        String eventId,
        ScoreType scoreType,
        String submissionId,
        Instant recordedAt,
        Instant createdAt) {

    public static ScoreResponse from(Score score) {
        return new ScoreResponse(score.getId(), score.getUserId(), score.getSportId(),
                score.getValue(), score.getEventName(), score.getEventId(),
                score.getScoreType(), score.getSubmissionId(),
                score.getRecordedAt(), score.getCreatedAt());
    }
}
