package com.realtimeleaderboard.user.controller;

import com.realtimeleaderboard.user.dto.request.CreatePlayerRequest;
import com.realtimeleaderboard.user.dto.request.UpdatePlayerRequest;
import com.realtimeleaderboard.user.dto.response.PageResponse;
import com.realtimeleaderboard.user.dto.response.PlayerResponse;
import com.realtimeleaderboard.user.security.AuthenticatedUser;
import com.realtimeleaderboard.user.service.PlayerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        PlayerResponse response = playerService.createPlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponse> getPlayer(@PathVariable Long id) {
        PlayerResponse response = playerService.getPlayer(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<PlayerResponse>> listPlayers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        PageResponse<PlayerResponse> response = playerService.listPlayers(page, size, search);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlayerResponse> updatePlayer(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePlayerRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PlayerResponse response = playerService.updatePlayer(id, request, currentUser.role(), currentUser.uid());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivatePlayer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        playerService.deactivatePlayer(id, currentUser.role());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activatePlayer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        playerService.activatePlayer(id, currentUser.role());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlayer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        playerService.deletePlayer(id, currentUser.role());
        return ResponseEntity.noContent().build();
    }
}
