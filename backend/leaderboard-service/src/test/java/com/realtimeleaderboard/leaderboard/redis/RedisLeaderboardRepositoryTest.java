package com.realtimeleaderboard.leaderboard.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisLeaderboardRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisLeaderboardRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        repository = new RedisLeaderboardRepository(redisTemplate);
    }

    @Test
    void incrementScore() {
        when(zSetOps.incrementScore("leaderboard:football", "101", 100.0)).thenReturn(100.0);
        repository.incrementScore("leaderboard:football", "101", 100.0);
        verify(zSetOps).incrementScore("leaderboard:football", "101", 100.0);
    }

    @Test
    void getScore() {
        when(zSetOps.score("leaderboard:football", "101")).thenReturn(950.0);
        assertEquals(950.0, repository.getScore("leaderboard:football", "101"));
    }

    @Test
    void getScoreNotFound() {
        when(zSetOps.score("leaderboard:football", "999")).thenReturn(null);
        assertNull(repository.getScore("leaderboard:football", "999"));
    }

    @Test
    void getRank() {
        when(zSetOps.reverseRank("leaderboard:football", "101")).thenReturn(0L);
        assertEquals(0L, repository.getRank("leaderboard:football", "101"));
    }

    @Test
    void getRankNotFound() {
        when(zSetOps.reverseRank("leaderboard:football", "999")).thenReturn(null);
        assertNull(repository.getRank("leaderboard:football", "999"));
    }

    @Test
    void size() {
        when(zSetOps.zCard("leaderboard:football")).thenReturn(150L);
        assertEquals(150L, repository.size("leaderboard:football"));
    }

    @Test
    void delete() {
        repository.delete("leaderboard:football");
        verify(redisTemplate).delete("leaderboard:football");
    }

    @Test
    void hasKey() {
        when(redisTemplate.hasKey("leaderboard:football")).thenReturn(true);
        assertTrue(repository.hasKey("leaderboard:football"));
    }

    @Test
    void setProcessedScore() {
        repository.setProcessedScore("leaderboard:processed:abc", 72);
        verify(valueOps).set(eq("leaderboard:processed:abc"), eq("1"), eq(72L), any());
    }

    @Test
    void isProcessedScore() {
        when(redisTemplate.hasKey("leaderboard:processed:abc")).thenReturn(true);
        assertTrue(repository.isProcessedScore("leaderboard:processed:abc"));
    }

    @Test
    void isProcessedScoreNotFound() {
        when(redisTemplate.hasKey("leaderboard:processed:xyz")).thenReturn(false);
        assertFalse(repository.isProcessedScore("leaderboard:processed:xyz"));
    }
}
