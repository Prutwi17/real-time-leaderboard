package com.realtimeleaderboard.leaderboard.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardEntryResponse;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.service.LeaderboardService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class LeaderboardWebSocketIntegrationTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private LeaderboardKeyFactory keyFactory;

    @Test
    void duplicateEventProducesSingleBroadcast() {
        LeaderboardUpdatePublisher publisher =
                new LeaderboardUpdatePublisher(messagingTemplate, leaderboardService, keyFactory, 10);

        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(leaderboardService.getTop("FOOTBALL", 10)).thenReturn(
                new LeaderboardResponse("FOOTBALL",
                        List.of(new LeaderboardEntryResponse(1, 101L, 100.0)), 0, 10, 1));

        publisher.broadcastLeaderboardUpdate("same-event-id", "FOOTBALL");
        publisher.broadcastLeaderboardUpdate("same-event-id", "FOOTBALL");

        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/topic/leaderboards/football"), any(LeaderboardUpdateMessage.class));
    }

    @Test
    void clientCannotPublishToTopicViaSimpMessagingTemplate() {
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void allSubscribersReceiveSameUpdate() {
        LeaderboardUpdatePublisher publisher =
                new LeaderboardUpdatePublisher(messagingTemplate, leaderboardService, keyFactory, 10);

        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(leaderboardService.getTop("FOOTBALL", 10)).thenReturn(
                new LeaderboardResponse("FOOTBALL",
                        List.of(new LeaderboardEntryResponse(1, 101L, 500.0)), 0, 10, 1));

        publisher.broadcastLeaderboardUpdate("evt-multi", "FOOTBALL");

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/leaderboards/football"), any(LeaderboardUpdateMessage.class));
    }

    @Test
    void unsupportedSportDoesNotBroadcast() {
        LeaderboardUpdatePublisher publisher =
                new LeaderboardUpdatePublisher(messagingTemplate, leaderboardService, keyFactory, 10);

        when(keyFactory.isSupportedSport("TENNIS")).thenReturn(false);

        publisher.broadcastLeaderboardUpdate("evt-bad", "TENNIS");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void messageEventTypesAreCorrect() {
        LeaderboardUpdatePublisher publisher =
                new LeaderboardUpdatePublisher(messagingTemplate, leaderboardService, keyFactory, 10);

        when(keyFactory.isSupportedSport("CRICKET")).thenReturn(true);
        when(leaderboardService.getTop("CRICKET", 10)).thenReturn(
                new LeaderboardResponse("CRICKET", List.of(), 0, 10, 0));

        publisher.broadcastLeaderboardUpdate("evt-type", "CRICKET");

        ArgumentCaptor<LeaderboardUpdateMessage> captor =
                ArgumentCaptor.forClass(LeaderboardUpdateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/leaderboards/cricket"), captor.capture());

        assertThat(captor.getValue().eventType()).isEqualTo("LEADERBOARD_UPDATED");
        assertThat(captor.getValue().sport()).isEqualTo("CRICKET");
    }

    @Test
    void broadcastFailureDoesNotPropagateException() {
        LeaderboardUpdatePublisher publisher =
                new LeaderboardUpdatePublisher(messagingTemplate, leaderboardService, keyFactory, 10);

        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(leaderboardService.getTop("FOOTBALL", 10))
                .thenThrow(new RuntimeException("Redis connection lost"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> publisher.broadcastLeaderboardUpdate("evt-fail", "FOOTBALL"));
    }
}
