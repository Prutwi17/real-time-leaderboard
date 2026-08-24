package com.realtimeleaderboard.auth.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal: carries identity claims both when loaded from the
 * database (login) and when derived from validated JWT claims (filter).
 */
public record UserPrincipal(Long userId, String username, String role,
                            String passwordHash, boolean active) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public static UserPrincipal fromEntity(com.realtimeleaderboard.auth.entity.User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getRole().name(),
                user.getPassword(), user.isActive());
    }

    public static UserPrincipal fromClaims(io.jsonwebtoken.Claims claims) {
        Long uid = claims.get("uid", Long.class);
        if (uid == null || claims.get("role", String.class) == null) {
            throw new IllegalArgumentException("Token missing required claims");
        }
        return new UserPrincipal(uid, claims.getSubject(), claims.get("role", String.class), null, true);
    }
}
