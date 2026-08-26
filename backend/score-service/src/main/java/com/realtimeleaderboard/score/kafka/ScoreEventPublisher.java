package com.realtimeleaderboard.score.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeleaderboard.score.entity.Score;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ScoreEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScoreEventPublisher.class);

    private final KafkaTemplate<String, ScoreSubmittedEvent> kafkaTemplate;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ScoreEventPublisher(KafkaTemplate<String, ScoreSubmittedEvent> kafkaTemplate,
                               OutboxEventRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScoreSaved(Score score) {
        String eventId = UUID.randomUUID().toString();
        String scoreId = String.valueOf(score.getId());

        ScoreSubmittedEvent event = ScoreSubmittedEvent.of(
                eventId,
                scoreId,
                score.getUserId(),
                score.getSportId(),
                score.getValue().doubleValue(),
                score.getScoreType() != null ? score.getScoreType().name() : "POINTS",
                score.getEventName(),
                score.getRecordedAt()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                    eventId,
                    "ScoreSubmitted",
                    "Score",
                    scoreId,
                    payload
            );
            outboxRepository.save(outboxEvent);
            log.info("Outbox event created: eventId={}, scoreId={}", eventId, scoreId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ScoreSubmittedEvent for scoreId={}: {}", scoreId, e.getMessage());
        }
    }

    public void publishPendingEvents() {
        var pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.Status.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 50));

        for (OutboxEvent outboxEvent : pending) {
            try {
                ScoreSubmittedEvent event = objectMapper.readValue(
                        outboxEvent.getPayload(), ScoreSubmittedEvent.class);

                CompletableFuture<SendResult<String, ScoreSubmittedEvent>> future =
                        kafkaTemplate.send(KafkaTopics.SCORE_SUBMITTED, event.userId().toString(), event);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish outbox event {}: {}", outboxEvent.getEventId(), ex.getMessage());
                        outboxEvent.incrementAttempts();
                        outboxEvent.setLastError(ex.getMessage());
                        if (outboxEvent.getAttempts() >= 5) {
                            outboxEvent.setStatus(OutboxEvent.Status.FAILED);
                        }
                        outboxRepository.save(outboxEvent);
                    } else {
                        outboxEvent.setStatus(OutboxEvent.Status.PUBLISHED);
                        outboxEvent.setPublishedAt(java.time.Instant.now());
                        outboxRepository.save(outboxEvent);
                        log.info("Published outbox event: eventId={}, partition={}, offset={}",
                                outboxEvent.getEventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
            } catch (Exception e) {
                log.error("Error publishing outbox event {}: {}", outboxEvent.getEventId(), e.getMessage());
                outboxEvent.incrementAttempts();
                outboxEvent.setLastError(e.getMessage());
                if (outboxEvent.getAttempts() >= 5) {
                    outboxEvent.setStatus(OutboxEvent.Status.FAILED);
                }
                outboxRepository.save(outboxEvent);
            }
        }
    }
}
