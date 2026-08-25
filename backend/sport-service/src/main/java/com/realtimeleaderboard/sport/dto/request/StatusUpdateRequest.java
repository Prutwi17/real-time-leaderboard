package com.realtimeleaderboard.sport.dto.request;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "active is required") Boolean active) {
}
