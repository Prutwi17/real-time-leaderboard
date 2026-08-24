package com.realtimeleaderboard.auth.controller;

import com.realtimeleaderboard.auth.dto.request.LoginRequest;
import com.realtimeleaderboard.auth.dto.request.LogoutRequest;
import com.realtimeleaderboard.auth.dto.request.RefreshTokenRequest;
import com.realtimeleaderboard.auth.dto.request.RegisterRequest;
import com.realtimeleaderboard.auth.dto.response.AuthResponse;
import com.realtimeleaderboard.auth.dto.response.MeResponse;
import com.realtimeleaderboard.auth.dto.response.MessageResponse;
import com.realtimeleaderboard.auth.dto.response.RegistrationResponse;
import com.realtimeleaderboard.auth.security.UserPrincipal;
import com.realtimeleaderboard.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.me(principal.userId()));
    }
}
