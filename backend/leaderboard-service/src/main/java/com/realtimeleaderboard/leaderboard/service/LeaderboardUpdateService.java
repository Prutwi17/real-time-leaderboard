package com.realtimeleaderboard.leaderboard.service;

import com.realtimeleaderboard.leaderboard.dto.request.LeaderboardScoreUpdateRequest;
import com.realtimeleaderboard.leaderboard.dto.response.MessageResponse;
import com.realtimeleaderboard.leaderboard.exception.ForbiddenException;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.redis.RedisLeaderboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardUpdateService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardUpdateService.class);

    private final RedisLeaderboardRepository redisRepository;
    private final LeaderboardKeyFactory keyFactory;
    private final String internalSecret;
    private final long processedScoreTtlHours;

    public LeaderboardUpdateService(RedisLeaderboardRepository redisRepository,
                                    LeaderboardKeyFactory keyFactory,
                                    @Value("${internal.service-secret:}") String internalSecret,
                                    @Value("${leaderboard.processed-score-ttl-hours:72}") long processedScoreTtlHours) {
        this.redisRepository = redisRepository;
        this.keyFactory = keyFactory;
        this.internalSecret = internalSecret;
        this.processedScoreTtlHours = processedScoreTtlHours;
    }

    public void validateInternalSecret(String providedSecret) {
        if (internalSecret == null || internalSecret.isEmpty()) {
            log.warn("INTERNAL_SERVICE_SECRET not configured; rejecting internal request");
            throw new ForbiddenException("Internal service secret not configured");
        }
        if (!internalSecret.equals(providedSecret)) {
            throw new ForbiddenException("Invalid internal service secret");
        }
    }

    public MessageResponse processScoreUpdate(LeaderboardScoreUpdateRequest request) {
        String sport = resolveSportFromId(request.sportId());
        if (!keyFactory.isSupportedSport(sport)) {
            throw new InvalidSportException("Unsupported sportId: " + request.sportId());
        }

        String scoreId = request.scoreId();
        String processedKey = keyFactory.processedScoreKey(scoreId);

        if (redisRepository.isProcessedScore(processedKey)) {
            log.info("Score {} already processed; skipping", scoreId);
            return new MessageResponse("Score already processed");
        }

        try {
            String key = keyFactory.leaderboardKey(sport);
            redisRepository.incrementScore(key, String.valueOf(request.userId()), request.score());
            redisRepository.setProcessedScore(processedKey, processedScoreTtlHours);
            log.info("Updated leaderboard: sport={}, userId={}, score={}, scoreId={}",
                    sport, request.userId(), request.score(), scoreId);
            return new MessageResponse("Leaderboard updated");
        } catch (Exception e) {
            log.error("Failed to update leaderboard for scoreId={}: {}", scoreId, e.getMessage());
            throw new com.realtimeleaderboard.leaderboard.exception.ServiceUnavailableException(
                    "Leaderboard update failed; score persisted in MySQL; rebuild will restore consistency");
        }
    }

    private String resolveSportFromId(Long sportId) {
        if (sportId == null) return "";
        return switch (sportId.intValue()) {
            case 1 -> "FOOTBALL";
            case 2 -> "CRICKET";
            case 3 -> "F1";
            default -> "";
        };
    }
}
