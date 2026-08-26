package com.realtimeleaderboard.score.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ScoreSubmittedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("eventVersion") int eventVersion,
        @JsonProperty("scoreId") String scoreId,
        @JsonProperty("userId") Long userId,
        @JsonProperty("sportId") Long sportId,
        @JsonProperty("scoreValue") double scoreValue,
        @JsonProperty("scoreType") String scoreType,
        @JsonProperty("eventName") String eventName,
        @JsonProperty("recordedAt") Instant recordedAt,
        @JsonProperty("occurredAt") Instant occurredAt
) {

    @JsonCreator
    public ScoreSubmittedEvent {
    }

    public static ScoreSubmittedEvent of(String eventId, String scoreId, Long userId,
                                         Long sportId, double scoreValue, String scoreType,
                                         String eventName, Instant recordedAt) {
        return new ScoreSubmittedEvent(
                eventId,
                1,
                scoreId,
                userId,
                sportId,
                scoreValue,
                scoreType,
                eventName,
                recordedAt,
                Instant.now()
        );
    }
}
