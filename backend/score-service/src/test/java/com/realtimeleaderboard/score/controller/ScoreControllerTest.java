package com.realtimeleaderboard.score.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realtimeleaderboard.score.config.SecurityConfig;
import com.realtimeleaderboard.score.dto.response.PageResponse;
import com.realtimeleaderboard.score.dto.response.ScoreResponse;
import com.realtimeleaderboard.score.entity.ScoreType;
import com.realtimeleaderboard.score.exception.ForbiddenException;
import com.realtimeleaderboard.score.exception.ResourceNotFoundException;
import com.realtimeleaderboard.score.security.JwtAuthenticationFilter;
import com.realtimeleaderboard.score.security.JwtService;
import com.realtimeleaderboard.score.service.ScoreService;
import com.realtimeleaderboard.score.support.TestJwtFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScoreController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class ScoreControllerTest {

    private static final String BEARER = "Bearer ";
    @Autowired MockMvc mockMvc;
    private static final ScoreService scoreService = Mockito.mock(ScoreService.class);

    @BeforeEach void resetMocks() { Mockito.reset(scoreService); }

    @TestConfiguration
    static class ServiceMocks {
        @Bean ScoreService scoreService() { return scoreService; }
    }

    private ScoreResponse sampleScore(Long id, Long userId, Long sportId, ScoreType type, String subId) {
        return new ScoreResponse(id, userId, sportId, BigDecimal.TEN, "Event", "EVT-1", type, subId,
                Instant.parse("2026-08-25T12:00:00Z"), Instant.parse("2026-08-25T12:00:00Z"));
    }

    @Test
    void submitRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/scores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":10,\"scoreType\":\"POINTS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitAsUserReturns201() throws Exception {
        when(scoreService.submit(eq(10L), any()))
                .thenReturn(sampleScore(null, 10L, 1L, ScoreType.POINTS, "sub-1"));
        mockMvc.perform(post("/api/scores").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":10,\"scoreType\":\"POINTS\",\"submissionId\":\"sub-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(10));
    }

    @Test
    void submitMissingValueReturns400() throws Exception {
        mockMvc.perform(post("/api/scores").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"scoreType\":\"POINTS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void submitNegativeValueReturns400() throws Exception {
        mockMvc.perform(post("/api/scores").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":-5,\"scoreType\":\"POINTS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitUnknownScoreTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/scores").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":10,\"scoreType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myScoresReturnsAuthenticatedUserPage() throws Exception {
        when(scoreService.getMyScores(eq(10L), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(sampleScore(1L, 10L, 1L, ScoreType.RUNS, null)), 0, 20, 1, 1));
        mockMvc.perform(get("/api/scores/me").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(10));
    }

    @Test
    void getByIdReturnsScoreForOwner() throws Exception {
        when(scoreService.getById(eq(5L), eq(10L), eq("USER")))
                .thenReturn(sampleScore(5L, 10L, 1L, ScoreType.RUNS, null));
        mockMvc.perform(get("/api/scores/5").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void getByIdReturns403ForOtherUser() throws Exception {
        when(scoreService.getById(eq(5L), eq(20L), eq("USER")))
                .thenThrow(new ForbiddenException("You do not have access to this score"));
        mockMvc.perform(get("/api/scores/5").header("Authorization", BEARER + TestJwtFactory.userToken(20L, "bob")))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/scores").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "a")))
                .andExpect(status().isForbidden());

        when(scoreService.search(null, null, null, null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mockMvc.perform(get("/api/scores").header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRequiresAdmin() throws Exception {
        mockMvc.perform(delete("/api/scores/1").header("Authorization", BEARER + TestJwtFactory.userToken(10L, "a")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/scores/1").header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Score deleted"));
    }

    @Test
    void deleteMissingScoreReturns404() throws Exception {
        Mockito.doThrow(new ResourceNotFoundException("Score not found: id 999")).when(scoreService).delete(999L);
        mockMvc.perform(delete("/api/scores/999").header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isNotFound());
    }
}
