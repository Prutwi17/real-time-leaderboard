package com.realtimeleaderboard.score.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class ScoreEventPublisherTest {

    @Mock private KafkaTemplate<String, ScoreSubmittedEvent> kafkaTemplate;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ScoreEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ScoreEventPublisher(kafkaTemplate, outboxRepository, objectMapper);
    }

    @Test
    void onScoreSavedCreatesOutboxEvent() throws Exception {
        com.realtimeleaderboard.score.entity.Score score = new com.realtimeleaderboard.score.entity.Score();
        score.setUserId(10L);
        score.setSportId(1L);
        score.setValue(BigDecimal.valueOf(100));
        score.setScoreType(com.realtimeleaderboard.score.entity.ScoreType.GOALS);
        score.setEventName("EPL");
        score.setRecordedAt(Instant.now());

        publisher.onScoreSaved(score);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ScoreSubmitted");
        assertThat(saved.getAggregateType()).isEqualTo("Score");
        assertThat(saved.getAggregateId()).isEqualTo(String.valueOf(score.getId()));
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);

        ScoreSubmittedEvent event = objectMapper.readValue(saved.getPayload(), ScoreSubmittedEvent.class);
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.sportId()).isEqualTo(1L);
        assertThat(event.scoreValue()).isEqualTo(100.0);
        assertThat(event.scoreType()).isEqualTo("GOALS");
        assertThat(event.eventName()).isEqualTo("EPL");
        assertThat(event.eventVersion()).isEqualTo(1);
    }

    @Test
    void eventIdIsUnique() {
        com.realtimeleaderboard.score.entity.Score score1 = createScore(10L, 1L);
        com.realtimeleaderboard.score.entity.Score score2 = createScore(20L, 2L);

        publisher.onScoreSaved(score1);
        publisher.onScoreSaved(score2);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getEventId())
                .isNotEqualTo(captor.getAllValues().get(1).getEventId());
    }

    private com.realtimeleaderboard.score.entity.Score createScore(Long userId, Long sportId) {
        com.realtimeleaderboard.score.entity.Score score = new com.realtimeleaderboard.score.entity.Score();
        score.setUserId(userId);
        score.setSportId(sportId);
        score.setValue(BigDecimal.TEN);
        score.setScoreType(com.realtimeleaderboard.score.entity.ScoreType.POINTS);
        score.setRecordedAt(Instant.now());
        return score;
    }
}
