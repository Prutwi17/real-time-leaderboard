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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public PlayerResponse createPlayer(CreatePlayerRequest request) {
        if (playerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Player with email '" + request.getEmail() + "' already exists");
        }

        Player player = new Player();
        player.setDisplayName(request.getDisplayName());
        player.setEmail(request.getEmail());
        player.setBio(request.getBio());
        player.setProfileImageUrl(request.getProfileImageUrl());

        Player saved = playerRepository.save(player);
        log.info("Created player id={} displayName='{}'", saved.getId(), saved.getDisplayName());

        return PlayerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayer(Long id) {
        Player player = playerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));
        return PlayerResponse.from(player);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlayerResponse> listPlayers(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<Player> playerPage;

        if (search != null && !search.isBlank()) {
            playerPage = playerRepository.findByDisplayNameContainingIgnoreCase(search, pageable);
        } else {
            playerPage = playerRepository.findByActiveTrue(pageable);
        }

        return new PageResponse<>(
            playerPage.getContent().stream().map(PlayerResponse::from).toList(),
            playerPage.getNumber(),
            playerPage.getSize(),
            playerPage.getTotalElements(),
            playerPage.getTotalPages()
        );
    }

    public PlayerResponse updatePlayer(Long id, UpdatePlayerRequest request, String currentUserRole, String currentUserId) {
        Player player = playerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));

        if (!"ADMIN".equals(currentUserRole)) {
            throw new ForbiddenException("Only ADMIN users can update player profiles");
        }

        if (request.getDisplayName() != null) {
            player.setDisplayName(request.getDisplayName());
        }
        if (request.getEmail() != null) {
            if (!request.getEmail().equals(player.getEmail()) && playerRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Player with email '" + request.getEmail() + "' already exists");
            }
            player.setEmail(request.getEmail());
        }
        if (request.getBio() != null) {
            player.setBio(request.getBio());
        }
        if (request.getProfileImageUrl() != null) {
            player.setProfileImageUrl(request.getProfileImageUrl());
        }

        Player saved = playerRepository.save(player);
        log.info("Updated player id={}", saved.getId());

        return PlayerResponse.from(saved);
    }

    public void deactivatePlayer(Long id, String currentUserRole) {
        if (!"ADMIN".equals(currentUserRole)) {
            throw new ForbiddenException("Only ADMIN users can deactivate player profiles");
        }

        Player player = playerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));

        player.setActive(false);
        playerRepository.save(player);
        log.info("Deactivated player id={}", id);
    }

    public void activatePlayer(Long id, String currentUserRole) {
        if (!"ADMIN".equals(currentUserRole)) {
            throw new ForbiddenException("Only ADMIN users can activate player profiles");
        }

        Player player = playerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));

        player.setActive(true);
        playerRepository.save(player);
        log.info("Activated player id={}", id);
    }

    public void deletePlayer(Long id, String currentUserRole) {
        if (!"ADMIN".equals(currentUserRole)) {
            throw new ForbiddenException("Only ADMIN users can delete player profiles");
        }

        Player player = playerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));

        playerRepository.delete(player);
        log.info("Deleted player id={}", id);
    }
}
