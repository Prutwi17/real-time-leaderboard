package com.realtimeleaderboard.score.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class TestJwtFactory {

    private static final String SECRET = "unit-test-only-secret-key-0123456789abcdef0123456789abcdef";

    private TestJwtFactory() { }

    public static String token(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public static String userToken(long id, String username) { return token(id, username, "USER"); }
    public static String adminToken(long id, String username) { return token(id, username, "ADMIN"); }
}
