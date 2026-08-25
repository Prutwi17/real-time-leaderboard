package com.realtimeleaderboard.user.dto.response;

import com.realtimeleaderboard.user.entity.Player;
import java.time.Instant;

public record PlayerResponse(
    Long id,
    String displayName,
    String email,
    String bio,
    String profileImageUrl,
    Boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static PlayerResponse from(Player player) {
        return new PlayerResponse(
            player.getId(),
            player.getDisplayName(),
            player.getEmail(),
            player.getBio(),
            player.getProfileImageUrl(),
            player.getActive(),
            player.getCreatedAt(),
            player.getUpdatedAt()
        );
    }
}
