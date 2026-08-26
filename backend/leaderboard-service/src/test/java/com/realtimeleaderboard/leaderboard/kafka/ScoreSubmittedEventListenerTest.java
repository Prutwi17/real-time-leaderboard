package com.realtimeleaderboard.leaderboard.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeleaderboard.leaderboard.dto.request.LeaderboardScoreUpdateRequest;
import com.realtimeleaderboard.leaderboard.dto.response.MessageResponse;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.redis.LeaderboardKeyFactory;
import com.realtimeleaderboard.leaderboard.service.LeaderboardUpdateService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class ScoreSubmittedEventListenerTest {

    @Mock private LeaderboardUpdateService updateService;
    @Mock private LeaderboardKeyFactory keyFactory;
    @Mock private Acknowledgment acknowledgment;
    private ScoreSubmittedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ScoreSubmittedEventListener(updateService, keyFactory);
    }

    @Test
    void validEventCallsUpdateService() {
        when(updateService.processScoreUpdate(any())).thenReturn(new MessageResponse("Leaderboard updated"));

        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 1, "score-1", 10L, 1L, 100.0, "GOALS", "EPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);

        ArgumentCaptor<LeaderboardScoreUpdateRequest> captor = ArgumentCaptor.forClass(LeaderboardScoreUpdateRequest.class);
        verify(updateService).processScoreUpdate(captor.capture());
        LeaderboardScoreUpdateRequest req = captor.getValue();
        assertThat(req.userId()).isEqualTo(10L);
        assertThat(req.sportId()).isEqualTo(1L);
        assertThat(req.score()).isEqualTo(100.0);
        assertThat(req.scoreId()).isEqualTo("evt-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void nullEventIsAcknowledged() {
        listener.onScoreSubmitted(null, "key-1", 0, 0, acknowledgment);
        verify(updateService, never()).processScoreUpdate(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void unsupportedVersionIsAcknowledged() {
        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 99, "score-1", 10L, 1L, 100.0, "GOALS", "EPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);
        verify(updateService, never()).processScoreUpdate(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void missingEventIdIsAcknowledged() {
        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "", 1, "score-1", 10L, 1L, 100.0, "GOALS", "EPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);
        verify(updateService, never()).processScoreUpdate(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void missingUserIdIsAcknowledged() {
        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 1, "score-1", null, 1L, 100.0, "GOALS", "EPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);
        verify(updateService, never()).processScoreUpdate(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void missingSportIdIsAcknowledged() {
        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 1, "score-1", 10L, null, 100.0, "GOALS", "EPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);
        verify(updateService, never()).processScoreUpdate(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void unsupportedSportIsAcknowledged() {
        when(updateService.processScoreUpdate(any()))
                .thenThrow(new InvalidSportException("Unsupported sportId: 99"));

        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 1, "score-1", 10L, 99L, 100.0, "GOALS", "EPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void footballEventIsProcessed() {
        when(updateService.processScoreUpdate(any())).thenReturn(new MessageResponse("Updated"));
        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 1, "score-1", 10L, 1L, 95.0, "GOALS", "EPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);

        ArgumentCaptor<LeaderboardScoreUpdateRequest> captor = ArgumentCaptor.forClass(LeaderboardScoreUpdateRequest.class);
        verify(updateService).processScoreUpdate(captor.capture());
        assertThat(captor.getValue().sportId()).isEqualTo(1L);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void cricketEventIsProcessed() {
        when(updateService.processScoreUpdate(any())).thenReturn(new MessageResponse("Updated"));
        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 1, "score-1", 20L, 2L, 82.0, "RUNS", "IPL",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);

        ArgumentCaptor<LeaderboardScoreUpdateRequest> captor = ArgumentCaptor.forClass(LeaderboardScoreUpdateRequest.class);
        verify(updateService).processScoreUpdate(captor.capture());
        assertThat(captor.getValue().sportId()).isEqualTo(2L);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void f1EventIsProcessed() {
        when(updateService.processScoreUpdate(any())).thenReturn(new MessageResponse("Updated"));
        ScoreSubmittedEvent event = new ScoreSubmittedEvent(
                "evt-1", 1, "score-1", 30L, 3L, 85.5, "LAP_TIME", "Monaco",
                Instant.now(), Instant.now());

        listener.onScoreSubmitted(event, "key-1", 0, 0, acknowledgment);

        ArgumentCaptor<LeaderboardScoreUpdateRequest> captor = ArgumentCaptor.forClass(LeaderboardScoreUpdateRequest.class);
        verify(updateService).processScoreUpdate(captor.capture());
        assertThat(captor.getValue().sportId()).isEqualTo(3L);
        verify(acknowledgment).acknowledge();
    }
}
