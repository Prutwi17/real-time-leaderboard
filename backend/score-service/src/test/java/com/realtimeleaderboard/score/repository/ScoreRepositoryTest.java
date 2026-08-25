package com.realtimeleaderboard.score.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realtimeleaderboard.score.entity.Score;
import com.realtimeleaderboard.score.entity.ScoreType;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
class ScoreRepositoryTest {

    @Autowired private ScoreRepository scoreRepository;
    @Autowired private EntityManager em;

    private Score build(Long userId, Long sportId, BigDecimal value, ScoreType type, String submissionId) {
        Score s = new Score();
        s.setUserId(userId);
        s.setSportId(sportId);
        s.setValue(value);
        s.setScoreType(type);
        s.setEventName("Test Event");
        s.setEventId("TEST-001");
        s.setSubmissionId(submissionId);
        // recordedAt defaults via @PrePersist when null.
        return s;
    }

    @Test
    void savesAndRetrieves() {
        Score saved = scoreRepository.save(build(1L, 1L, BigDecimal.TEN, ScoreType.POINTS, null));
        assertThat(scoreRepository.findById(saved.getId())).isPresent();
        assertThat(scoreRepository.findById(saved.getId()).get().getValue()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void newestFirstOrdering() {
        Instant now = Instant.now();
        Score s1 = build(1L, 1L, BigDecimal.ONE, ScoreType.POINTS, null);
        s1.setRecordedAt(now.minusSeconds(10));
        scoreRepository.save(s1);

        Score s2 = build(1L, 1L, BigDecimal.TEN, ScoreType.RUNS, null);
        s2.setRecordedAt(now);
        scoreRepository.save(s2);

        var page = scoreRepository.findAllByUserIdOrderByRecordedAtDescIdDesc(1L, PageRequest.of(0, 10));
        assertThat(page.getContent().get(0).getId()).isEqualTo(s2.getId());
    }

    @Test
    void uniqueSubmissionIdPerUser() {
        scoreRepository.save(build(1L, 1L, BigDecimal.TEN, ScoreType.GOALS, "sub-1"));
        scoreRepository.save(build(2L, 1L, BigDecimal.TEN, ScoreType.GOALS, "sub-1")); // different user ok
        assertThatThrownBy(() -> {
            scoreRepository.save(build(1L, 1L, BigDecimal.TEN, ScoreType.GOALS, "sub-1"));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleNullSubmissionIdsAllowed() {
        scoreRepository.save(build(1L, 1L, BigDecimal.TEN, ScoreType.POINTS, null));
        scoreRepository.save(build(1L, 1L, BigDecimal.ONE, ScoreType.POINTS, null));
        assertThat(scoreRepository.count()).isEqualTo(2);
    }

    @Test
    void searchFilterSportId() {
        scoreRepository.save(build(1L, 1L, BigDecimal.TEN, ScoreType.POINTS, "s1"));
        scoreRepository.save(build(1L, 2L, BigDecimal.ONE, ScoreType.RUNS, "s2"));
        var result = scoreRepository.search(null, 2L, null, null, null, null, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSportId()).isEqualTo(2L);
    }

    @Test
    void searchFilterEventType() {
        scoreRepository.save(build(1L, 1L, BigDecimal.TEN, ScoreType.GOALS, "e1"));
        scoreRepository.save(build(1L, 1L, BigDecimal.ONE, ScoreType.POINTS, "e2"));
        var result = scoreRepository.search(null, null, null, ScoreType.GOALS, null, null, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void existsByUserIdAndSubmissionIdWorks() {
        scoreRepository.save(build(10L, 1L, BigDecimal.TEN, ScoreType.POINTS, "exists"));
        assertThat(scoreRepository.existsByUserIdAndSubmissionId(10L, "exists")).isTrue();
        assertThat(scoreRepository.existsByUserIdAndSubmissionId(10L, "other")).isFalse();
        assertThat(scoreRepository.existsByUserIdAndSubmissionId(99L, "exists")).isFalse();
    }
}
