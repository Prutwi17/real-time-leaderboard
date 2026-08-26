package com.realtimeleaderboard.score.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScoreSubmittedEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void ofCreatesEventWithCorrectVersion() {
        Instant now = Instant.now();
        ScoreSubmittedEvent event = ScoreSubmittedEvent.of(
                "evt-1", "score-1", 10L, 1L, 95.0, "GOALS", "EPL", now);

        assertThat(event.eventId()).isEqualTo("evt-1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.scoreId()).isEqualTo("score-1");
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.sportId()).isEqualTo(1L);
        assertThat(event.scoreValue()).isEqualTo(95.0);
        assertThat(event.scoreType()).isEqualTo("GOALS");
        assertThat(event.eventName()).isEqualTo("EPL");
        assertThat(event.recordedAt()).isEqualTo(now);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void serializationRoundTrip() throws Exception {
        Instant now = Instant.now();
        ScoreSubmittedEvent event = ScoreSubmittedEvent.of(
                "evt-2", "score-2", 20L, 2L, 82.5, "RUNS", "IPL", now);

        String json = objectMapper.writeValueAsString(event);
        ScoreSubmittedEvent deserialized = objectMapper.readValue(json, ScoreSubmittedEvent.class);

        assertThat(deserialized.eventId()).isEqualTo("evt-2");
        assertThat(deserialized.eventVersion()).isEqualTo(1);
        assertThat(deserialized.scoreId()).isEqualTo("score-2");
        assertThat(deserialized.userId()).isEqualTo(20L);
        assertThat(deserialized.sportId()).isEqualTo(2L);
        assertThat(deserialized.scoreValue()).isEqualTo(82.5);
        assertThat(deserialized.scoreType()).isEqualTo("RUNS");
    }

    @Test
    void eventVersionIsOne() {
        ScoreSubmittedEvent event = ScoreSubmittedEvent.of(
                "e1", "s1", 1L, 1L, 10.0, "POINTS", null, Instant.now());
        assertThat(event.eventVersion()).isEqualTo(1);
    }

    @Test
    void nullEventNameIsAllowed() {
        ScoreSubmittedEvent event = ScoreSubmittedEvent.of(
                "e1", "s1", 1L, 1L, 10.0, "POINTS", null, Instant.now());
        assertThat(event.eventName()).isNull();
    }
}
