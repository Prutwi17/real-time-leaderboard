package com.realtimeleaderboard.leaderboard.kafka;

import com.realtimeleaderboard.leaderboard.dto.request.LeaderboardScoreUpdateRequest;
import com.realtimeleaderboard.leaderboard.dto.response.ScoreUpdateResult;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.service.LeaderboardUpdateService;
import com.realtimeleaderboard.leaderboard.websocket.LeaderboardUpdatePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class ScoreSubmittedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ScoreSubmittedEventListener.class);

    private final LeaderboardUpdateService updateService;
    private final LeaderboardKeyFactory keyFactory;
    private final LeaderboardUpdatePublisher updatePublisher;

    public ScoreSubmittedEventListener(LeaderboardUpdateService updateService,
                                       LeaderboardKeyFactory keyFactory,
                                       LeaderboardUpdatePublisher updatePublisher) {
        this.updateService = updateService;
        this.keyFactory = keyFactory;
        this.updatePublisher = updatePublisher;
    }

    @KafkaListener(
            topics = "${spring.kafka.topic.score-submitted:leaderboard.score.submitted}",
            groupId = "${spring.kafka.consumer.group-id:leaderboard-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onScoreSubmitted(
            @Payload ScoreSubmittedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received score event: key={}, partition={}, offset={}",
                key, partition, offset);

        if (!validateEvent(event)) {
            log.warn("Invalid event received, skipping: key={}", key);
            acknowledgment.acknowledge();
            return;
        }

        log.info("Processing score event: eventId={}, scoreId={}, userId={}, sportId={}",
                event.eventId(), event.scoreId(), event.userId(), event.sportId());

        try {
            LeaderboardScoreUpdateRequest request = new LeaderboardScoreUpdateRequest(
                    event.userId(),
                    event.sportId(),
                    event.scoreValue(),
                    event.eventId()
            );
            ScoreUpdateResult result = updateService.processScoreUpdate(request);
            if (result.updated()) {
                updatePublisher.broadcastLeaderboardUpdate(event.eventId(), result.sport());
            }
            acknowledgment.acknowledge();
            log.info("Successfully processed score event: eventId={}, scoreId={}", event.eventId(), event.scoreId());
        } catch (InvalidSportException e) {
            log.warn("Unsupported sport in event: eventId={}, sportId={}: {}", event.eventId(), event.sportId(), e.getMessage());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process score event: eventId={}, scoreId={}: {}", event.eventId(), event.scoreId(), e.getMessage());
            throw e;
        }
    }

    private boolean validateEvent(ScoreSubmittedEvent event) {
        if (event == null) {
            log.warn("Null event received");
            return false;
        }
        if (event.eventVersion() != 1) {
            log.warn("Unsupported event version: {}", event.eventVersion());
            return false;
        }
        if (event.eventId() == null || event.eventId().isBlank()) {
            log.warn("Missing eventId");
            return false;
        }
        if (event.userId() == null) {
            log.warn("Missing userId in event: eventId={}", event.eventId());
            return false;
        }
        if (event.sportId() == null) {
            log.warn("Missing sportId in event: eventId={}", event.eventId());
            return false;
        }
        return true;
    }
}
