package com.realtimeleaderboard.auth.service;

import com.realtimeleaderboard.auth.entity.RefreshToken;
import com.realtimeleaderboard.auth.entity.User;
import com.realtimeleaderboard.auth.exception.InvalidTokenException;
import com.realtimeleaderboard.auth.repository.RefreshTokenRepository;
import com.realtimeleaderboard.auth.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Generates a cryptographically random opaque token. Only its SHA-256 hash is persisted.
     */
    @Transactional
    public String create(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(sha256(rawToken));
        entity.setUserId(userId);
        entity.setExpiresAt(LocalDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public User validateAndGetUser(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Refresh token is required");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
        if (stored.isRevoked()) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }
        if (stored.isExpired(LocalDateTime.now())) {
            throw new InvalidTokenException("Refresh token has expired");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));
        if (!user.isActive()) {
            throw new InvalidTokenException("Account is inactive");
        }
        return user;
    }

    /**
     * Idempotent revocation: unknown tokens are silently ignored so the response
     * leaks nothing about which refresh tokens exist.
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
