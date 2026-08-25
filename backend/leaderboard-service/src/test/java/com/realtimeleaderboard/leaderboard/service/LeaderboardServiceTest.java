package com.realtimeleaderboard.leaderboard.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.dto.response.PlayerRankResponse;
import com.realtimeleaderboard.leaderboard.dto.response.SizeResponse;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.exception.ResourceNotFoundException;
import com.realtimeleaderboard.leaderboard.exception.ServiceUnavailableException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.redis.RedisLeaderboardRepository;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private RedisLeaderboardRepository redisRepository;

    @Mock
    private LeaderboardKeyFactory keyFactory;

    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(
                redisRepository, keyFactory, 10, 100, 20, 100, 2, 10);
    }

    private void mockSupportedSport(String sport) {
        when(keyFactory.isSupportedSport(sport)).thenReturn(true);
        when(keyFactory.leaderboardKey(sport)).thenReturn("leaderboard:" + sport.toLowerCase());
    }

    @Test
    void getTopReturnsEntries() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.size("leaderboard:football")).thenReturn(3L);
        when(redisRepository.topNEntries("leaderboard:football", 10)).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>("101", 950.0),
                new AbstractMap.SimpleEntry<>("102", 820.0),
                new AbstractMap.SimpleEntry<>("103", 710.0)
        ));

        LeaderboardResponse response = leaderboardService.getTop("FOOTBALL", 10);
        assertEquals("FOOTBALL", response.sport());
        assertEquals(3, response.entries().size());
        assertEquals(1, response.entries().get(0).rank());
        assertEquals(101L, response.entries().get(0).userId());
        assertEquals(950.0, response.entries().get(0).score());
        assertEquals(2, response.entries().get(1).rank());
        assertEquals(3, response.entries().get(2).rank());
    }

    @Test
    void getTopEmptyLeaderboard() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.size("leaderboard:football")).thenReturn(0L);

        LeaderboardResponse response = leaderboardService.getTop("FOOTBALL", 10);
        assertEquals(0, response.entries().size());
        assertEquals(0, response.totalPlayers());
    }

    @Test
    void getTopInvalidSport() {
        when(keyFactory.isSupportedSport("BASKETBALL")).thenReturn(false);
        assertThrows(InvalidSportException.class, () -> leaderboardService.getTop("BASKETBALL", 10));
    }

    @Test
    void getTopRedisUnavailable() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.size("leaderboard:football")).thenThrow(new RuntimeException("Connection refused"));
        assertThrows(ServiceUnavailableException.class, () -> leaderboardService.getTop("FOOTBALL", 10));
    }

    @Test
    void getLeaderboardPagination() {
        mockSupportedSport("CRICKET");
        when(redisRepository.size("leaderboard:cricket")).thenReturn(50L);
        when(redisRepository.pageEntries("leaderboard:cricket", 20L, 20)).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>("201", 500.0),
                new AbstractMap.SimpleEntry<>("202", 450.0)
        ));

        LeaderboardResponse response = leaderboardService.getLeaderboard("CRICKET", 1, 20);
        assertEquals("CRICKET", response.sport());
        assertEquals(1, response.page());
        assertEquals(20, response.size());
        assertEquals(50, response.totalPlayers());
        assertEquals(21, response.entries().get(0).rank());
        assertEquals(22, response.entries().get(1).rank());
    }

    @Test
    void getLeaderboardOutOfBoundsPage() {
        mockSupportedSport("CRICKET");
        when(redisRepository.size("leaderboard:cricket")).thenReturn(10L);

        LeaderboardResponse response = leaderboardService.getLeaderboard("CRICKET", 5, 20);
        assertEquals(0, response.entries().size());
    }

    @Test
    void getPlayerRankSuccess() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.getScore("leaderboard:football", "101")).thenReturn(950.0);
        when(redisRepository.getRank("leaderboard:football", "101")).thenReturn(0L);

        PlayerRankResponse response = leaderboardService.getPlayerRank("FOOTBALL", 101L);
        assertEquals("FOOTBALL", response.sport());
        assertEquals(101L, response.userId());
        assertEquals(1, response.rank());
        assertEquals(950.0, response.score());
    }

    @Test
    void getPlayerRankNotFound() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.getScore("leaderboard:football", "999")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> leaderboardService.getPlayerRank("FOOTBALL", 999L));
    }

    @Test
    void getNearbyPlayers() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.getScore("leaderboard:football", "101")).thenReturn(800.0);
        when(redisRepository.getRank("leaderboard:football", "101")).thenReturn(4L);
        when(redisRepository.nearbyEntries("leaderboard:football", "101", 2)).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>("100", 900.0),
                new AbstractMap.SimpleEntry<>("105", 850.0),
                new AbstractMap.SimpleEntry<>("101", 800.0),
                new AbstractMap.SimpleEntry<>("103", 750.0),
                new AbstractMap.SimpleEntry<>("107", 700.0)
        ));

        var response = leaderboardService.getNearbyPlayers("FOOTBALL", 101L, 2);
        assertEquals(5, response.size());
        assertEquals(3, response.get(0).rank());
        assertEquals(4, response.get(1).rank());
        assertEquals(5, response.get(2).rank());
    }

    @Test
    void getNearbyPlayersNotFound() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.getScore("leaderboard:football", "999")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> leaderboardService.getNearbyPlayers("FOOTBALL", 999L, 2));
    }

    @Test
    void getSize() {
        mockSupportedSport("F1");
        when(redisRepository.size("leaderboard:f1")).thenReturn(25L);

        SizeResponse response = leaderboardService.getSize("F1");
        assertEquals("F1", response.sport());
        assertEquals(25, response.totalPlayers());
    }

    @Test
    void rankConversionZeroToOneBased() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.getScore("leaderboard:football", "101")).thenReturn(950.0);
        when(redisRepository.getRank("leaderboard:football", "101")).thenReturn(0L);

        PlayerRankResponse response = leaderboardService.getPlayerRank("FOOTBALL", 101L);
        assertEquals(1, response.rank());
    }

    @Test
    void limitCapping() {
        mockSupportedSport("FOOTBALL");
        when(redisRepository.size("leaderboard:football")).thenReturn(200L);
        when(redisRepository.topNEntries("leaderboard:football", 100)).thenReturn(List.of());

        LeaderboardResponse response = leaderboardService.getTop("FOOTBALL", 200);
        assertEquals(100, response.size());
    }
}
