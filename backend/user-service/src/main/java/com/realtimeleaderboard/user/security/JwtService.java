package com.realtimeleaderboard.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
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

    public boolean isValidToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
    }

    public String getUserId(String token) {
        return getClaims(token).getSubject();
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public List<SimpleGrantedAuthority> getAuthorities(String token) {
        String role = getRole(token);
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public String generateToken(String userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 86400000);

        return Jwts.builder()
            .subject(userId)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
    }
}
