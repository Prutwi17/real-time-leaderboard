package com.realtimeleaderboard.sport.dto.request;

import com.realtimeleaderboard.sport.entity.SportCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSportRequest(
        @NotNull(message = "code is required") SportCode code,
        @NotBlank(message = "name is required") @Size(max = 100, message = "name must not exceed 100 characters")
        String name,
        @Size(max = 500, message = "description must not exceed 500 characters")
        String description) {
}
