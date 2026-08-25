package com.realtimeleaderboard.leaderboard.controller;

import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardEntryResponse;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.dto.response.PlayerRankResponse;
import com.realtimeleaderboard.leaderboard.dto.response.SizeResponse;
import com.realtimeleaderboard.leaderboard.security.AuthenticatedUser;
import com.realtimeleaderboard.leaderboard.service.LeaderboardService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/{sport}/top")
    public LeaderboardResponse getTop(@PathVariable String sport,
                                      @RequestParam(defaultValue = "10") int limit) {
        return leaderboardService.getTop(sport, limit);
    }

    @GetMapping("/{sport}")
    public LeaderboardResponse getLeaderboard(@PathVariable String sport,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return leaderboardService.getLeaderboard(sport, page, size);
    }

    @GetMapping("/{sport}/players/{userId}/rank")
    public PlayerRankResponse getPlayerRank(@PathVariable String sport,
                                            @PathVariable Long userId) {
        return leaderboardService.getPlayerRank(sport, userId);
    }

    @GetMapping("/{sport}/players/{userId}/nearby")
    public List<LeaderboardEntryResponse> getNearbyPlayers(
            @PathVariable String sport,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "2") int range) {
        return leaderboardService.getNearbyPlayers(sport, userId, range);
    }

    @GetMapping("/{sport}/me")
    public PlayerRankResponse getMyRank(@PathVariable String sport,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return leaderboardService.getPlayerRank(sport, user.userId());
    }

    @GetMapping("/{sport}/size")
    public SizeResponse getSize(@PathVariable String sport) {
        return leaderboardService.getSize(sport);
    }
}
