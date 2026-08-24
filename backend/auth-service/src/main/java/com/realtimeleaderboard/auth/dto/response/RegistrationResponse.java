package com.realtimeleaderboard.auth.dto.response;

public class RegistrationResponse {

    private final String message;
    private final Long userId;
    private final String username;
    private final String role;

    public RegistrationResponse(String message, Long userId, String username, String role) {
        this.message = message;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
