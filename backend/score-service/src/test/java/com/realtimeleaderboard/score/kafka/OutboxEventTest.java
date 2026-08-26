package com.realtimeleaderboard.score.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void constructorSetsPendingStatus() {
        OutboxEvent event = new OutboxEvent("evt-1", "ScoreSubmitted", "Score", "100", "{}");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(0);
        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getEventType()).isEqualTo("ScoreSubmitted");
        assertThat(event.getAggregateType()).isEqualTo("Score");
        assertThat(event.getAggregateId()).isEqualTo("100");
        assertThat(event.getPayload()).isEqualTo("{}");
    }

    @Test
    void incrementAttemptsIncreasesCount() {
        OutboxEvent event = new OutboxEvent("evt-1", "ScoreSubmitted", "Score", "100", "{}");
        assertThat(event.getAttempts()).isEqualTo(0);
        event.incrementAttempts();
        assertThat(event.getAttempts()).isEqualTo(1);
        event.incrementAttempts();
        assertThat(event.getAttempts()).isEqualTo(2);
    }

    @Test
    void statusTransitions() {
        OutboxEvent event = new OutboxEvent("evt-1", "ScoreSubmitted", "Score", "100", "{}");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);

        event.setStatus(OutboxEvent.Status.PUBLISHED);
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        assertThat(event.getPublishedAt()).isNull();

        event.setPublishedAt(Instant.now());
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void failedStatusWithLastError() {
        OutboxEvent event = new OutboxEvent("evt-1", "ScoreSubmitted", "Score", "100", "{}");
        event.setStatus(OutboxEvent.Status.FAILED);
        event.setLastError("Connection refused");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(event.getLastError()).isEqualTo("Connection refused");
    }
}
