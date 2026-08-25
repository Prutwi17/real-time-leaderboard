package com.realtimeleaderboard.sport.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateCompetitionRequest(
        @NotBlank(message = "name is required") @Size(max = 150, message = "name must not exceed 150 characters")
        String name,
        @NotBlank(message = "code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,50}$", message = "code must be 2-50 uppercase letters, digits or underscores")
        String code,
        @Size(max = 500, message = "description must not exceed 500 characters")
        String description,
        LocalDate startDate,
        LocalDate endDate) {

    @AssertTrue(message = "endDate must be on or after startDate")
    public boolean isValidDateRange() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
