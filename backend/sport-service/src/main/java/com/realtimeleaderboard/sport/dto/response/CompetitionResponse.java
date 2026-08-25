package com.realtimeleaderboard.sport.dto.response;

import com.realtimeleaderboard.sport.entity.Competition;
import java.time.LocalDate;

public record CompetitionResponse(
        Long id,
        String name,
        String code,
        Long sportId,
        String sportCode,
        String description,
        boolean active,
        LocalDate startDate,
        LocalDate endDate) {

    public static CompetitionResponse from(Competition competition) {
        return new CompetitionResponse(competition.getId(), competition.getName(), competition.getCode(),
                competition.getSport().getId(), competition.getSport().getCode().name(),
                competition.getDescription(), competition.isActive(),
                competition.getStartDate(), competition.getEndDate());
    }
}
