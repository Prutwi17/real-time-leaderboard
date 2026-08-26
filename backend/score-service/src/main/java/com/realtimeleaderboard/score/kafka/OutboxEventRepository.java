package com.realtimeleaderboard.score.kafka;

import com.realtimeleaderboard.score.kafka.OutboxEvent.Status;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(Status status, org.springframework.data.domain.Pageable pageable);

    boolean existsByEventIdAndStatus(String eventId, Status status);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.attempts < 5 ORDER BY e.createdAt ASC")
    List<OutboxEvent> findRetryableFailed(org.springframework.data.domain.Pageable pageable);
}
