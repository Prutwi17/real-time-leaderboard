package com.realtimeleaderboard.leaderboard.redis;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardKeyFactory {

    private static final Map<String, String> SPORT_KEY_MAP = Map.of(
            "FOOTBALL", "leaderboard:football",
            "CRICKET", "leaderboard:cricket",
            "F1", "leaderboard:f1"
    );

    private final Set<String> supportedSports;

    public LeaderboardKeyFactory(
            @Value("${leaderboard.supported-sports}") String supportedSportsCsv) {
        this.supportedSports = Set.of(supportedSportsCsv.split(","))
                .stream().map(String::trim).collect(Collectors.toSet());
    }

    public String leaderboardKey(String sport) {
        String key = SPORT_KEY_MAP.get(sport.toUpperCase());
        if (key == null) {
            throw new IllegalArgumentException("Unsupported sport: " + sport);
        }
        return key;
    }

    public String processedScoreKey(String scoreId) {
        return "leaderboard:processed:" + scoreId;
    }

    public boolean isSupportedSport(String sport) {
        return sport != null && SPORT_KEY_MAP.containsKey(sport.toUpperCase());
    }

    public Set<String> getSupportedSports() {
        return supportedSports;
    }
}
