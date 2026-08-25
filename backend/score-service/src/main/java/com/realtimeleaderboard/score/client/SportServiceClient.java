package com.realtimeleaderboard.score.client;

import com.realtimeleaderboard.score.exception.ResourceNotFoundException;
import com.realtimeleaderboard.score.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class SportServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SportServiceClient.class);
    private final RestTemplate restTemplate;

    public SportServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SportSnapshot fetchSport(Long sportId) {
        try {
            ResponseEntity<SportSnapshot> response = restTemplate.getForEntity(
                    "http://sport-service/api/sports/{id}", SportSnapshot.class, sportId);
            SportSnapshot sport = response.getBody();
            if (sport == null) {
                mapError(404, sportId);
            }
            return sport;
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            mapError(ex.getStatusCode().value(), sportId);
        } catch (ResourceAccessException ex) {
            log.warn("Sport Service unavailable while validating sport {}: {}", sportId, ex.getMessage());
            mapUnavailable();
        }
        return null; // unreachable
    }

    static void mapError(int status, Long sportId) {
        if (status == 404) {
            throw new ResourceNotFoundException("Sport not found: id " + sportId);
        }
        throw new ServiceUnavailableException(
                "Sport Service returned HTTP " + status + "; score cannot be validated");
    }

    static void mapUnavailable() {
        throw new ServiceUnavailableException(
                "Sport Service is unavailable; score cannot be validated right now");
    }
}
