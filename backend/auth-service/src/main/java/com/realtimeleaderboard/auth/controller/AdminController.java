package com.realtimeleaderboard.auth.controller;

import com.realtimeleaderboard.auth.dto.response.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal ADMIN-only probe used to verify role-based authorization end to end.
 * Real administrative features (user management, moderation) arrive in later phases.
 */
@RestController
@RequestMapping("/api/auth/admin")
public class AdminController {

    @GetMapping("/check")
    public ResponseEntity<MessageResponse> check() {
        return ResponseEntity.ok(new MessageResponse("ADMIN access confirmed"));
    }
}
