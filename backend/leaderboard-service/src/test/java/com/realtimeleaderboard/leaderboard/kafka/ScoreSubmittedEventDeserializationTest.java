package com.realtimeleaderboard.leaderboard.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScoreSubmittedEventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void deserializeFromJson() throws Exception {
        String json = """
                {
                    "eventId": "evt-123",
                    "eventVersion": 1,
                    "scoreId": "score-456",
                    "userId": 42,
                    "sportId": 1,
                    "scoreValue": 95.5,
                    "scoreType": "GOALS",
                    "eventName": "EPL Match",
                    "recordedAt": "2024-01-15T10:30:00Z",
                    "occurredAt": "2024-01-15T10:30:05Z"
                }
                """;

        ScoreSubmittedEvent event = objectMapper.readValue(json, ScoreSubmittedEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-123");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.scoreId()).isEqualTo("score-456");
        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.sportId()).isEqualTo(1L);
        assertThat(event.scoreValue()).isEqualTo(95.5);
        assertThat(event.scoreType()).isEqualTo("GOALS");
        assertThat(event.eventName()).isEqualTo("EPL Match");
        assertThat(event.recordedAt()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2024-01-15T10:30:05Z"));
    }

    @Test
    void serializeAndDeserialize() throws Exception {
        ScoreSubmittedEvent original = new ScoreSubmittedEvent(
                "evt-789", 1, "score-789", 50L, 3L, 85.12, "LAP_TIME", "Monaco",
                Instant.parse("2024-06-01T12:00:00Z"), Instant.parse("2024-06-01T12:00:01Z"));

        String json = objectMapper.writeValueAsString(original);
        ScoreSubmittedEvent deserialized = objectMapper.readValue(json, ScoreSubmittedEvent.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void nullEventNameIsHandled() throws Exception {
        String json = """
                {"eventId":"e1","eventVersion":1,"scoreId":"s1","userId":1,"sportId":1,"scoreValue":10.0,"scoreType":"POINTS","eventName":null,"recordedAt":"2024-01-01T00:00:00Z","occurredAt":"2024-01-01T00:00:01Z"}
                """;

        ScoreSubmittedEvent event = objectMapper.readValue(json, ScoreSubmittedEvent.class);
        assertThat(event.eventName()).isNull();
    }
}
