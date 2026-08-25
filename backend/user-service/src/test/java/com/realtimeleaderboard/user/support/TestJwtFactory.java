package com.realtimeleaderboard.user.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class TestJwtFactory {

    private static final String SECRET = "unit-test-only-secret-key-0123456789abcdef0123456789abcdef";

    private TestJwtFactory() { }

    public static String createToken(String userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId)
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(900)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    public static String createAdminToken() {
        return createToken("admin-user-001", "ADMIN");
    }

    public static String createUserToken(String userId) {
        return createToken(userId, "USER");
    }

    public static String createExpiredToken(String userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId)
            .claim("role", role)
            .issuedAt(Date.from(now.minusSeconds(1000)))
            .expiration(Date.from(now.minusSeconds(100)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
