package com.realtimeleaderboard.score.service;

import com.realtimeleaderboard.score.client.LeaderboardClient;
import com.realtimeleaderboard.score.client.SportSnapshot;
import com.realtimeleaderboard.score.dto.request.CreateScoreRequest;
import com.realtimeleaderboard.score.dto.response.PageResponse;
import com.realtimeleaderboard.score.dto.response.ScoreResponse;
import com.realtimeleaderboard.score.entity.Score;
import com.realtimeleaderboard.score.entity.ScoreType;
import com.realtimeleaderboard.score.exception.ConflictException;
import com.realtimeleaderboard.score.exception.ForbiddenException;
import com.realtimeleaderboard.score.exception.ResourceNotFoundException;
import com.realtimeleaderboard.score.repository.ScoreRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreService {

    private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

    private final ScoreRepository scoreRepository;
    private final SportValidationService sportValidationService;
    private final LeaderboardClient leaderboardClient;

    public ScoreService(ScoreRepository scoreRepository, SportValidationService sportValidationService,
                        LeaderboardClient leaderboardClient) {
        this.scoreRepository = scoreRepository;
        this.sportValidationService = sportValidationService;
        this.leaderboardClient = leaderboardClient;
    }

    @Transactional
    public ScoreResponse submit(Long userId, CreateScoreRequest request) {
        sportValidationService.validateSportForSubmission(request.sportId());

        if (request.submissionId() != null && scoreRepository.existsByUserIdAndSubmissionId(userId, request.submissionId())) {
            throw new ConflictException("Duplicate submission for this user");
        }

        Score score = new Score();
        score.setUserId(userId);
        score.setSportId(request.sportId());
        score.setValue(request.value());
        score.setEventName(request.eventName());
        score.setEventId(request.eventId());
        score.setScoreType(request.scoreType());
        score.setSubmissionId(request.submissionId());
        // recordedAt defaults to Instant.now() via @PrePersist if null.
        Score saved = scoreRepository.save(score);

        // Notify leaderboard-service (best-effort; failure does not rollback score)
        try {
            leaderboardClient.notifyScoreUpdate(
                    userId, request.sportId(), request.value().doubleValue(),
                    request.submissionId() != null ? request.submissionId() : String.valueOf(saved.getId()));
        } catch (Exception e) {
            log.warn("Leaderboard notification failed for score {}: {}", saved.getId(), e.getMessage());
        }

        return ScoreResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScoreResponse> getMyScores(Long userId, int page, int size) {
        Page<Score> p = scoreRepository.findAllByUserIdOrderByRecordedAtDescIdDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(p.map(ScoreResponse::from));
    }

    @Transactional(readOnly = true)
    public ScoreResponse getById(Long scoreId, Long userId, String role) {
        Score score = scoreRepository.findById(scoreId)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found: id " + scoreId));
        if (!"ADMIN".equals(role) && !score.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this score");
        }
        return ScoreResponse.from(score);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScoreResponse> search(Long userId, Long sportId, String eventId,
                                              ScoreType scoreType, Instant from, Instant to,
                                              int page, int size) {
        Page<Score> p = scoreRepository.search(userId, sportId, eventId, scoreType, from, to, PageRequest.of(page, size));
        return PageResponse.from(p.map(ScoreResponse::from));
    }

    @Transactional
    public void delete(Long scoreId) {
        Score score = scoreRepository.findById(scoreId)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found: id " + scoreId));
        // Hard delete chosen intentionally: scores are audit-relevant and deleting
        // invalid submissions is rare and admin-only; soft-delete adds complexity
        // that is not warranted here. The future event/audit log will capture
        // deletions separately.
        scoreRepository.delete(score);
    }
}
