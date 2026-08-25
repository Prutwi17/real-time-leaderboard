package com.realtimeleaderboard.leaderboard.service;

import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardEntryResponse;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.dto.response.PlayerRankResponse;
import com.realtimeleaderboard.leaderboard.dto.response.SizeResponse;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.exception.ResourceNotFoundException;
import com.realtimeleaderboard.leaderboard.exception.ServiceUnavailableException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.redis.RedisLeaderboardRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private final RedisLeaderboardRepository redisRepository;
    private final LeaderboardKeyFactory keyFactory;
    private final int defaultTopLimit;
    private final int maxTopLimit;
    private final int defaultPageSize;
    private final int maxPageSize;
    private final int defaultNearbyRange;
    private final int maxNearbyRange;

    public LeaderboardService(RedisLeaderboardRepository redisRepository,
                              LeaderboardKeyFactory keyFactory,
                              @Value("${leaderboard.default-top-limit:10}") int defaultTopLimit,
                              @Value("${leaderboard.max-top-limit:100}") int maxTopLimit,
                              @Value("${leaderboard.default-page-size:20}") int defaultPageSize,
                              @Value("${leaderboard.max-page-size:100}") int maxPageSize,
                              @Value("${leaderboard.default-nearby-range:2}") int defaultNearbyRange,
                              @Value("${leaderboard.max-nearby-range:10}") int maxNearbyRange) {
        this.redisRepository = redisRepository;
        this.keyFactory = keyFactory;
        this.defaultTopLimit = defaultTopLimit;
        this.maxTopLimit = maxTopLimit;
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
        this.defaultNearbyRange = defaultNearbyRange;
        this.maxNearbyRange = maxNearbyRange;
    }

    private void validateSport(String sport) {
        if (!keyFactory.isSupportedSport(sport)) {
            throw new InvalidSportException("Unsupported sport: " + sport
                    + ". Supported: FOOTBALL, CRICKET, F1");
        }
    }

    private String getKeyOrThrow(String sport) {
        validateSport(sport);
        try {
            return keyFactory.leaderboardKey(sport);
        } catch (IllegalArgumentException e) {
            throw new InvalidSportException(e.getMessage());
        }
    }

    private int toApiRank(long redisRank) {
        return (int) redisRank + 1;
    }

    private LeaderboardEntryResponse toEntry(Map.Entry<String, Double> entry, int rank) {
        Long userId = Long.parseLong(entry.getKey());
        return new LeaderboardEntryResponse(rank, userId, entry.getValue());
    }

    public LeaderboardResponse getTop(String sport, int limit) {
        String key = getKeyOrThrow(sport);
        int effectiveLimit = Math.min(Math.max(limit, 1), maxTopLimit);
        try {
            long totalPlayers = getTotalPlayersFromKey(key);
            if (totalPlayers == 0) {
                return new LeaderboardResponse(sport.toUpperCase(), List.of(), 0, effectiveLimit, 0);
            }
            List<Map.Entry<String, Double>> entries = redisRepository.topNEntries(key, effectiveLimit);
            List<LeaderboardEntryResponse> responseEntries = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                responseEntries.add(toEntry(entries.get(i), i + 1));
            }
            return new LeaderboardResponse(sport.toUpperCase(), responseEntries, 0, effectiveLimit, totalPlayers);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error getting top-N for sport={}: {}", sport, e.getMessage());
            throw new ServiceUnavailableException("Leaderboard service temporarily unavailable");
        }
    }

    public LeaderboardResponse getLeaderboard(String sport, int page, int size) {
        String key = getKeyOrThrow(sport);
        int effectiveSize = Math.min(Math.max(size, 1), maxPageSize);
        int effectivePage = Math.max(page, 0);
        try {
            long totalPlayers = getTotalPlayersFromKey(key);
            if (totalPlayers == 0) {
                return new LeaderboardResponse(sport.toUpperCase(), List.of(), effectivePage, effectiveSize, 0);
            }
            long offset = (long) effectivePage * effectiveSize;
            if (offset >= totalPlayers) {
                return new LeaderboardResponse(sport.toUpperCase(), List.of(), effectivePage, effectiveSize, totalPlayers);
            }
            List<Map.Entry<String, Double>> entries = redisRepository.pageEntries(key, offset, effectiveSize);
            List<LeaderboardEntryResponse> responseEntries = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                responseEntries.add(toEntry(entries.get(i), toApiRank(offset + i)));
            }
            return new LeaderboardResponse(sport.toUpperCase(), responseEntries, effectivePage, effectiveSize, totalPlayers);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error getting leaderboard for sport={}: {}", sport, e.getMessage());
            throw new ServiceUnavailableException("Leaderboard service temporarily unavailable");
        }
    }

    public PlayerRankResponse getPlayerRank(String sport, Long userId) {
        String key = getKeyOrThrow(sport);
        try {
            Double score = redisRepository.getScore(key, String.valueOf(userId));
            if (score == null) {
                throw new ResourceNotFoundException("Player " + userId + " not found on " + sport + " leaderboard");
            }
            Long rank = redisRepository.getRank(key, String.valueOf(userId));
            if (rank == null) {
                throw new ResourceNotFoundException("Player " + userId + " not found on " + sport + " leaderboard");
            }
            return new PlayerRankResponse(sport.toUpperCase(), userId, toApiRank(rank), score);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error getting rank for userId={} sport={}: {}", userId, sport, e.getMessage());
            throw new ServiceUnavailableException("Leaderboard service temporarily unavailable");
        }
    }

    public List<LeaderboardEntryResponse> getNearbyPlayers(String sport, Long userId, int range) {
        String key = getKeyOrThrow(sport);
        int effectiveRange = Math.min(Math.max(range, 1), maxNearbyRange);
        try {
            Double score = redisRepository.getScore(key, String.valueOf(userId));
            if (score == null) {
                throw new ResourceNotFoundException("Player " + userId + " not found on " + sport + " leaderboard");
            }
            Long rank = redisRepository.getRank(key, String.valueOf(userId));
            if (rank == null) {
                throw new ResourceNotFoundException("Player " + userId + " not found on " + sport + " leaderboard");
            }
            List<Map.Entry<String, Double>> entries = redisRepository.nearbyEntries(
                    key, String.valueOf(userId), effectiveRange);
            List<LeaderboardEntryResponse> result = new ArrayList<>();
            int startRank = toApiRank(Math.max(0, rank - effectiveRange));
            for (int i = 0; i < entries.size(); i++) {
                result.add(toEntry(entries.get(i), startRank + i));
            }
            return result;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error getting nearby for userId={} sport={}: {}", userId, sport, e.getMessage());
            throw new ServiceUnavailableException("Leaderboard service temporarily unavailable");
        }
    }

    public SizeResponse getSize(String sport) {
        String key = getKeyOrThrow(sport);
        try {
            long totalPlayers = getTotalPlayersFromKey(key);
            return new SizeResponse(sport.toUpperCase(), totalPlayers);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error getting size for sport={}: {}", sport, e.getMessage());
            throw new ServiceUnavailableException("Leaderboard service temporarily unavailable");
        }
    }

    private long getTotalPlayersFromKey(String key) {
        Long size = redisRepository.size(key);
        return size != null ? size : 0;
    }
}
