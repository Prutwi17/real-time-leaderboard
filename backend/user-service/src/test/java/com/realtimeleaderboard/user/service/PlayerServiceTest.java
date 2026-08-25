package com.realtimeleaderboard.user.service;

import com.realtimeleaderboard.user.dto.request.CreatePlayerRequest;
import com.realtimeleaderboard.user.dto.request.UpdatePlayerRequest;
import com.realtimeleaderboard.user.dto.response.PageResponse;
import com.realtimeleaderboard.user.dto.response.PlayerResponse;
import com.realtimeleaderboard.user.entity.Player;
import com.realtimeleaderboard.user.exception.DuplicateResourceException;
import com.realtimeleaderboard.user.exception.ForbiddenException;
import com.realtimeleaderboard.user.exception.ResourceNotFoundException;
import com.realtimeleaderboard.user.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    private Player samplePlayer;

    @BeforeEach
    void setUp() {
        samplePlayer = new Player("Alice", "alice@example.com");
        samplePlayer.setId(1L);
        samplePlayer.setBio("Test bio");
        samplePlayer.setActive(true);
        samplePlayer.setCreatedAt(Instant.now());
        samplePlayer.setUpdatedAt(Instant.now());
    }

    @Test
    void createPlayer_success() {
        CreatePlayerRequest request = new CreatePlayerRequest("Alice", "alice@example.com");
        when(playerRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(playerRepository.save(any(Player.class))).thenReturn(samplePlayer);

        PlayerResponse response = playerService.createPlayer(request);

        assertEquals(1L, response.id());
        assertEquals("Alice", response.displayName());
        assertEquals("alice@example.com", response.email());
        verify(playerRepository).save(any(Player.class));
    }

    @Test
    void createPlayer_duplicateEmail_throws() {
        CreatePlayerRequest request = new CreatePlayerRequest("Alice", "alice@example.com");
        when(playerRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> playerService.createPlayer(request));
        verify(playerRepository, never()).save(any());
    }

    @Test
    void getPlayer_found() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(samplePlayer));

        PlayerResponse response = playerService.getPlayer(1L);

        assertEquals("Alice", response.displayName());
    }

    @Test
    void getPlayer_notFound_throws() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> playerService.getPlayer(99L));
    }

    @Test
    void listPlayers_returnsPage() {
        Page<Player> page = new PageImpl<>(List.of(samplePlayer), PageRequest.of(0, 20), 1);
        when(playerRepository.findByActiveTrue(any(Pageable.class))).thenReturn(page);

        PageResponse<PlayerResponse> response = playerService.listPlayers(0, 20, null);

        assertEquals(1, response.items().size());
        assertEquals(0, response.page());
        assertEquals(1L, response.totalElements());
    }

    @Test
    void listPlayers_withSearch_usesSearch() {
        Page<Player> page = new PageImpl<>(List.of(samplePlayer), PageRequest.of(0, 20), 1);
        when(playerRepository.findByDisplayNameContainingIgnoreCase(eq("alice"), any(Pageable.class))).thenReturn(page);

        PageResponse<PlayerResponse> response = playerService.listPlayers(0, 20, "alice");

        assertEquals(1, response.items().size());
        verify(playerRepository).findByDisplayNameContainingIgnoreCase(eq("alice"), any(Pageable.class));
    }

    @Test
    void updatePlayer_admin_canUpdate() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(samplePlayer));
        when(playerRepository.save(any(Player.class))).thenReturn(samplePlayer);

        UpdatePlayerRequest request = new UpdatePlayerRequest();
        request.setDisplayName("Alice Updated");

        PlayerResponse response = playerService.updatePlayer(1L, request, "ADMIN", "admin-001");

        assertEquals("Alice Updated", response.displayName());
    }

    @Test
    void updatePlayer_user_cannotUpdate_throws() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(samplePlayer));

        UpdatePlayerRequest request = new UpdatePlayerRequest();
        request.setDisplayName("Hacker");

        assertThrows(ForbiddenException.class,
            () -> playerService.updatePlayer(1L, request, "USER", "user-001"));
    }

    @Test
    void deactivatePlayer_admin_success() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(samplePlayer));
        when(playerRepository.save(any(Player.class))).thenReturn(samplePlayer);

        playerService.deactivatePlayer(1L, "ADMIN");

        assertFalse(samplePlayer.getActive());
        verify(playerRepository).save(samplePlayer);
    }

    @Test
    void deactivatePlayer_user_throws() {
        assertThrows(ForbiddenException.class,
            () -> playerService.deactivatePlayer(1L, "USER"));
    }

    @Test
    void activatePlayer_admin_success() {
        samplePlayer.setActive(false);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(samplePlayer));
        when(playerRepository.save(any(Player.class))).thenReturn(samplePlayer);

        playerService.activatePlayer(1L, "ADMIN");

        assertTrue(samplePlayer.getActive());
    }

    @Test
    void deletePlayer_admin_success() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(samplePlayer));

        playerService.deletePlayer(1L, "ADMIN");

        verify(playerRepository).delete(samplePlayer);
    }

    @Test
    void deletePlayer_user_throws() {
        assertThrows(ForbiddenException.class,
            () -> playerService.deletePlayer(1L, "USER"));
    }
}
