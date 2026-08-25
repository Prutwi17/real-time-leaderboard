package com.realtimeleaderboard.score.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Component
public class LeaderboardClient {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardClient.class);

    private final RestTemplate restTemplate;
    private final String internalSecret;

    public LeaderboardClient(RestTemplate restTemplate,
                             @Value("${internal.service-secret:}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.internalSecret = internalSecret;
    }

    public void notifyScoreUpdate(Long userId, Long sportId, Double score, String scoreId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service-Secret", internalSecret);

            Map<String, Object> body = Map.of(
                    "userId", userId,
                    "sportId", sportId,
                    "score", score,
                    "scoreId", scoreId
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(
                    "http://leaderboard-service/internal/leaderboards/scores",
                    request, String.class);
            log.info("Notified leaderboard-service: userId={}, sportId={}, scoreId={}", userId, sportId, scoreId);
        } catch (Exception e) {
            log.warn("Failed to notify leaderboard-service for scoreId={}: {}. Rebuild will restore consistency.",
                    scoreId, e.getMessage());
        }
    }
}
