package com.realtimeleaderboard.user.repository;

import com.realtimeleaderboard.user.entity.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Player> findByActiveTrue(Pageable pageable);

    Page<Player> findByDisplayNameContainingIgnoreCase(String displayName, Pageable pageable);

    List<Player> findByActiveTrue();
}
