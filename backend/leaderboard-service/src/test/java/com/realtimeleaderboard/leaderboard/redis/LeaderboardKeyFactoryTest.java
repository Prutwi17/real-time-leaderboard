package com.realtimeleaderboard.leaderboard.redis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeaderboardKeyFactoryTest {

    private LeaderboardKeyFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LeaderboardKeyFactory("FOOTBALL,CRICKET,F1");
    }

    @Test
    void footballKey() {
        assertEquals("leaderboard:football", factory.leaderboardKey("FOOTBALL"));
    }

    @Test
    void cricketKey() {
        assertEquals("leaderboard:cricket", factory.leaderboardKey("CRICKET"));
    }

    @Test
    void f1Key() {
        assertEquals("leaderboard:f1", factory.leaderboardKey("F1"));
    }

    @Test
    void caseInsensitive() {
        assertEquals("leaderboard:football", factory.leaderboardKey("football"));
        assertEquals("leaderboard:cricket", factory.leaderboardKey("Cricket"));
    }

    @Test
    void unsupportedSportThrows() {
        assertThrows(IllegalArgumentException.class, () -> factory.leaderboardKey("BASKETBALL"));
        assertThrows(IllegalArgumentException.class, () -> factory.leaderboardKey("TENNIS"));
    }

    @Test
    void isSupportedSport() {
        assertTrue(factory.isSupportedSport("FOOTBALL"));
        assertTrue(factory.isSupportedSport("cricket"));
        assertTrue(factory.isSupportedSport("f1"));
        assertFalse(factory.isSupportedSport("BASKETBALL"));
        assertFalse(factory.isSupportedSport(null));
    }

    @Test
    void processedScoreKey() {
        assertEquals("leaderboard:processed:abc123", factory.processedScoreKey("abc123"));
    }

    @Test
    void supportedSportsList() {
        assertEquals(3, factory.getSupportedSports().size());
        assertTrue(factory.getSupportedSports().contains("FOOTBALL"));
        assertTrue(factory.getSupportedSports().contains("CRICKET"));
        assertTrue(factory.getSupportedSports().contains("F1"));
    }
}
