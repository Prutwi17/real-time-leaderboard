package com.realtimeleaderboard.score.service;

import com.realtimeleaderboard.score.client.SportServiceClient;
import com.realtimeleaderboard.score.client.SportSnapshot;
import com.realtimeleaderboard.score.exception.ConflictException;
import com.realtimeleaderboard.score.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SportValidationService {

    private final SportServiceClient sportServiceClient;

    public SportValidationService(SportServiceClient sportServiceClient) {
        this.sportServiceClient = sportServiceClient;
    }

    /**
     * Verifies that the given sportId references an existing, active sport in
     * Sport Service. Throws 404 for missing sports and 409 for inactive ones.
     * Throws 503 when Sport Service itself is unreachable.
     */
    public SportSnapshot validateSportForSubmission(Long sportId) {
        SportSnapshot sport = sportServiceClient.fetchSport(sportId);
        if (!sport.active()) {
            throw new ConflictException("Sport '" + sport.code() + "' is not active");
        }
        return sport;
    }
}
