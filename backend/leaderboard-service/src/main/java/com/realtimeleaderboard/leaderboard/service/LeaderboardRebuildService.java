package com.realtimeleaderboard.leaderboard.service;

import com.realtimeleaderboard.leaderboard.client.ScoreServiceClient;
import com.realtimeleaderboard.leaderboard.dto.response.MessageResponse;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.exception.ServiceUnavailableException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.redis.RedisLeaderboardRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardRebuildService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRebuildService.class);

    private final RedisLeaderboardRepository redisRepository;
    private final LeaderboardKeyFactory keyFactory;
    private final ScoreServiceClient scoreServiceClient;

    public LeaderboardRebuildService(RedisLeaderboardRepository redisRepository,
                                     LeaderboardKeyFactory keyFactory,
                                     ScoreServiceClient scoreServiceClient) {
        this.redisRepository = redisRepository;
        this.keyFactory = keyFactory;
        this.scoreServiceClient = scoreServiceClient;
    }

    public MessageResponse rebuild(String sport) {
        if (!keyFactory.isSupportedSport(sport)) {
            throw new InvalidSportException("Unsupported sport for rebuild: " + sport);
        }

        String key = keyFactory.leaderboardKey(sport);
        Long sportId = resolveSportId(sport);

        log.info("Starting rebuild for sport={} (sportId={})", sport, sportId);

        try {
            var scores = scoreServiceClient.fetchScoresForRebuild(String.valueOf(sportId));
            Map<String, Double> aggregated = new HashMap<>();
            for (var scoreEntry : scores) {
                Object userIdObj = scoreEntry.get("userId");
                Object scoreValueObj = scoreEntry.get("value");
                if (userIdObj == null || scoreValueObj == null) continue;

                Long userId = parseUserId(userIdObj);
                Double scoreValue = parseScoreValue(scoreValueObj);
                if (userId == null || scoreValue == null) continue;

                aggregated.merge(String.valueOf(userId), scoreValue, Double::sum);
            }

            redisRepository.delete(key);

            if (!aggregated.isEmpty()) {
                Map<String, Double> membersWithScores = new HashMap<>(aggregated);
                for (var entry : membersWithScores.entrySet()) {
                    redisRepository.addScore(key, entry.getKey(), entry.getValue());
                }
            }

            log.info("Rebuild complete for sport={}: {} players populated", sport, aggregated.size());
            return new MessageResponse("Leaderboard rebuilt for " + sport + ": " + aggregated.size() + " players");
        } catch (InvalidSportException e) {
            throw e;
        } catch (Exception e) {
            log.error("Rebuild failed for sport={}: {}", sport, e.getMessage());
            throw new ServiceUnavailableException("Rebuild failed: " + e.getMessage());
        }
    }

    private Long resolveSportId(String sport) {
        return switch (sport.toUpperCase()) {
            case "FOOTBALL" -> 1L;
            case "CRICKET" -> 2L;
            case "F1" -> 3L;
            default -> throw new InvalidSportException("Unknown sport: " + sport);
        };
    }

    private Long parseUserId(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        if (obj instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Double parseScoreValue(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
