package com.realtimeleaderboard.leaderboard.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardEntryResponse;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.service.LeaderboardService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class LeaderboardUpdatePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private LeaderboardKeyFactory keyFactory;

    private LeaderboardUpdatePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new LeaderboardUpdatePublisher(messagingTemplate, leaderboardService, keyFactory, 10);
    }

    @Test
    void broadcastSendsToCorrectTopic() {
        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        LeaderboardResponse response = new LeaderboardResponse(
                "FOOTBALL",
                List.of(new LeaderboardEntryResponse(1, 101L, 950.0)),
                0, 10, 1);
        when(leaderboardService.getTop("FOOTBALL", 10)).thenReturn(response);

        publisher.broadcastLeaderboardUpdate("evt-1", "FOOTBALL");

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destCaptor.capture(), any(LeaderboardUpdateMessage.class));
        assertThat(destCaptor.getValue()).isEqualTo("/topic/leaderboards/football");
    }

    @Test
    void broadcastCreatesCorrectMessageStructure() {
        when(keyFactory.isSupportedSport("CRICKET")).thenReturn(true);
        LeaderboardResponse response = new LeaderboardResponse(
                "CRICKET",
                List.of(
                        new LeaderboardEntryResponse(1, 201L, 800.0),
                        new LeaderboardEntryResponse(2, 202L, 750.0)),
                0, 10, 2);
        when(leaderboardService.getTop("CRICKET", 10)).thenReturn(response);

        publisher.broadcastLeaderboardUpdate("evt-2", "CRICKET");

        ArgumentCaptor<LeaderboardUpdateMessage> msgCaptor = ArgumentCaptor.forClass(LeaderboardUpdateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/leaderboards/cricket"), msgCaptor.capture());

        LeaderboardUpdateMessage msg = msgCaptor.getValue();
        assertThat(msg.eventId()).isEqualTo("evt-2");
        assertThat(msg.eventType()).isEqualTo("LEADERBOARD_UPDATED");
        assertThat(msg.sport()).isEqualTo("CRICKET");
        assertThat(msg.timestamp()).isNotNull();
        assertThat(msg.leaderboard()).isNotNull();
        assertThat(msg.leaderboard().sport()).isEqualTo("CRICKET");
        assertThat(msg.leaderboard().entries()).hasSize(2);
        assertThat(msg.leaderboard().totalPlayers()).isEqualTo(2);
        assertThat(msg.leaderboard().entries().get(0).userId()).isEqualTo(201L);
        assertThat(msg.leaderboard().entries().get(0).score()).isEqualTo(800.0);
    }

    @Test
    void broadcastF1UsesCorrectTopic() {
        when(keyFactory.isSupportedSport("F1")).thenReturn(true);
        when(leaderboardService.getTop("F1", 10)).thenReturn(
                new LeaderboardResponse("F1", List.of(), 0, 10, 0));

        publisher.broadcastLeaderboardUpdate("evt-3", "F1");

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destCaptor.capture(), any(LeaderboardUpdateMessage.class));
        assertThat(destCaptor.getValue()).isEqualTo("/topic/leaderboards/f1");
    }

    @Test
    void broadcastUnsupportedSportDoesNothing() {
        when(keyFactory.isSupportedSport("TENNIS")).thenReturn(false);

        publisher.broadcastLeaderboardUpdate("evt-4", "TENNIS");

        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(leaderboardService);
    }

    @Test
    void broadcastRedisFailureDoesNotCrash() {
        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(leaderboardService.getTop("FOOTBALL", 10))
                .thenThrow(new RuntimeException("Redis down"));

        publisher.broadcastLeaderboardUpdate("evt-5", "FOOTBALL");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void broadcastEmptyLeaderboardStillBroadcasts() {
        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        when(leaderboardService.getTop("FOOTBALL", 10)).thenReturn(
                new LeaderboardResponse("FOOTBALL", List.of(), 0, 10, 0));

        publisher.broadcastLeaderboardUpdate("evt-6", "FOOTBALL");

        ArgumentCaptor<LeaderboardUpdateMessage> msgCaptor = ArgumentCaptor.forClass(LeaderboardUpdateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/leaderboards/football"), msgCaptor.capture());
        assertThat(msgCaptor.getValue().leaderboard().entries()).isEmpty();
    }

    @Test
    void broadcastMultipleEntriesCorrectRanking() {
        when(keyFactory.isSupportedSport("FOOTBALL")).thenReturn(true);
        List<LeaderboardEntryResponse> entries = List.of(
                new LeaderboardEntryResponse(1, 101L, 950.0),
                new LeaderboardEntryResponse(2, 102L, 900.0),
                new LeaderboardEntryResponse(3, 103L, 850.0));
        when(leaderboardService.getTop("FOOTBALL", 10)).thenReturn(
                new LeaderboardResponse("FOOTBALL", entries, 0, 10, 3));

        publisher.broadcastLeaderboardUpdate("evt-7", "FOOTBALL");

        ArgumentCaptor<LeaderboardUpdateMessage> msgCaptor = ArgumentCaptor.forClass(LeaderboardUpdateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/leaderboards/football"), msgCaptor.capture());
        assertThat(msgCaptor.getValue().leaderboard().entries()).hasSize(3);
        assertThat(msgCaptor.getValue().leaderboard().totalPlayers()).isEqualTo(3);
    }
}
