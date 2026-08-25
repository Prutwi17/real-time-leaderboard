package com.realtimeleaderboard.leaderboard.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.realtimeleaderboard.leaderboard.dto.request.LeaderboardScoreUpdateRequest;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.dto.response.MessageResponse;
import com.realtimeleaderboard.leaderboard.dto.response.PlayerRankResponse;
import com.realtimeleaderboard.leaderboard.dto.response.SizeResponse;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.exception.ResourceNotFoundException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.redis.RedisLeaderboardRepository;
import com.realtimeleaderboard.leaderboard.service.LeaderboardService;
import com.realtimeleaderboard.leaderboard.service.LeaderboardUpdateService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LeaderboardServiceIntegrationTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private LeaderboardUpdateService updateService;

    @Autowired
    private RedisLeaderboardRepository redisRepository;

    @Autowired
    private LeaderboardKeyFactory keyFactory;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        for (String sport : List.of("FOOTBALL", "CRICKET", "F1")) {
            String key = keyFactory.leaderboardKey(sport);
            redisRepository.delete(key);
        }
        Set<String> processedKeys = redisTemplate.keys("leaderboard:processed:*");
        if (processedKeys != null && !processedKeys.isEmpty()) {
            redisTemplate.delete(processedKeys);
        }
    }

    private void addScore(Long userId, Long sportId, double score, String scoreId) {
        LeaderboardScoreUpdateRequest request = new LeaderboardScoreUpdateRequest(userId, sportId, score, scoreId);
        updateService.processScoreUpdate(request);
    }

    @Test
    void fullLeaderboardFlow() {
        addScore(101L, 1L, 950.0, "fs-1");
        addScore(102L, 1L, 820.0, "fs-2");
        addScore(103L, 1L, 710.0, "fs-3");

        LeaderboardResponse top = leaderboardService.getTop("FOOTBALL", 10);
        assertEquals(3, top.entries().size());
        assertEquals(101L, top.entries().get(0).userId());
        assertEquals(1, top.entries().get(0).rank());
        assertEquals(3, top.totalPlayers());
    }

    @Test
    void cricketLeaderboard() {
        addScore(201L, 2L, 500.0, "cs-1");
        addScore(202L, 2L, 450.0, "cs-2");

        LeaderboardResponse top = leaderboardService.getTop("CRICKET", 10);
        assertEquals(2, top.entries().size());
        assertEquals("CRICKET", top.sport());
    }

    @Test
    void f1Leaderboard() {
        addScore(301L, 3L, 1000.0, "f1s-1");
        addScore(302L, 3L, 800.0, "f1s-2");

        LeaderboardResponse top = leaderboardService.getTop("F1", 10);
        assertEquals(2, top.entries().size());
        assertEquals("F1", top.sport());
    }

    @Test
    void pagination() {
        for (int i = 1; i <= 30; i++) {
            addScore((long) i, 1L, 100.0 + i, "page-" + i);
        }

        LeaderboardResponse page0 = leaderboardService.getLeaderboard("FOOTBALL", 0, 10);
        assertEquals(10, page0.entries().size());
        assertEquals(0, page0.page());
        assertEquals(30, page0.totalPlayers());

        LeaderboardResponse page1 = leaderboardService.getLeaderboard("FOOTBALL", 1, 10);
        assertEquals(10, page1.entries().size());
        assertEquals(1, page1.page());
        assertEquals(11, page1.entries().get(0).rank());
    }

    @Test
    void playerRankLookup() {
        addScore(101L, 1L, 950.0, "rank-1");
        addScore(102L, 1L, 820.0, "rank-2");
        addScore(103L, 1L, 710.0, "rank-3");

        PlayerRankResponse rank = leaderboardService.getPlayerRank("FOOTBALL", 102L);
        assertEquals(2, rank.rank());
        assertEquals(820.0, rank.score());
        assertEquals("FOOTBALL", rank.sport());
    }

    @Test
    void playerRankNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> leaderboardService.getPlayerRank("FOOTBALL", 999L));
    }

    @Test
    void scoreAggregation() {
        addScore(101L, 1L, 100.0, "agg-1");
        addScore(101L, 1L, 200.0, "agg-2");
        addScore(101L, 1L, 300.0, "agg-3");

        PlayerRankResponse rank = leaderboardService.getPlayerRank("FOOTBALL", 101L);
        assertEquals(600.0, rank.score());
    }

    @Test
    void idempotencyDuplicateScore() {
        LeaderboardScoreUpdateRequest request = new LeaderboardScoreUpdateRequest(101L, 1L, 100.0, "idempotent-1");
        updateService.processScoreUpdate(request);

        MessageResponse second = updateService.processScoreUpdate(request);
        assertEquals("Score already processed", second.message());

        PlayerRankResponse rank = leaderboardService.getPlayerRank("FOOTBALL", 101L);
        assertEquals(100.0, rank.score());
    }

    @Test
    void leaderboardSize() {
        addScore(101L, 1L, 950.0, "size-1");
        addScore(102L, 1L, 820.0, "size-2");

        SizeResponse size = leaderboardService.getSize("FOOTBALL");
        assertEquals(2, size.totalPlayers());
    }

    @Test
    void invalidSportRejected() {
        assertThrows(InvalidSportException.class, () -> leaderboardService.getTop("BASKETBALL", 10));
    }

    @Test
    void nearbyPlayers() {
        for (int i = 1; i <= 10; i++) {
            addScore((long) i, 1L, 100.0 * i, "near-" + i);
        }

        var nearby = leaderboardService.getNearbyPlayers("FOOTBALL", 5L, 2);
        assertFalse(nearby.isEmpty());
        boolean containsTarget = nearby.stream().anyMatch(e -> e.userId() == 5L);
        assertTrue(containsTarget);
    }

    @Test
    void emptyLeaderboard() {
        LeaderboardResponse top = leaderboardService.getTop("FOOTBALL", 10);
        assertEquals(0, top.entries().size());
        assertEquals(0, top.totalPlayers());
    }

    @Test
    void rankConversionOneBased() {
        addScore(101L, 1L, 950.0, "conv-1");
        addScore(102L, 1L, 820.0, "conv-2");

        PlayerRankResponse rank = leaderboardService.getPlayerRank("FOOTBALL", 101L);
        assertEquals(1, rank.rank());

        PlayerRankResponse rank2 = leaderboardService.getPlayerRank("FOOTBALL", 102L);
        assertEquals(2, rank2.rank());
    }
}
