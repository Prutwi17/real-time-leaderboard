package com.realtimeleaderboard.sport.dto.response;

import com.realtimeleaderboard.sport.entity.Sport;

public record SportResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active) {

    public static SportResponse from(Sport sport) {
        return new SportResponse(sport.getId(), sport.getCode().name(), sport.getName(),
                sport.getDescription(), sport.isActive());
    }
}
