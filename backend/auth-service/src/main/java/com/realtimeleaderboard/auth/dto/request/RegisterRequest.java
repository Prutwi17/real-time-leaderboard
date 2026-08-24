package com.realtimeleaderboard.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username may contain only letters, digits and underscore")
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /**
     * Plain password, accepted only for hashing at registration.
     * Never persisted in plain text and never returned by any endpoint.
     */
    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password must contain at least one letter and one digit")
    private String password;

    /**
     * Ignored by design: public registration always creates USER accounts.
     * Present in the DTO so clients can send it harmlessly; the service never reads it.
     */
    private String role;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
