package com.realtimeleaderboard.leaderboard.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeleaderboard.leaderboard.config.SecurityConfig;
import com.realtimeleaderboard.leaderboard.dto.request.LeaderboardScoreUpdateRequest;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardEntryResponse;
import com.realtimeleaderboard.leaderboard.dto.response.LeaderboardResponse;
import com.realtimeleaderboard.leaderboard.dto.response.MessageResponse;
import com.realtimeleaderboard.leaderboard.dto.response.PlayerRankResponse;
import com.realtimeleaderboard.leaderboard.dto.response.SizeResponse;
import com.realtimeleaderboard.leaderboard.exception.ForbiddenException;
import com.realtimeleaderboard.leaderboard.exception.GlobalExceptionHandler;
import com.realtimeleaderboard.leaderboard.exception.InvalidSportException;
import com.realtimeleaderboard.leaderboard.exception.ResourceNotFoundException;
import com.realtimeleaderboard.leaderboard.exception.ServiceUnavailableException;
import com.realtimeleaderboard.leaderboard.security.JwtAuthenticationFilter;
import com.realtimeleaderboard.leaderboard.security.JwtService;
import com.realtimeleaderboard.leaderboard.service.LeaderboardRebuildService;
import com.realtimeleaderboard.leaderboard.service.LeaderboardService;
import com.realtimeleaderboard.leaderboard.service.LeaderboardUpdateService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {LeaderboardController.class, InternalLeaderboardController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaderboardService leaderboardService;

    @MockBean
    private LeaderboardUpdateService updateService;

    @MockBean
    private LeaderboardRebuildService rebuildService;

    @MockBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getTopReturnsEntries() throws Exception {
        LeaderboardResponse response = new LeaderboardResponse("FOOTBALL", List.of(
                new LeaderboardEntryResponse(1, 101L, 950.0),
                new LeaderboardEntryResponse(2, 102L, 820.0)
        ), 0, 10, 2);
        when(leaderboardService.getTop(eq("football"), anyInt())).thenReturn(response);

        mockMvc.perform(get("/api/leaderboards/football/top").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sport").value("FOOTBALL"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].userId").value(101))
                .andExpect(jsonPath("$.entries[0].score").value(950.0))
                .andExpect(jsonPath("$.totalPlayers").value(2));
    }

    @Test
    void getLeaderboardPagination() throws Exception {
        LeaderboardResponse response = new LeaderboardResponse("CRICKET", List.of(
                new LeaderboardEntryResponse(21, 201L, 500.0)
        ), 1, 20, 50);
        when(leaderboardService.getLeaderboard(eq("cricket"), anyInt(), anyInt())).thenReturn(response);

        mockMvc.perform(get("/api/leaderboards/cricket").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sport").value("CRICKET"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPlayers").value(50));
    }

    @Test
    void getPlayerRank() throws Exception {
        when(leaderboardService.getPlayerRank(eq("football"), eq(101L)))
                .thenReturn(new PlayerRankResponse("FOOTBALL", 101L, 1, 950.0));

        mockMvc.perform(get("/api/leaderboards/football/players/101/rank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.userId").value(101))
                .andExpect(jsonPath("$.score").value(950.0));
    }

    @Test
    void getPlayerRankNotFound() throws Exception {
        when(leaderboardService.getPlayerRank(eq("football"), eq(999L)))
                .thenThrow(new ResourceNotFoundException("Player 999 not found"));

        mockMvc.perform(get("/api/leaderboards/football/players/999/rank"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getNearbyPlayers() throws Exception {
        when(leaderboardService.getNearbyPlayers(eq("football"), eq(101L), anyInt()))
                .thenReturn(List.of(
                        new LeaderboardEntryResponse(4, 103L, 800.0),
                        new LeaderboardEntryResponse(5, 101L, 750.0),
                        new LeaderboardEntryResponse(6, 105L, 700.0)
                ));

        mockMvc.perform(get("/api/leaderboards/football/players/101/nearby").param("range", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].userId").value(101));
    }

    @Test
    void getSize() throws Exception {
        when(leaderboardService.getSize(eq("f1"))).thenReturn(new SizeResponse("F1", 150));

        mockMvc.perform(get("/api/leaderboards/f1/size"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlayers").value(150));
    }

    @Test
    void invalidSport() throws Exception {
        when(leaderboardService.getTop(eq("basketball"), anyInt()))
                .thenThrow(new InvalidSportException("Unsupported sport: BASKETBALL"));

        mockMvc.perform(get("/api/leaderboards/basketball/top"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void redisUnavailable() throws Exception {
        when(leaderboardService.getTop(eq("football"), anyInt()))
                .thenThrow(new ServiceUnavailableException("Redis unavailable"));

        mockMvc.perform(get("/api/leaderboards/football/top"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void updateScoreRequiresInternalSecret() throws Exception {
        LeaderboardScoreUpdateRequest request = new LeaderboardScoreUpdateRequest(101L, 1L, 100.0, "score-1");
        doThrow(new ForbiddenException("Invalid internal service secret"))
                .when(updateService).validateInternalSecret("wrong-secret");

        mockMvc.perform(post("/internal/leaderboards/scores")
                        .header("X-Internal-Service-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateScoreSuccess() throws Exception {
        LeaderboardScoreUpdateRequest request = new LeaderboardScoreUpdateRequest(101L, 1L, 100.0, "score-1");
        doNothing().when(updateService).validateInternalSecret("valid-secret");
        when(updateService.processScoreUpdate(request)).thenReturn(new MessageResponse("Leaderboard updated"));

        mockMvc.perform(post("/internal/leaderboards/scores")
                        .header("X-Internal-Service-Secret", "valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Leaderboard updated"));
    }

    @Test
    void rebuildRequiresInternalSecret() throws Exception {
        doThrow(new ForbiddenException("Invalid internal service secret"))
                .when(updateService).validateInternalSecret("wrong");

        mockMvc.perform(post("/internal/leaderboards/football/rebuild")
                        .header("X-Internal-Service-Secret", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rebuildSuccess() throws Exception {
        doNothing().when(updateService).validateInternalSecret("valid-secret");
        when(rebuildService.rebuild("football")).thenReturn(new MessageResponse("Rebuilt: 50 players"));

        mockMvc.perform(post("/internal/leaderboards/football/rebuild")
                        .header("X-Internal-Service-Secret", "valid-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Rebuilt: 50 players"));
    }

    @Test
    void getMyRankRequiresAuth() throws Exception {
        when(leaderboardService.getPlayerRank(eq("football"), eq(101L)))
                .thenReturn(new PlayerRankResponse("FOOTBALL", 101L, 1, 950.0));
        when(jwtService.parseAndValidate("valid-token"))
                .thenReturn(io.jsonwebtoken.Jwts.claims().subject("user1").add("uid", 101L).add("role", "USER").build());

        mockMvc.perform(get("/api/leaderboards/football/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyRankNoAuth() throws Exception {
        mockMvc.perform(get("/api/leaderboards/football/me"))
                .andExpect(status().isInternalServerError());
    }
}
