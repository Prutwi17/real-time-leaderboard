package com.realtimeleaderboard.score.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Validation-only: shares the same secret as auth-service; never issues tokens. */
@Service
public class JwtService {

    private static final String PLACEHOLDER_SECRET = "replace_with_secure_random_secret";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
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
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
