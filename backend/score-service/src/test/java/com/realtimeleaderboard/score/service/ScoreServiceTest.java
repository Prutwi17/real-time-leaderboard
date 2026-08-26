package com.realtimeleaderboard.score.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realtimeleaderboard.score.client.SportSnapshot;
import com.realtimeleaderboard.score.dto.request.CreateScoreRequest;
import com.realtimeleaderboard.score.dto.response.ScoreResponse;
import com.realtimeleaderboard.score.entity.Score;
import com.realtimeleaderboard.score.entity.ScoreType;
import com.realtimeleaderboard.score.exception.ConflictException;
import com.realtimeleaderboard.score.exception.ForbiddenException;
import com.realtimeleaderboard.score.exception.ResourceNotFoundException;
import com.realtimeleaderboard.score.repository.ScoreRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock private ScoreRepository scoreRepository;
    @Mock private SportValidationService sportValidationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    private ScoreService scoreService;

    @BeforeEach void setUp() { scoreService = new ScoreService(scoreRepository, sportValidationService, eventPublisher); }

    @Test
    void submitSavesScoreAfterSportValidation() {
        when(sportValidationService.validateSportForSubmission(1L))
                .thenReturn(new SportSnapshot(1L, "FOOTBALL", "Football", true));
        when(scoreRepository.existsByUserIdAndSubmissionId(10L, "sub-1")).thenReturn(false);
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> inv.getArgument(0, Score.class));

        CreateScoreRequest req = new CreateScoreRequest(1L, BigDecimal.valueOf(100), "EPL", "EPL-001", ScoreType.POINTS, "sub-1");
        ScoreResponse resp = scoreService.submit(10L, req);

        assertThat(resp.userId()).isEqualTo(10L);
        assertThat(resp.sportId()).isEqualTo(1L);
        assertThat(resp.value()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(resp.eventId()).isEqualTo("EPL-001");
        ArgumentCaptor<Score> cap = ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository).save(cap.capture());
        assertThat(cap.getValue().getSubmissionId()).isEqualTo("sub-1");
        verify(eventPublisher).publishEvent(any(Score.class));
    }

    @Test
    void submitPublishesEventAfterDbSave() {
        when(sportValidationService.validateSportForSubmission(1L))
                .thenReturn(new SportSnapshot(1L, "FOOTBALL", "Football", true));
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> inv.getArgument(0, Score.class));

        CreateScoreRequest req = new CreateScoreRequest(1L, BigDecimal.valueOf(50), null, null, ScoreType.GOALS, null);
        scoreService.submit(10L, req);

        ArgumentCaptor<Score> cap = ArgumentCaptor.forClass(Score.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(10L);
    }

    @Test
    void submitRejectsDuplicateSubmissionId() {
        when(sportValidationService.validateSportForSubmission(1L)).thenReturn(new SportSnapshot(1L, "FOOTBALL", "Football", true));
        when(scoreRepository.existsByUserIdAndSubmissionId(10L, "dup")).thenReturn(true);

        CreateScoreRequest req = new CreateScoreRequest(1L, BigDecimal.TEN, null, null, ScoreType.RUNS, "dup");
        assertThatThrownBy(() -> scoreService.submit(10L, req))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Duplicate");
        verify(scoreRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void submitAllowsNullSubmissionId() {
        when(sportValidationService.validateSportForSubmission(1L)).thenReturn(new SportSnapshot(1L, "FOOTBALL", "Football", true));
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> inv.getArgument(0, Score.class));

        CreateScoreRequest req = new CreateScoreRequest(1L, BigDecimal.valueOf(50), null, null, ScoreType.GOALS, null);
        ScoreResponse resp = scoreService.submit(10L, req);
        assertThat(resp.submissionId()).isNull();
    }

    @Test
    void sportValidationThrows404OnMissingSport() {
        when(sportValidationService.validateSportForSubmission(99L))
                .thenThrow(new ResourceNotFoundException("Sport not found: id 99"));
        CreateScoreRequest req = new CreateScoreRequest(99L, BigDecimal.ONE, null, null, ScoreType.POINTS, null);
        assertThatThrownBy(() -> scoreService.submit(10L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sportValidationThrows409OnInactiveSport() {
        when(sportValidationService.validateSportForSubmission(2L))
                .thenThrow(new ConflictException("Sport 'CRICKET' is not active"));
        CreateScoreRequest req = new CreateScoreRequest(2L, BigDecimal.ONE, null, null, ScoreType.RUNS, null);
        assertThatThrownBy(() -> scoreService.submit(10L, req))
                .isInstanceOf(ConflictException.class).hasMessageContaining("not active");
    }

    @Test
    void getByIdReturnsScoreForOwner() {
        Score score = new Score(); score.setUserId(10L); score.setSportId(1L);
        score.setValue(BigDecimal.TEN); score.setScoreType(ScoreType.RUNS);
        when(scoreRepository.findById(5L)).thenReturn(Optional.of(score));
        ScoreResponse resp = scoreService.getById(5L, 10L, "USER");
        assertThat(resp.id()).isNull();
        assertThat(resp.userId()).isEqualTo(10L);
    }

    @Test
    void getByIdAllowsAdminToAccessAnyScore() {
        Score score = new Score(); score.setUserId(10L); score.setSportId(1L);
        score.setValue(BigDecimal.TEN); score.setScoreType(ScoreType.RUNS);
        when(scoreRepository.findById(5L)).thenReturn(Optional.of(score));
        ScoreResponse resp = scoreService.getById(5L, 99L, "ADMIN");
        assertThat(resp.userId()).isEqualTo(10L);
    }

    @Test
    void getByIdRejectsUserAccessingOtherUserScore() {
        Score score = new Score(); score.setUserId(10L); score.setSportId(1L);
        score.setValue(BigDecimal.TEN); score.setScoreType(ScoreType.RUNS);
        when(scoreRepository.findById(5L)).thenReturn(Optional.of(score));
        assertThatThrownBy(() -> scoreService.getById(5L, 20L, "USER"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getByIdThrows404WhenScoreMissing() {
        when(scoreRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> scoreService.getById(999L, 10L, "ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesScore() {
        Score score = new Score(); score.setUserId(10L);
        when(scoreRepository.findById(5L)).thenReturn(Optional.of(score));
        scoreService.delete(5L);
        verify(scoreRepository).delete(score);
    }

    @Test
    void deleteThrows404WhenScoreMissing() {
        when(scoreRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> scoreService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
