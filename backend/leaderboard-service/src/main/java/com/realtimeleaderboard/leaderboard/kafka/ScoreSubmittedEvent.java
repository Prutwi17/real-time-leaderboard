package com.realtimeleaderboard.leaderboard.kafka;

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
}
