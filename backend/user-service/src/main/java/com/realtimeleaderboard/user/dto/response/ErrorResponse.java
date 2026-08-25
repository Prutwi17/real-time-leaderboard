package com.realtimeleaderboard.user.dto.response;

public record ErrorResponse(
    int status,
    String error,
    String message,
    String path
) {}
