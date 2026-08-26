package com.realtimeleaderboard.score;

import com.realtimeleaderboard.score.kafka.OutboxEventRepository;
import com.realtimeleaderboard.score.kafka.ScoreEventPublisher;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

@TestConfiguration
public class KafkaTestConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, ?> mockKafkaTemplate() {
        return Mockito.mock(KafkaTemplate.class);
    }

    @Bean
    @Primary
    public OutboxEventRepository mockOutboxEventRepository() {
        return Mockito.mock(OutboxEventRepository.class);
    }

    @Bean
    @Primary
    public ScoreEventPublisher mockScoreEventPublisher() {
        return Mockito.mock(ScoreEventPublisher.class);
    }
}
