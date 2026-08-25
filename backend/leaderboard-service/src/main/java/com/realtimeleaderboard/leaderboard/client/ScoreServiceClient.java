package com.realtimeleaderboard.leaderboard.client;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ScoreServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ScoreServiceClient.class);

    private final RestTemplate restTemplate;
    private final String internalSecret;

    public ScoreServiceClient(RestTemplate restTemplate,
                              @Value("${internal.service-secret:}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.internalSecret = internalSecret;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> fetchScoresForRebuild(String sportId) {
        try {
            String url = "http://score-service/api/scores?sportId=" + sportId + "&size=10000&page=0";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Secret", internalSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<>() {});

            if (response.getBody() != null && response.getBody().containsKey("content")) {
                Object content = response.getBody().get("content");
                if (content instanceof java.util.List<?> list) {
                    return (java.util.List<Map<String, Object>>) (java.util.List<?>) list;
                }
            }
            return java.util.List.of();
        } catch (Exception e) {
            log.error("Failed to fetch scores from score-service for sportId={}: {}", sportId, e.getMessage());
            return java.util.List.of();
        }
    }
}
