package com.realtimeleaderboard.score.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final ScoreEventPublisher publisher;

    public OutboxScheduler(ScoreEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public void publishPendingEvents() {
        try {
            publisher.publishPendingEvents();
        } catch (Exception e) {
            log.error("Outbox scheduler error: {}", e.getMessage());
        }
    }
}
