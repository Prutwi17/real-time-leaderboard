package com.realtimeleaderboard.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single owner of JWT creation and validation (HS256).
 */
@Service
public class JwtService {

    private static final String PLACEHOLDER_SECRET = "replace_with_secure_random_secret";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final boolean usingDefaultSecret;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes; refusing to start with a weak key");
        }
        if (PLACEHOLDER_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "jwt.secret is still the documented placeholder value; set the JWT_SECRET environment variable");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.usingDefaultSecret = secret.startsWith("dev-only-insecure-secret");
    }

    public String generateAccessToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates signature + expiry.
     *
     * @throws JwtException on invalid signature, malformed token or expired token
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    public boolean isUsingDefaultSecret() {
        return usingDefaultSecret;
    }
}
