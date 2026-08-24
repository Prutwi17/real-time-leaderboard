package com.realtimeleaderboard.auth.repository;

import com.realtimeleaderboard.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserIdAndRevokedTrue(Long userId);
}
