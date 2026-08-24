package com.realtimeleaderboard.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-only-secret-key-0123456789abcdef0123456789abcdef";

    @Test
    void generatesTokenWithExpectedClaims() {
        JwtService service = new JwtService(SECRET, 900_000);
        String token = service.generateAccessToken(42L, "john", "USER");

        Claims claims = service.parseAndValidate(token);
        assertThat(claims.getSubject()).isEqualTo("john");
        assertThat(claims.get("uid", Long.class)).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void rejectsTamperedTokenSignature() {
        JwtService service = new JwtService(SECRET, 900_000);
        JwtService attacker = new JwtService("attacker-key-must-be-at-least-32-bytes-long!!", 900_000);

        String forgedToken = attacker.generateAccessToken(1L, "admin", "ADMIN");
        assertThatThrownBy(() -> service.parseAndValidate(forgedToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expiredService = new JwtService(SECRET, -1_000);
        String token = expiredService.generateAccessToken(42L, "john", "USER");

        assertThatThrownBy(() -> expiredService.parseAndValidate(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsGarbageToken() {
        JwtService service = new JwtService(SECRET, 900_000);
        assertThatThrownBy(() -> service.parseAndValidate("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesSecretsShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtService("too-short", 900_000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesDocumentedPlaceholderSecret() {
        assertThatThrownBy(() -> new JwtService("replace_with_secure_random_secret", 900_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
