package com.realtimeleaderboard.score.controller;

import com.realtimeleaderboard.score.dto.request.CreateScoreRequest;
import com.realtimeleaderboard.score.dto.response.MessageResponse;
import com.realtimeleaderboard.score.dto.response.PageResponse;
import com.realtimeleaderboard.score.dto.response.ScoreResponse;
import com.realtimeleaderboard.score.entity.ScoreType;
import com.realtimeleaderboard.score.security.AuthenticatedUser;
import com.realtimeleaderboard.score.service.ScoreService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) { this.scoreService = scoreService; }

    @PostMapping
    public ResponseEntity<ScoreResponse> submit(@AuthenticationPrincipal AuthenticatedUser user,
                                                @Valid @RequestBody CreateScoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scoreService.submit(user.userId(), request));
    }

    @GetMapping("/me")
    public PageResponse<ScoreResponse> myScores(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return scoreService.getMyScores(user.userId(), page, Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/{id}")
    public ScoreResponse getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return scoreService.getById(id, user.userId(), user.role());
    }

    @GetMapping
    public PageResponse<ScoreResponse> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long sportId,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) ScoreType scoreType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scoreService.search(userId, sportId, eventId, scoreType, from, to, page, Math.min(Math.max(size, 1), 100));
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable Long id) {
        scoreService.delete(id);
        return new MessageResponse("Score deleted");
    }
}
