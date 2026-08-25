package com.realtimeleaderboard.leaderboard.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.realtimeleaderboard.leaderboard.dto.request.LeaderboardScoreUpdateRequest;
import com.realtimeleaderboard.leaderboard.dto.response.MessageResponse;
import com.realtimeleaderboard.leaderboard.exception.ForbiddenException;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.exception.ServiceUnavailableException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.redis.RedisLeaderboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardUpdateServiceTest {

    @Mock
    private RedisLeaderboardRepository redisRepository;

    @Mock
    private LeaderboardKeyFactory keyFactory;

    private LeaderboardUpdateService updateService;

    private static final String SECRET = "test-internal-secret";

    @BeforeEach
    void setUp() {
        updateService = new LeaderboardUpdateService(redisRepository, keyFactory, SECRET, 72);
    }

    @Test
    void validateInternalSecretSuccess() {
        assertDoesNotThrow(() -> updateService.validateInternalSecret(SECRET));
    }

    @Test
    void validateInternalSecretInvalid() {
        assertThrows(ForbiddenException.class,
                () -> updateService.validateInternalSecret("wrong-secret"));
    }

    @Test
    void validateInternalSecretNull() {
        LeaderboardUpdateService noSecretService =
                new LeaderboardUpdateService(redisRepository, keyFactory, "", 72);
        assertThrows(ForbiddenException.class,
                () -> noSecretService.validateInternalSecret("anything"));
    }

    @Test
    void processScoreUpdateSuccess() {
        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(keyFactory.leaderboardKey("FOOTBALL")).thenReturn("leaderboard:football");
        when(keyFactory.processedScoreKey("score-1")).thenReturn("leaderboard:processed:score-1");
        when(redisRepository.isProcessedScore("leaderboard:processed:score-1")).thenReturn(false);

        LeaderboardScoreUpdateRequest request =
                new LeaderboardScoreUpdateRequest(101L, 1L, 100.0, "score-1");
        MessageResponse response = updateService.processScoreUpdate(request);

        assertEquals("Leaderboard updated", response.message());
        verify(redisRepository).incrementScore("leaderboard:football", "101", 100.0);
        verify(redisRepository).setProcessedScore("leaderboard:processed:score-1", 72);
    }

    @Test
    void processScoreIdempotentDuplicate() {
        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(keyFactory.processedScoreKey("score-1")).thenReturn("leaderboard:processed:score-1");
        when(redisRepository.isProcessedScore("leaderboard:processed:score-1")).thenReturn(true);

        LeaderboardScoreUpdateRequest request =
                new LeaderboardScoreUpdateRequest(101L, 1L, 100.0, "score-1");
        MessageResponse response = updateService.processScoreUpdate(request);

        assertEquals("Score already processed", response.message());
        verify(redisRepository, never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    void processScoreUnsupportedSport() {
        when(keyFactory.isSupportedSport("")).thenReturn(false);

        LeaderboardScoreUpdateRequest request =
                new LeaderboardScoreUpdateRequest(101L, 99L, 100.0, "score-1");
        assertThrows(InvalidSportException.class, () -> updateService.processScoreUpdate(request));
    }

    @Test
    void processScoreRedisFailure() {
        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(keyFactory.leaderboardKey("FOOTBALL")).thenReturn("leaderboard:football");
        when(keyFactory.processedScoreKey("score-1")).thenReturn("leaderboard:processed:score-1");
        when(redisRepository.isProcessedScore("leaderboard:processed:score-1")).thenReturn(false);
        doThrow(new RuntimeException("Redis down"))
                .when(redisRepository).incrementScore(anyString(), anyString(), anyDouble());

        LeaderboardScoreUpdateRequest request =
                new LeaderboardScoreUpdateRequest(101L, 1L, 100.0, "score-1");
        assertThrows(ServiceUnavailableException.class, () -> updateService.processScoreUpdate(request));
    }

    @Test
    void sportIdMapping() {
        when(keyFactory.isSupportedSport("CRICKET")).thenReturn(true);
        when(keyFactory.leaderboardKey("CRICKET")).thenReturn("leaderboard:cricket");
        when(keyFactory.processedScoreKey("score-2")).thenReturn("leaderboard:processed:score-2");
        when(redisRepository.isProcessedScore("leaderboard:processed:score-2")).thenReturn(false);

        LeaderboardScoreUpdateRequest request =
                new LeaderboardScoreUpdateRequest(202L, 2L, 50.0, "score-2");
        MessageResponse response = updateService.processScoreUpdate(request);
        assertEquals("Leaderboard updated", response.message());
        verify(redisRepository).incrementScore("leaderboard:cricket", "202", 50.0);
    }

    @Test
    void f1SportIdMapping() {
        when(keyFactory.isSupportedSport("F1")).thenReturn(true);
        when(keyFactory.leaderboardKey("F1")).thenReturn("leaderboard:f1");
        when(keyFactory.processedScoreKey("score-3")).thenReturn("leaderboard:processed:score-3");
        when(redisRepository.isProcessedScore("leaderboard:processed:score-3")).thenReturn(false);

        LeaderboardScoreUpdateRequest request =
                new LeaderboardScoreUpdateRequest(303L, 3L, 200.0, "score-3");
        MessageResponse response = updateService.processScoreUpdate(request);
        assertEquals("Leaderboard updated", response.message());
        verify(redisRepository).incrementScore("leaderboard:f1", "303", 200.0);
    }
}
