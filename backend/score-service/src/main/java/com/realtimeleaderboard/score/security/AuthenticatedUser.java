package com.realtimeleaderboard.score.security;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AuthenticatedUser(Long userId, String username, String role) implements UserDetails {

    public static AuthenticatedUser fromClaims(Claims claims) {
        Long uid = claims.get("uid", Long.class);
        String role = claims.get("role", String.class);
        if (uid == null || role == null) {
            throw new IllegalArgumentException("Token missing required claims");
        }
        return new AuthenticatedUser(uid, claims.getSubject(), role);
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String getPassword()    { return null; }
    @Override public String getUsername()     { return username; }
    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isAccountNonLocked()    { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()             { return true; }
}
