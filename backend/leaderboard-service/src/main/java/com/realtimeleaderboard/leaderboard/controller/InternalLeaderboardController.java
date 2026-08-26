package com.realtimeleaderboard.leaderboard.controller;

import com.realtimeleaderboard.leaderboard.dto.request.LeaderboardScoreUpdateRequest;
import com.realtimeleaderboard.leaderboard.dto.response.MessageResponse;
import com.realtimeleaderboard.leaderboard.service.LeaderboardRebuildService;
import com.realtimeleaderboard.leaderboard.service.LeaderboardUpdateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/leaderboards")
public class InternalLeaderboardController {

    private final LeaderboardUpdateService updateService;
    private final LeaderboardRebuildService rebuildService;

    public InternalLeaderboardController(LeaderboardUpdateService updateService,
                                         LeaderboardRebuildService rebuildService) {
        this.updateService = updateService;
        this.rebuildService = rebuildService;
    }

    @PostMapping("/scores")
    public MessageResponse updateScore(
            @RequestHeader("X-Internal-Service-Secret") String secret,
            @Valid @RequestBody LeaderboardScoreUpdateRequest request) {
        updateService.validateInternalSecret(secret);
        var result = updateService.processScoreUpdate(request);
        return new MessageResponse(result.message());
    }

    @PostMapping("/{sport}/rebuild")
    public MessageResponse rebuild(
            @RequestHeader("X-Internal-Service-Secret") String secret,
            @PathVariable String sport) {
        updateService.validateInternalSecret(secret);
        return rebuildService.rebuild(sport);
    }
}
