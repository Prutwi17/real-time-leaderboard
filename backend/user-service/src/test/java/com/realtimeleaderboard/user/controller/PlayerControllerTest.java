package com.realtimeleaderboard.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeleaderboard.user.config.SecurityConfig;
import com.realtimeleaderboard.user.dto.request.CreatePlayerRequest;
import com.realtimeleaderboard.user.dto.request.UpdatePlayerRequest;
import com.realtimeleaderboard.user.dto.response.PageResponse;
import com.realtimeleaderboard.user.dto.response.PlayerResponse;
import com.realtimeleaderboard.user.exception.DuplicateResourceException;
import com.realtimeleaderboard.user.exception.ForbiddenException;
import com.realtimeleaderboard.user.exception.ResourceNotFoundException;
import com.realtimeleaderboard.user.security.JwtAuthenticationFilter;
import com.realtimeleaderboard.user.security.JwtService;
import com.realtimeleaderboard.user.service.PlayerService;
import com.realtimeleaderboard.user.support.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlayerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class PlayerControllerTest {

    private static final String BEARER = "Bearer ";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final PlayerService playerService = Mockito.mock(PlayerService.class);

    @BeforeEach
    void resetMocks() { Mockito.reset(playerService); }

    @TestConfiguration
    static class ServiceMocks {
        @Bean
        PlayerService playerService() { return playerService; }
    }

    private PlayerResponse sampleResponse() {
        return new PlayerResponse(
            1L, "Alice", "alice@example.com", "bio",
            "http://img.com/alice.png", true,
            Instant.parse("2026-08-25T12:00:00Z"), Instant.parse("2026-08-25T12:00:00Z")
        );
    }

    @Test
    void createPlayer_success() throws Exception {
        when(playerService.createPlayer(any(CreatePlayerRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/players")
                .header("Authorization", BEARER + TestJwtFactory.createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePlayerRequest("Alice", "alice@example.com"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.displayName").value("Alice"));
    }

    @Test
    void createPlayer_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePlayerRequest("Alice", "alice@example.com"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createPlayer_invalidEmail_400() throws Exception {
        mockMvc.perform(post("/api/players")
                .header("Authorization", BEARER + TestJwtFactory.createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePlayerRequest("Alice", "not-an-email"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createPlayer_duplicateEmail_409() throws Exception {
        when(playerService.createPlayer(any())).thenThrow(new DuplicateResourceException("Email exists"));

        mockMvc.perform(post("/api/players")
                .header("Authorization", BEARER + TestJwtFactory.createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePlayerRequest("Alice", "alice@example.com"))))
            .andExpect(status().isConflict());
    }

    @Test
    void getPlayer_found_publicAccess() throws Exception {
        when(playerService.getPlayer(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/players/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Alice"));
    }

    @Test
    void getPlayer_notFound_404() throws Exception {
        when(playerService.getPlayer(99L)).thenThrow(new ResourceNotFoundException("Player", "id", 99L));

        mockMvc.perform(get("/api/players/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void listPlayers_returnsPage_publicAccess() throws Exception {
        PageResponse<PlayerResponse> page = new PageResponse<>(
            List.of(sampleResponse()), 0, 20, 1, 1
        );
        when(playerService.listPlayers(0, 20, null)).thenReturn(page);

        mockMvc.perform(get("/api/players")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updatePlayer_admin_success() throws Exception {
        UpdatePlayerRequest request = new UpdatePlayerRequest();
        request.setDisplayName("Alice Updated");

        PlayerResponse updated = new PlayerResponse(
            1L, "Alice Updated", "alice@example.com", "bio",
            "http://img.com/alice.png", true,
            Instant.parse("2026-08-25T12:00:00Z"), Instant.parse("2026-08-25T12:00:00Z")
        );
        when(playerService.updatePlayer(eq(1L), any(), eq("ADMIN"), eq("admin-user-001"))).thenReturn(updated);

        mockMvc.perform(put("/api/players/1")
                .header("Authorization", BEARER + TestJwtFactory.createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Alice Updated"));
    }

    @Test
    void updatePlayer_forbiddenForUser() throws Exception {
        UpdatePlayerRequest request = new UpdatePlayerRequest();
        request.setDisplayName("Hacker");

        when(playerService.updatePlayer(eq(1L), any(), eq("USER"), eq("user-001")))
            .thenThrow(new ForbiddenException("Only ADMIN users can update player profiles"));

        mockMvc.perform(put("/api/players/1")
                .header("Authorization", BEARER + TestJwtFactory.createUserToken("user-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deactivatePlayer_admin_success() throws Exception {
        mockMvc.perform(put("/api/players/1/deactivate")
                .header("Authorization", BEARER + TestJwtFactory.createAdminToken()))
            .andExpect(status().isNoContent());

        Mockito.verify(playerService).deactivatePlayer(1L, "ADMIN");
    }

    @Test
    void activatePlayer_admin_success() throws Exception {
        mockMvc.perform(put("/api/players/1/activate")
                .header("Authorization", BEARER + TestJwtFactory.createAdminToken()))
            .andExpect(status().isNoContent());

        Mockito.verify(playerService).activatePlayer(1L, "ADMIN");
    }

    @Test
    void deletePlayer_admin_success() throws Exception {
        mockMvc.perform(delete("/api/players/1")
                .header("Authorization", BEARER + TestJwtFactory.createAdminToken()))
            .andExpect(status().isNoContent());

        Mockito.verify(playerService).deletePlayer(1L, "ADMIN");
    }

    @Test
    void deletePlayer_forbiddenForUser() throws Exception {
        Mockito.doThrow(new ForbiddenException("Only ADMIN users can delete player profiles"))
            .when(playerService).deletePlayer(1L, "USER");

        mockMvc.perform(delete("/api/players/1")
                .header("Authorization", BEARER + TestJwtFactory.createUserToken("user-001")))
            .andExpect(status().isForbidden());
    }
}
