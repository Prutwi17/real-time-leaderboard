package com.realtimeleaderboard.leaderboard.websocket;

import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardEntryResponse;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.service.LeaderboardService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardUpdatePublisher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final LeaderboardService leaderboardService;
    private final LeaderboardKeyFactory keyFactory;
    private final int defaultTopN;

    public LeaderboardUpdatePublisher(SimpMessagingTemplate messagingTemplate,
                                       LeaderboardService leaderboardService,
                                       LeaderboardKeyFactory keyFactory,
                                       @Value("${leaderboard.websocket.top-n:10}") int defaultTopN) {
        this.messagingTemplate = messagingTemplate;
        this.leaderboardService = leaderboardService;
        this.keyFactory = keyFactory;
        this.defaultTopN = defaultTopN;
    }

    public void broadcastLeaderboardUpdate(String eventId, String sport) {
        if (!keyFactory.isSupportedSport(sport)) {
            log.warn("Cannot broadcast for unsupported sport: {}", sport);
            return;
        }

        try {
            LeaderboardResponse topBoard = leaderboardService.getTop(sport, defaultTopN);
            String sportLower = sport.toLowerCase();

            LeaderboardUpdateMessage.LeaderboardSnapshot snapshot =
                    new LeaderboardUpdateMessage.LeaderboardSnapshot(
                            sport,
                            topBoard.entries(),
                            topBoard.totalPlayers());

            LeaderboardUpdateMessage message = new LeaderboardUpdateMessage(
                    eventId,
                    "LEADERBOARD_UPDATED",
                    sport,
                    Instant.now(),
                    snapshot);

            String destination = "/topic/leaderboards/" + sportLower;
            messagingTemplate.convertAndSend(destination, message);

            log.info("Broadcast leaderboard update: sport={}, eventId={}, entries={}, totalPlayers={}, destination={}",
                    sport, eventId, topBoard.entries().size(), topBoard.totalPlayers(), destination);
        } catch (Exception e) {
            log.error("Failed to broadcast leaderboard update for sport={}, eventId={}: {}",
                    sport, eventId, e.getMessage());
        }
    }
}
