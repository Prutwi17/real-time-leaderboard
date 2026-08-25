package com.realtimeleaderboard.user.repository;

import com.realtimeleaderboard.user.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @BeforeEach
    void setUp() {
        playerRepository.deleteAll();
    }

    @Test
    void saveAndFindById() {
        Player player = new Player("Bob", "bob@example.com");
        Player saved = playerRepository.save(player);

        assertNotNull(saved.getId());
        assertEquals("Bob", saved.getDisplayName());

        var found = playerRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("bob@example.com", found.get().getEmail());
    }

    @Test
    void findByEmail() {
        Player player = new Player("Charlie", "charlie@example.com");
        playerRepository.save(player);

        var found = playerRepository.findByEmail("charlie@example.com");
        assertTrue(found.isPresent());
        assertEquals("Charlie", found.get().getDisplayName());
    }

    @Test
    void existsByEmail_true() {
        Player player = new Player("Dave", "dave@example.com");
        playerRepository.save(player);

        assertTrue(playerRepository.existsByEmail("dave@example.com"));
    }

    @Test
    void existsByEmail_false() {
        assertFalse(playerRepository.existsByEmail("nobody@example.com"));
    }

    @Test
    void findByActiveTrue_filtersInactive() {
        Player active = new Player("Active", "active@example.com");
        active.setActive(true);
        playerRepository.save(active);

        Player inactive = new Player("Inactive", "inactive@example.com");
        inactive.setActive(false);
        playerRepository.save(inactive);

        var result = playerRepository.findByActiveTrue(PageRequest.of(0, 20));
        assertEquals(1, result.getTotalElements());
        assertEquals("Active", result.getContent().get(0).getDisplayName());
    }

    @Test
    void findByDisplayNameContainingIgnoreCase() {
        playerRepository.save(new Player("Alice Smith", "alice@example.com"));
        playerRepository.save(new Player("Bob Jones", "bob@example.com"));
        playerRepository.save(new Player("Alice Brown", "alice2@example.com"));

        Page<Player> result = playerRepository.findByDisplayNameContainingIgnoreCase(
            "alice", PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
    }

    @Test
    void findByActiveTrue_list() {
        Player active = new Player("Active", "active2@example.com");
        active.setActive(true);
        playerRepository.save(active);

        Player inactive = new Player("Inactive2", "inactive2@example.com");
        inactive.setActive(false);
        playerRepository.save(inactive);

        var result = playerRepository.findByActiveTrue();
        assertEquals(1, result.size());
    }
}
