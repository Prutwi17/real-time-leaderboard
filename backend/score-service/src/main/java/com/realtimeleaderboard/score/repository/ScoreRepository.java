package com.realtimeleaderboard.score.repository;

import com.realtimeleaderboard.score.entity.Score;
import com.realtimeleaderboard.score.entity.ScoreType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    Optional<Score> findByIdAndUserId(Long id, Long userId);

    Page<Score> findAllByUserIdOrderByRecordedAtDescIdDesc(Long userId, Pageable pageable);

    boolean existsByUserIdAndSubmissionId(Long userId, String submissionId);

    @Query("""
            select s from Score s
            where (:userId is null or s.userId = :userId)
              and (:sportId is null or s.sportId = :sportId)
              and (:eventId is null or s.eventId = :eventId)
              and (:scoreType is null or s.scoreType = :scoreType)
              and (:from is null or s.recordedAt >= :from)
              and (:to   is null or s.recordedAt <= :to)
            order by s.recordedAt desc, s.id desc
            """)
    Page<Score> search(Long userId, Long sportId, String eventId,
                       ScoreType scoreType, Instant from, Instant to,
                       Pageable pageable);
}
