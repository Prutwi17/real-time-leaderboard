package com.realtimeleaderboard.user.integration;

import com.realtimeleaderboard.user.dto.request.CreatePlayerRequest;
import com.realtimeleaderboard.user.dto.request.UpdatePlayerRequest;
import com.realtimeleaderboard.user.dto.response.PlayerResponse;
import com.realtimeleaderboard.user.entity.Player;
import com.realtimeleaderboard.user.exception.DuplicateResourceException;
import com.realtimeleaderboard.user.exception.ForbiddenException;
import com.realtimeleaderboard.user.exception.ResourceNotFoundException;
import com.realtimeleaderboard.user.repository.PlayerRepository;
import com.realtimeleaderboard.user.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlayerServiceIntegrationTest {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerRepository playerRepository;

    @BeforeEach
    void setUp() {
        playerRepository.deleteAll();
    }

    @Test
    void createPlayer_fullLifecycle() {
        CreatePlayerRequest request = new CreatePlayerRequest("Integration Player", "integration@example.com");

        PlayerResponse created = playerService.createPlayer(request);

        assertNotNull(created.id());
        assertEquals("Integration Player", created.displayName());
        assertEquals("integration@example.com", created.email());
        assertTrue(created.active());
    }

    @Test
    void createPlayer_duplicateEmail_throws() {
        playerService.createPlayer(new CreatePlayerRequest("P1", "dup@example.com"));

        assertThrows(DuplicateResourceException.class,
            () -> playerService.createPlayer(new CreatePlayerRequest("P2", "dup@example.com")));
    }

    @Test
    void getPlayer_foundAndNotFound() {
        PlayerResponse created = playerService.createPlayer(new CreatePlayerRequest("Finder", "finder@example.com"));

        PlayerResponse found = playerService.getPlayer(created.id());
        assertEquals("Finder", found.displayName());

        assertThrows(ResourceNotFoundException.class, () -> playerService.getPlayer(99999L));
    }

    @Test
    void listPlayers_pagination() {
        for (int i = 0; i < 5; i++) {
            playerService.createPlayer(new CreatePlayerRequest("Player" + i, "p" + i + "@example.com"));
        }

        var page1 = playerService.listPlayers(0, 2, null);
        assertEquals(2, page1.items().size());
        assertEquals(5, page1.totalElements());
        assertEquals(3, page1.totalPages());

        var page2 = playerService.listPlayers(1, 2, null);
        assertEquals(2, page2.items().size());
    }

    @Test
    void listPlayers_search() {
        playerService.createPlayer(new CreatePlayerRequest("Alpha Test", "alpha@example.com"));
        playerService.createPlayer(new CreatePlayerRequest("Beta User", "beta@example.com"));
        playerService.createPlayer(new CreatePlayerRequest("Alpha Again", "alpha2@example.com"));

        var result = playerService.listPlayers(0, 10, "alpha");
        assertEquals(2, result.items().size());
    }

    @Test
    void updatePlayer_adminCanUpdate() {
        PlayerResponse created = playerService.createPlayer(new CreatePlayerRequest("Old Name", "old@example.com"));

        UpdatePlayerRequest update = new UpdatePlayerRequest();
        update.setDisplayName("New Name");

        PlayerResponse updated = playerService.updatePlayer(created.id(), update, "ADMIN", "admin-001");

        assertEquals("New Name", updated.displayName());
        assertEquals("old@example.com", updated.email());
    }

    @Test
    void updatePlayer_userCannotUpdate_throws() {
        PlayerResponse created = playerService.createPlayer(new CreatePlayerRequest("Protected", "prot@example.com"));

        UpdatePlayerRequest update = new UpdatePlayerRequest();
        update.setDisplayName("Hacked");

        assertThrows(ForbiddenException.class,
            () -> playerService.updatePlayer(created.id(), update, "USER", "user-001"));
    }

    @Test
    void deactivateAndActivatePlayer() {
        PlayerResponse created = playerService.createPlayer(new CreatePlayerRequest("Toggleable", "toggle@example.com"));

        playerService.deactivatePlayer(created.id(), "ADMIN");
        PlayerResponse deactivated = playerService.getPlayer(created.id());
        assertFalse(deactivated.active());

        playerService.activatePlayer(created.id(), "ADMIN");
        PlayerResponse activated = playerService.getPlayer(created.id());
        assertTrue(activated.active());
    }

    @Test
    void deletePlayer_adminCanDelete() {
        PlayerResponse created = playerService.createPlayer(new CreatePlayerRequest("Doomed", "doomed@example.com"));

        playerService.deletePlayer(created.id(), "ADMIN");

        assertThrows(ResourceNotFoundException.class, () -> playerService.getPlayer(created.id()));
    }

    @Test
    void deletePlayer_userCannotDelete_throws() {
        PlayerResponse created = playerService.createPlayer(new CreatePlayerRequest("Safe", "safe@example.com"));

        assertThrows(ForbiddenException.class,
            () -> playerService.deletePlayer(created.id(), "USER"));
    }
}
