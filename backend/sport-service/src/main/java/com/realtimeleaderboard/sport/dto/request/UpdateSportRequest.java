package com.realtimeleaderboard.sport.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sport codes are immutable: the code identifies a fixed catalog entry and is
 * referenced by competitions, so updates may only change display metadata.
 */
public record UpdateSportRequest(
        @NotBlank(message = "name is required") @Size(max = 100, message = "name must not exceed 100 characters")
        String name,
        @Size(max = 500, message = "description must not exceed 500 characters")
        String description) {
}
